package com.arcadedb.security.serializers;

import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Data
@Jacksonized
public class OpaResult {
    private List<OpaPolicy> Policy;
}
