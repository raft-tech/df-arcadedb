package com.arcadedb.server.security.oidc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.arcadedb.GlobalConfiguration;
import com.arcadedb.security.ACCM.Argument;
import com.arcadedb.security.ACCM.ArgumentOperator;
import com.arcadedb.security.ACCM.Expression;
import com.arcadedb.security.ACCM.ExpressionOperator;
import com.arcadedb.security.ACCM.GraphType;
import com.arcadedb.security.ACCM.TypeRestriction;
import com.arcadedb.security.serializers.OpaPolicy;
import com.arcadedb.security.serializers.OpaResponse;
import com.arcadedb.security.serializers.OpaResult;
import com.arcadedb.serializer.json.JSONObject;
import com.arcadedb.server.DataFabricRestClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OpaClient extends DataFabricRestClient {
    private static String getBaseOpaUrl() {
        return String.format("%s/v1/data/datafabric/accm/postauthorize/authz", GlobalConfiguration.OPA_ROOT_URL.getValueAsString());
    }

    public static OpaResponse getPolicy(String username, Set<String> databaseNames) {
        var policyResponseString = sendAuthenticatedPostAndGetResponse(getBaseOpaUrl(), username);

        System.out.println("policy response string: " + policyResponseString);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson = null;

        try {
            // Convert string to JSON object
            responseJson = objectMapper.readTree(policyResponseString);
            responseJson = responseJson.get("result");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }

        //var response = OpaClient.getPolicy(username);

        var possibleClassifications = new String[] { "U", "C", "S", "TS" };
        var clearance = responseJson.get("clearance_usa").asText();


        // TODO look at this. Don't think it's right
    //    String[] authorizedClassifications = List.of(possibleClassifications).subList(0, List.of(possibleClassifications).indexOf(clearance) + 1).toArray(new String[0]);
        
        List<String> authorizedClassificationsList = new ArrayList<>();
        for (String classification : possibleClassifications) {
            authorizedClassificationsList.add(classification);
            if (classification.equals(clearance)) {
                break;
            }
        }

        
        System.out.println("authorized clasifications: " + authorizedClassificationsList);

        var hasAccessToNoforn = responseJson.get("user_has_access_to_noforn").asBoolean();

        // relto
        List<String> relTo = new ArrayList<>();// Arrays.asList(responseJson.get("releasable_to").asText().split(","));

        String nationality = "USA";
        var hasAccessToFvey = responseJson.get("has_access_to_fvey").asBoolean();
        var hasAccessToAcgu = responseJson.get("has_access_to_acgu").asBoolean();

        relTo.add(nationality);

        if (hasAccessToFvey) {
          relTo.add("FVEY");
        }

        if (hasAccessToAcgu) {
          relTo.add("ACGU");
        }

        // user has access to fgi if country isn't nationality and relto doesn't contain country

        Expression disclosedData = new Expression();

        List<Argument> accmArgs = new ArrayList<>();

        accmArgs.add(new Argument("classification.components.classification", ArgumentOperator.ANY_OF, authorizedClassificationsList));
    
        if (!hasAccessToNoforn) {
          accmArgs.add(new Argument("classification.components.disseminationControls", ArgumentOperator.NEQ, "NOFORN", true));
        }

        accmArgs.add(new Argument("classification.components.releasableTo", ArgumentOperator.ANY_IN, relTo));

        Expression accm = new Expression();
        accm.setOperator(ExpressionOperator.AND);
        accm.setArguments(accmArgs);

        Expression expressionOuter = new Expression();

        List<Expression> expressions = new ArrayList<>();
        expressions.add(accm);

        TypeRestriction typeRestrictionEdge = new TypeRestriction("*", GraphType.EDGE, expressions, expressions, expressions, expressions);
        TypeRestriction typeRestrictionVertex = new TypeRestriction("*", GraphType.VERTEX, expressions, expressions, expressions, expressions);

        List<TypeRestriction> typeRestrictions = new ArrayList<>();
        typeRestrictions.add(typeRestrictionEdge);
        typeRestrictions.add(typeRestrictionVertex);

        List<OpaPolicy> policies = new ArrayList<>();

        databaseNames.forEach( (databaseName) -> {
            OpaPolicy policy = new OpaPolicy(databaseName, new ArrayList<>(), typeRestrictions);
            policies.add(policy);
        });

        JSONObject json = new JSONObject(policyResponseString);

        List<String> roles = json.getJSONObject("result").getJSONArray("role_mappings").toList().stream().map(Object::toString).collect(Collectors.toList());

        OpaResult result = new OpaResult(true, roles, new HashMap<>(), policies);
        return new OpaResponse(result);
    }
}