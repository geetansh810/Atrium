package app.atrium.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the web dashboard (M0.8) — the browser calls core-api cross-origin
 * from Vite's dev server. Dev auth headers (X-Company-Id/X-User-Id) must be
 * explicitly allowed or the preflight rejects them.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${atrium.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
