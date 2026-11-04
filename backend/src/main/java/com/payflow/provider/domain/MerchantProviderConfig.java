package com.payflow.provider.domain;

import com.payflow.merchant.domain.Merchant;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "merchant_provider_configs", uniqueConstraints = {
        @UniqueConstraint(name = "uq_merchant_provider", columnNames = {"merchant_id", "provider_id"})
})
public class MerchantProviderConfig {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private int priority = 1;

    @Column(nullable = false)
    private int weight = 100;

    @Column(name = "encrypted_config_reference")
    private String encryptedConfigReference;

    public MerchantProviderConfig() {}

    public MerchantProviderConfig(UUID id, Merchant merchant, Provider provider, boolean enabled, int priority, int weight) {
        this.id = id != null ? id : UUID.randomUUID();
        this.merchant = merchant;
        this.provider = provider;
        this.enabled = enabled;
        this.priority = priority;
        this.weight = weight;
    }

    public UUID getId() {
        return id;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public Provider getProvider() {
        return provider;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getEncryptedConfigReference() {
        return encryptedConfigReference;
    }

    public void setEncryptedConfigReference(String encryptedConfigReference) {
        this.encryptedConfigReference = encryptedConfigReference;
    }
}

