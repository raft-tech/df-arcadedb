package com.arcadedb.security.serializers;

import com.arcadedb.security.ACCM.TypeRestriction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Data
@AllArgsConstructor
@Jacksonized
public class OpaPolicy {
    private String database;
    private List<String> permissions;
    private List<TypeRestriction> typeRestrictions;
}
