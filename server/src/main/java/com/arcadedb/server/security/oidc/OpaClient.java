package com.arcadedb.server.security.oidc;

import com.arcadedb.GlobalConfiguration;
import com.arcadedb.security.serializers.OpaResponse;
import com.arcadedb.server.DataFabricRestClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OpaClient extends DataFabricRestClient {
    private static String getBaseOpaUrl() {
        return String.format("%s/v1/data/datafabric/arcadedb/authz/create_type_enfocements_for_user", GlobalConfiguration.OPA_ROOT_URL.getValueAsString());
    }

    public static OpaResponse getPolicy(String username) {
        var policyResponse = sendAuthenticatedPostAndGetResponse(getBaseOpaUrl(), username);

        try {
            return new ObjectMapper().readValue(policyResponse, OpaResponse.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}