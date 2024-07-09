package com.arcadedb.security.serializers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@Jacksonized
public class OpaResult {
    private boolean allow;
    private List<String> roles;
    private Map<String, Object> attributes;
    private List<OpaPolicy> policy;
}
