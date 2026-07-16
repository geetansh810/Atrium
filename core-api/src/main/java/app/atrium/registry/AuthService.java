package app.atrium.registry;

import app.atrium.common.ConflictException;
import app.atrium.common.JwtService;
import app.atrium.common.UnauthorizedException;
import app.atrium.registry.api.AuthDtos.AuthResponse;
import app.atrium.registry.api.AuthDtos.LoginRequest;
import app.atrium.registry.api.AuthDtos.SignupRequest;
import app.atrium.registry.api.CompanyDtos.CreateCompanyRequest;
import app.atrium.registry.domain.Company;
import app.atrium.registry.domain.CompanyRepository;
import app.atrium.registry.domain.User;
import app.atrium.registry.domain.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one pre-auth surface (04 §Auth, M3.1) — replaces the old dev-header
 * bootstrap. Signup creates the company AND its first admin user atomically;
 * there is no other way to create a company now (registry.CompanyController's
 * old standalone {@code POST /companies} is gone).
 */
@Service
public class AuthService {

    private final CompanyService companyService;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(CompanyService companyService, CompanyRepository companies,
                        UserRepository users, JwtService jwtService) {
        this.companyService = companyService;
        this.companies = companies;
        this.users = users;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        users.findByEmail(request.email()).ifPresent(existing -> {
            throw new ConflictException("Email '" + request.email() + "' is already registered");
        });

        // Slug-uniqueness check + save stays in CompanyService — the same rule
        // every OTHER company-creating path would need, not duplicated here.
        Company company = companyService.create(
                new CreateCompanyRequest(request.companyName(), request.companySlug()));
        User user = users.save(new User(company.getId(), request.displayName(), request.email(),
                passwordEncoder.encode(request.password())));

        String token = jwtService.issue(user.getId(), company.getId(), user.getRole());
        return AuthResponse.of(token, company, user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        Company company = companies.findById(user.getCompanyId())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        String token = jwtService.issue(user.getId(), company.getId(), user.getRole());
        return AuthResponse.of(token, company, user);
    }
}
