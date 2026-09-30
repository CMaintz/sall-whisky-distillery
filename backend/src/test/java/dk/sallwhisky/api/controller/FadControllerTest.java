package dk.sallwhisky.api.controller;

import dk.sallwhisky.api.dto.response.FadResponse;
import dk.sallwhisky.config.SecurityConfig;
import dk.sallwhisky.domain.service.FadService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FadController.class)
@Import(SecurityConfig.class)
class FadControllerTest {

    @Autowired MockMvc mvc;
    @MockBean FadService fadService;

    private static FadResponse fad(String nummer) {
        return new FadResponse(UUID.randomUUID(), nummer, 40, "Sherry", "Spanien",
                LocalDate.of(2004, 1, 1), "Fadpusheren", 20, null, null, false);
    }

    @Test
    void rejects_requests_without_credentials() throws Exception {
        mvc.perform(get("/api/fade")).andExpect(status().isUnauthorized());
        verifyNoInteractions(fadService);
    }

    @Test
    void rejects_wrong_password() throws Exception {
        mvc.perform(get("/api/fade").with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lists_barrels() throws Exception {
        when(fadService.getAlleFade()).thenReturn(List.of(fad("F-001"), fad("F-002")));

        mvc.perform(get("/api/fade").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fadNummer").value("F-001"));
    }

    @Test
    void creates_barrel_with_201() throws Exception {
        when(fadService.opretFad(any())).thenReturn(fad("F-010"));

        mvc.perform(post("/api/fade").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"literKapacitet":40,"tidligereIndhold":"Sherry","land":"Spanien",
                                 "fraAar":"2004-01-01","leverandoer":"Fadpusheren"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fadNummer").value("F-010"));
    }

    @Test
    void invalid_body_returns_400_problem_detail() throws Exception {
        mvc.perform(post("/api/fade").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"literKapacitet":0,"tidligereIndhold":"","land":"Spanien",
                                 "fraAar":"2004-01-01","leverandoer":"Fadpusheren"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(fadService);
    }

    @Test
    void illegal_state_maps_to_409_problem_detail() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("Cannot delete a barrel that contains a destillat"))
                .when(fadService).sletFad(id);

        mvc.perform(delete("/api/fade/{id}", id).with(httpBasic("admin", "admin")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Cannot delete a barrel that contains a destillat"));
    }

    @Test
    void illegal_argument_maps_to_400_problem_detail() throws Exception {
        UUID id = UUID.randomUUID();
        when(fadService.flytFad(eq(id), any())).thenThrow(new IllegalArgumentException("bad shelf"));

        mvc.perform(put("/api/fade/{id}/flyt", id).with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hyldeId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("bad shelf"));
    }

    @Test
    void missing_entity_maps_to_404_problem_detail() throws Exception {
        UUID fra = UUID.randomUUID();
        UUID til = UUID.randomUUID();
        doThrow(new EntityNotFoundException("Source barrel not found"))
                .when(fadService).omhaeldDestillat(fra, til);

        mvc.perform(post("/api/fade/{fra}/omhaeld/{til}", fra, til).with(httpBasic("admin", "admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }
}
