package com.arcadedb.server.security.oidc.role;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

public enum RoleType {
    DATABASE_ADMIN("dba", "dba"),
    USER("user", "user"),
    SERVER_ADMIN("sa","sa");

    @Getter
    private String keycloakName;

    private String arcadeName;

    RoleType(String keycloakName, String arcadeName) {
        this.keycloakName = keycloakName;
        this.arcadeName = arcadeName;
    }

    @JsonValue
    public String getArcadeName() {
        return arcadeName;
    }

    public static RoleType fromKeycloakName(String keycloakName) {
        for (RoleType roleType : RoleType.values()) {
            if (roleType.keycloakName.equalsIgnoreCase(keycloakName)) {
                return roleType;
            }
        }
        return null;
    }
}
