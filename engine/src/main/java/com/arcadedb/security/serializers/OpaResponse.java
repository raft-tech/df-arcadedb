package com.arcadedb.security.serializers;

import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Jacksonized
public class OpaResponse {
    private OpaResult result;
}
