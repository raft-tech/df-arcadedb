package com.arcadedb.security.serializers;

import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

@Data
@Jacksonized
public class OpaResult {
    private boolean allow;
    private List<String> roles;
    private Map<String, Object> attributes;
    private List<OpaPolicy> policy;
}
