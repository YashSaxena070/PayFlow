package com.payflow.provider.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "providers")
public class Provider {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "adapter_type", nullable = false, length = 50)
    private String adapterType;

    @Column(name = "capabilities_json", nullable = false, columnDefinition = "TEXT")
    private String capabilitiesJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Provider() {}

    public Provider(UUID id, String code, String displayName, boolean enabled) {
        this(id, code, displayName, enabled, code.toLowerCase(), "{}");
    }

    public Provider(UUID id, String code, String displayName, boolean enabled, String adapterType, String capabilitiesJson) {
        this.id = id != null ? id : UUID.randomUUID();
        this.code = code;
        this.displayName = displayName;
        this.enabled = enabled;
        this.adapterType = adapterType;
        this.capabilitiesJson = capabilitiesJson;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAdapterType() {
        return adapterType;
    }

    public String getCapabilitiesJson() {
        return capabilitiesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
