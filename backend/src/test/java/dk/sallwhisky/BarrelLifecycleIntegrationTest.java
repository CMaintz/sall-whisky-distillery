package dk.sallwhisky;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack test against the real H2 database, security chain and demo seed data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BarrelLifecycleIntegrationTest {

    @Autowired TestRestTemplate anonymous;

    private TestRestTemplate admin() {
        return anonymous.withBasicAuth("admin", "admin");
    }

    private static HttpEntity<Object> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void api_requires_authentication() {
        assertThat(anonymous.getForEntity("/api/fade", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void seed_data_is_loaded() {
        ResponseEntity<JsonNode> fade = admin().getForEntity("/api/fade", JsonNode.class);
        ResponseEntity<JsonNode> whisky = admin().getForEntity("/api/whisky", JsonNode.class);

        assertThat(fade.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fade.getBody()).isNotEmpty();
        assertThat(whisky.getBody().findValuesAsText("navn"))
                .contains("TØRV", "MULD", "MOSEDAL SINGLE MALT");
    }

    @Test
    void filling_a_new_barrel_and_enforcing_business_rules() {
        String destilleringId = admin().getForEntity("/api/destilleringer", JsonNode.class)
                .getBody().get(0).get("id").asText();

        JsonNode fad = admin().postForEntity("/api/fade", json(Map.of(
                "literKapacitet", 30, "tidligereIndhold", "Bourbon", "land", "USA",
                "fraAar", "2015-01-01", "leverandoer", "Test")), JsonNode.class).getBody();
        String fadId = fad.get("id").asText();
        assertThat(fad.get("destillat").isNull()).isTrue();

        ResponseEntity<JsonNode> filled = admin().postForEntity("/api/fade/" + fadId + "/destillat",
                json(Map.of("paafyldninger", new Object[]{
                        Map.of("destilleringId", destilleringId, "liter", 25, "medarbejder", "Test")})),
                JsonNode.class);
        assertThat(filled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filled.getBody().get("destillat").get("antalLiter").asDouble()).isEqualTo(25.0);
        assertThat(filled.getBody().get("destillat").get("erKlar").asBoolean()).isFalse();

        ResponseEntity<JsonNode> refill = admin().postForEntity("/api/fade/" + fadId + "/destillat",
                json(Map.of("paafyldninger", new Object[]{
                        Map.of("destilleringId", destilleringId, "liter", 5, "medarbejder", "Test")})),
                JsonNode.class);
        assertThat(refill.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refill.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(refill.getBody().get("detail").asText()).contains("already contains a destillat");

        ResponseEntity<JsonNode> delete = admin().exchange("/api/fade/" + fadId, HttpMethod.DELETE,
                null, JsonNode.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        String whiskyId = admin().postForEntity("/api/whisky", json(Map.of("navn", "UNG")), JsonNode.class)
                .getBody().get("id").asText();
        ResponseEntity<JsonNode> tap = admin().postForEntity("/api/whisky/" + whiskyId + "/tap",
                json(Map.of("fadId", fadId, "medarbejder", "Test")), JsonNode.class);
        assertThat(tap.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tap.getBody().get("detail").asText()).contains("has not matured for 3 years");
    }

    @Test
    void moving_and_rebarreling_between_shelves() {
        JsonNode lager = admin().postForEntity("/api/lagre",
                json(Map.of("navn", "Testlager", "antalReoler", 1, "hylderPerReol", 2)), JsonNode.class).getBody();
        JsonNode hylder = lager.get("reoler").get(0).get("hylder");
        String hylde1 = hylder.get(0).get("id").asText();
        String hylde2 = hylder.get(1).get("id").asText();

        String destilleringId = admin().getForEntity("/api/destilleringer", JsonNode.class)
                .getBody().get(0).get("id").asText();
        String fraId = opretFad();
        String tilId = opretFad();
        admin().postForEntity("/api/fade/" + fraId + "/destillat",
                json(Map.of("paafyldninger", new Object[]{
                        Map.of("destilleringId", destilleringId, "liter", 10, "medarbejder", "Test")})),
                JsonNode.class);

        ResponseEntity<JsonNode> moved = admin().exchange("/api/fade/" + fraId + "/flyt", HttpMethod.PUT,
                json(Map.of("hyldeId", hylde1)), JsonNode.class);
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(moved.getBody().get("hylde").get("lagerNavn").asText()).isEqualTo("Testlager");

        ResponseEntity<JsonNode> occupied = admin().exchange("/api/fade/" + tilId + "/flyt", HttpMethod.PUT,
                json(Map.of("hyldeId", hylde1)), JsonNode.class);
        assertThat(occupied.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<JsonNode> movedAgain = admin().exchange("/api/fade/" + fraId + "/flyt", HttpMethod.PUT,
                json(Map.of("hyldeId", hylde2)), JsonNode.class);
        assertThat(movedAgain.getBody().get("hylde").get("hyldeId").asText()).isEqualTo(hylde2);

        ResponseEntity<Void> omhaeld = admin().postForEntity(
                "/api/fade/" + fraId + "/omhaeld/" + tilId, null, Void.class);
        assertThat(omhaeld.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode til = findFad(tilId);
        assertThat(til.get("destillat").get("modningsHistorik")).hasSize(2);
        assertThat(findFad(fraId).get("destillat").isNull()).isTrue();

        ResponseEntity<JsonNode> emptySource = admin().postForEntity(
                "/api/fade/" + fraId + "/omhaeld/" + tilId, null, JsonNode.class);
        assertThat(emptySource.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private String opretFad() {
        return admin().postForEntity("/api/fade", json(Map.of(
                "literKapacitet", 30, "tidligereIndhold", "Sherry", "land", "Spanien",
                "fraAar", "2010-01-01", "leverandoer", "Test")), JsonNode.class).getBody().get("id").asText();
    }

    private JsonNode findFad(String id) {
        for (JsonNode fad : admin().getForEntity("/api/fade", JsonNode.class).getBody()) {
            if (fad.get("id").asText().equals(id)) {
                return fad;
            }
        }
        throw new AssertionError("Fad not found: " + id);
    }

    @Test
    void unknown_barrel_returns_404_problem_detail() {
        ResponseEntity<JsonNode> response = admin().exchange(
                "/api/fade/00000000-0000-0000-0000-000000000000", HttpMethod.DELETE, null, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("title").asText()).isEqualTo("Resource not found");
    }
}
