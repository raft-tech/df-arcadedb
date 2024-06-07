package com.arcadedb.security.serializers;

import com.arcadedb.security.ACCM.TypeRestriction;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

@Data
@Jacksonized
public class OpaPolicy {
    private String database;
    private List<String> permissions;
    private List<TypeRestriction> typeRestrictions;
}
