package app.atrium.registry.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Models are DATA (13 §1.2). Global table — no company_id. */
@Entity
@Table(name = "model_catalog")
public class ModelCatalogEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "context_window", nullable = false)
    private int contextWindow;

    @Column(name = "max_output_tokens", nullable = false)
    private int maxOutputTokens;

    @Column(name = "price_in_micro_usd_per_mtok", nullable = false)
    private long priceInMicroUsdPerMtok;

    @Column(name = "price_out_micro_usd_per_mtok", nullable = false)
    private long priceOutMicroUsdPerMtok;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode capabilities;

    /** fast | balanced | deep */
    @Column(nullable = false)
    private String tier;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ModelCatalogEntry() {}

    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public String getModelName() { return modelName; }
    public String getDisplayName() { return displayName; }
    public int getContextWindow() { return contextWindow; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public long getPriceInMicroUsdPerMtok() { return priceInMicroUsdPerMtok; }
    public long getPriceOutMicroUsdPerMtok() { return priceOutMicroUsdPerMtok; }
    public JsonNode getCapabilities() { return capabilities; }
    public String getTier() { return tier; }
    public boolean isEnabled() { return enabled; }
    public Instant getUpdatedAt() { return updatedAt; }
}
