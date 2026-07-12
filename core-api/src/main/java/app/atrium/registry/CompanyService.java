package app.atrium.registry;

import app.atrium.common.ConflictException;
import app.atrium.common.NotFoundException;
import app.atrium.registry.api.CompanyDtos.CreateCompanyRequest;
import app.atrium.registry.domain.Company;
import app.atrium.registry.domain.CompanyRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {

    private final CompanyRepository companies;

    public CompanyService(CompanyRepository companies) {
        this.companies = companies;
    }

    @Transactional
    public Company create(CreateCompanyRequest request) {
        companies.findBySlug(request.slug()).ifPresent(existing -> {
            throw new ConflictException("Slug '" + request.slug() + "' is already taken");
        });
        return companies.save(new Company(request.name(), request.slug()));
    }

    /** Path id must match the tenant — other companies are indistinguishable from absent. */
    @Transactional(readOnly = true)
    public Company get(UUID tenantCompanyId, UUID pathCompanyId) {
        if (!tenantCompanyId.equals(pathCompanyId)) {
            throw NotFoundException.of("Company", pathCompanyId);
        }
        return companies.findById(pathCompanyId)
                .orElseThrow(() -> NotFoundException.of("Company", pathCompanyId));
    }
}
