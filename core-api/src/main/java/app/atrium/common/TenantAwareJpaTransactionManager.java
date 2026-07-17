package app.atrium.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * A {@link JpaTransactionManager} that stamps every transaction with the two
 * Postgres session GUCs M3.2's Row Level Security policies read (08
 * §Security rule 6, 03 §V3) — {@code app.company_id} from {@link
 * TenantContext} when bound, and {@code app.bypass_rls} for the documented
 * cross-tenant system components. This is the ONE place those GUCs are set:
 * {@code doBegin} runs after the superclass has opened the transaction (so a
 * connection genuinely exists) and before any repository/JdbcTemplate call in
 * the method body executes, using {@code set_config(...)} rather than a bare
 * {@code SET LOCAL} because the JDBC driver can't bind a parameter into a SET
 * statement.
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    public TenantAwareJpaTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(getEntityManagerFactory());
        if (em == null) {
            return;
        }
        String companyId = TenantContext.isBound() ? TenantContext.requireCompanyId().toString() : "";
        em.createNativeQuery("SELECT set_config('app.company_id', :v, true)")
                .setParameter("v", companyId)
                .getSingleResult();
        em.createNativeQuery("SELECT set_config('app.bypass_rls', :v, true)")
                .setParameter("v", TenantContext.isBypass() ? "on" : "off")
                .getSingleResult();
    }
}
