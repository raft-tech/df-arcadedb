package com.arcadedb.server.security.oidc.role;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

public enum CRUDPermission {
    CREATE("C", "createRecord"),
    READ("R", "readRecord"),
    UPDATE("U", "updateRecord"),
    DELETE("D", "deleteRecord");

    @Getter
    private String keycloakPermissionAbbreviation;
    private String arcadeName;

    CRUDPermission(String keycloakPermissionAbbreviation, String arcadeName) {
        this.keycloakPermissionAbbreviation = keycloakPermissionAbbreviation;
        this.arcadeName = arcadeName;
    }

    @JsonValue
    public String getArcadeName() {
        return arcadeName;
    }

    public static CRUDPermission fromKeycloakPermissionAbbreviation(String keycloakPermissionAbbreviation) {
        for (CRUDPermission crud : CRUDPermission.values()) {
            if (crud.keycloakPermissionAbbreviation.equalsIgnoreCase(keycloakPermissionAbbreviation)) {
                return crud;
            }
        }
        return null;
    }

    public static List<CRUDPermission> getAll(){
        return List.of(CRUDPermission.values());
    }
}
