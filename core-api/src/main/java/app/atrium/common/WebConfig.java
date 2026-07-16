package app.atrium.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the web dashboard (M0.8) — the browser calls core-api cross-origin
 * from Vite's dev server. {@code allowedHeaders("*")} covers {@code
 * Authorization} (M3.1's JWT bearer) same as it did the old dev headers.
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
