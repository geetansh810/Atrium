package app.atrium.registry.api;

import app.atrium.registry.domain.Company;
import app.atrium.registry.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record SignupRequest(
            @NotBlank String companyName,
            @NotBlank @Pattern(regexp = "[a-z0-9-]+", message = "lowercase letters, digits and dashes only")
            String companySlug,
            @NotBlank String displayName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record AuthResponse(
            String token, UUID companyId, String companyName, String companySlug,
            UUID userId, String displayName, String role) {

        public static AuthResponse of(String token, Company company, User user) {
            return new AuthResponse(token, company.getId(), company.getName(), company.getSlug(),
                    user.getId(), user.getDisplayName(), user.getRole());
        }
    }
}
