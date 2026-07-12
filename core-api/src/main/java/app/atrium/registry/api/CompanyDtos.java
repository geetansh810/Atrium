package app.atrium.registry.api;

import app.atrium.registry.domain.Company;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

public final class CompanyDtos {

    private CompanyDtos() {}

    public record CreateCompanyRequest(
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "[a-z0-9-]+", message = "lowercase letters, digits and dashes only")
            String slug) {}

    public record CompanyResponse(UUID id, String name, String slug, String planTier, Instant createdAt) {

        public static CompanyResponse from(Company company) {
            return new CompanyResponse(company.getId(), company.getName(), company.getSlug(),
                    company.getPlanTier(), company.getCreatedAt());
        }
    }
}
