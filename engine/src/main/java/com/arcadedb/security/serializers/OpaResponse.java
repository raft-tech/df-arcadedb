package com.arcadedb.security.serializers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@AllArgsConstructor
@Jacksonized
public class OpaResponse {
    private OpaResult result;
}
