package app.atrium.common;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Replaces Spring Boot's auto-configured {@code JpaTransactionManager} (which
 * is only created {@code @ConditionalOnMissingBean}) with {@link
 * TenantAwareJpaTransactionManager} — every other transaction manager
 * behavior (dataSource auto-detection from the EntityManagerFactory, mixing
 * JPA + raw JdbcTemplate in one transaction) is unchanged.
 */
@Configuration
public class TenantAwareTransactionConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new TenantAwareJpaTransactionManager(entityManagerFactory);
    }
}
