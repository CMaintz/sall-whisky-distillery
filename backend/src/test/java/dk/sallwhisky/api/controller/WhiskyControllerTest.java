package dk.sallwhisky.api.controller;

import dk.sallwhisky.api.dto.response.WhiskyProduktResponse;
import dk.sallwhisky.config.SecurityConfig;
import dk.sallwhisky.domain.service.WhiskyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WhiskyController.class)
@Import(SecurityConfig.class)
class WhiskyControllerTest {

    @Autowired MockMvc mvc;
    @MockBean WhiskyService whiskyService;

    @Test
    void tapping_an_immature_barrel_returns_409() throws Exception {
        UUID whiskyId = UUID.randomUUID();
        when(whiskyService.tapFad(eq(whiskyId), any()))
                .thenThrow(new IllegalStateException("Destillat in barrel F-003 has not matured for 3 years yet"));

        mvc.perform(post("/api/whisky/{id}/tap", whiskyId).with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fadId\":\"" + UUID.randomUUID() + "\",\"medarbejder\":\"Chris\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Destillat in barrel F-003 has not matured for 3 years yet"));
    }

    @Test
    void adding_water_passes_litres_to_service() throws Exception {
        UUID whiskyId = UUID.randomUUID();
        when(whiskyService.tilfoejVand(whiskyId, 5.0)).thenReturn(new WhiskyProduktResponse(
                whiskyId, "MULD", 60.0, 45.0, 5.0, "Single Cask", 0, List.of(), List.of()));

        mvc.perform(post("/api/whisky/{id}/vand", whiskyId).with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liter\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whiskyType").value("Single Cask"));
        verify(whiskyService).tilfoejVand(whiskyId, 5.0);
    }

    @Test
    void non_positive_water_is_rejected() throws Exception {
        mvc.perform(post("/api/whisky/{id}/vand", UUID.randomUUID()).with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liter\":-1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(whiskyService);
    }
}
