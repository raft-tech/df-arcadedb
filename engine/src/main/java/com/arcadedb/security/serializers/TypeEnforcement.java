package com.arcadedb.security.serializers;

import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

@Data
@Jacksonized
public class TypeEnforcement {
    private String database;
    private String permissions;
    private String type;
    private Map<String, Object> attributes;
}
