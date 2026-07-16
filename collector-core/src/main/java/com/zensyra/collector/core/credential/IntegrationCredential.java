package com.zensyra.collector.core.credential;

import com.zensyra.collector.core.crypto.AesGcmAttributeConverter;
import com.zensyra.collector.core.sync.IntegrationSource;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "integration_credentials")
public class IntegrationCredential extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private IntegrationSource source;

    @Column(name="client_id", nullable = false)
    private String clientId;

    @Convert(converter = AesGcmAttributeConverter.class)
    @Column(name="client_secret",nullable = false, length = 512)
    private String clientSecret;

    // Second credential for sources that need one (Suunto's Azure APIM
    // subscription key). Nullable: Strava never has it.
    @Convert(converter = AesGcmAttributeConverter.class)
    @Column(name="api_subscription_key", length = 512)
    private String apiSubscriptionKey;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public IntegrationSource getSource() {
        return source;
    }

    public void setSource(IntegrationSource source) {
        this.source = source;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getApiSubscriptionKey() {
        return apiSubscriptionKey;
    }

    public void setApiSubscriptionKey(String apiSubscriptionKey) {
        this.apiSubscriptionKey = apiSubscriptionKey;
    }
}
