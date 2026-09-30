package dk.sallwhisky.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.admin")
public record AdminCredentialsProperties(@NotBlank String username, @NotBlank String password) {}
