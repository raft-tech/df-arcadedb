package com.arcadedb.server.security.oidc;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.arcadedb.GlobalConfiguration;
import com.arcadedb.log.LogManager;
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

    private static final String NOFORN = "user_has_access_to_noforn";
    private static final String FVEY = "has_access_to_fvey";
    private static final String ACGU = "has_access_to_acgu";


    private static String getBaseOpaUrl() {
        return String.format("%s/v1/data/datafabric/accm/postauthorize/authz", GlobalConfiguration.OPA_ROOT_URL.getValueAsString());
    }

    public static OpaResponse getPolicy(String username, Set<String> databaseNames) {
        var policyResponseString = sendAuthenticatedPostAndGetResponse(getBaseOpaUrl(), username);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode opaPolicyJson = null;

        try {
            // Convert string to JSON object
            opaPolicyJson = objectMapper.readTree(policyResponseString);
            opaPolicyJson = opaPolicyJson.get("result");
        } catch (JsonProcessingException e) {
            LogManager.instance().log(OpaClient.class, Level.SEVERE, "Error parsing JSON response from OPA.");
            return null;
        }

        LogManager.instance().log(OpaClient.class, Level.INFO, "OPA policy response: " + opaPolicyJson.toPrettyString());

        // TODO make configurable
        var possibleClassifications = new String[] { "U", "C", "S", "TS" };

        var clearance = opaPolicyJson.get("clearance").asText();

        List<String> authorizedClassificationsList = new ArrayList<>();
        for (String classification : possibleClassifications) {
            authorizedClassificationsList.add(classification);
            if (classification.equals(clearance)) {
                break;
            }
        }

        var hasAccessToNoForn = opaPolicyJson.has(NOFORN) && opaPolicyJson.get(NOFORN).asBoolean();

        // relto
        List<String> relTo = new ArrayList<>(); // TODO Arrays.asList(responseJson.get("releasable_to").asText().split(","));

        String nationality = opaPolicyJson.get("nationality").asText();
        var hasAccessToFvey = opaPolicyJson.has(FVEY) && opaPolicyJson.get(FVEY).asBoolean();
        var hasAccessToAcgu = opaPolicyJson.has(ACGU) && opaPolicyJson.get(ACGU).asBoolean();

        relTo.add(nationality);

        if (hasAccessToFvey) {
          relTo.add("FVEY");
        }

        if (hasAccessToAcgu) {
          relTo.add("ACGU");
        }

        // user has access to fgi if country isn't nationality and relto doesn't contain country

        Expression disclosedData = new Expression();

        List<Argument> disseminationArgs = new ArrayList<>();

        List<Argument> classificationArguments = new ArrayList<>();

        classificationArguments.add(new Argument("components.classification", ArgumentOperator.ANY_OF, authorizedClassificationsList));
    
        if (!hasAccessToNoForn || relTo.isEmpty()) {
            disseminationArgs.add(new Argument("components.disseminationControls", ArgumentOperator.CONTAINS, "NOFORN", true));
        }


        // If foreign national, block access to data with releasable to that doesn't include nationality to tetra they belong to


        // nationality is USA OR nationality/tetra listed in rel to
        
        var argRelTo = new Argument("components.releasableTo", ArgumentOperator.ANY_IN, relTo);

        if (nationality.equals("USA")) {
            argRelTo.setNullEvaluatesToGrantAccess(true);
        }

        disseminationArgs.add(argRelTo);

        // if user has no readons, block all ACCM
        List<Argument> accmArgs = new ArrayList<>();
        // if user has readons, only permit rows where all required readons are present
        if (opaPolicyJson.get("user_has_access_to_accm").asBoolean()) {
            var readons = opaPolicyJson.get("programReadons").asText().replaceAll(" ","").split(",");
            Arrays.asList(readons);

            // need an or expression to support any valid combo of program nicknames
            for (List<String> combo : getAllCombinations(Arrays.asList(readons))) {
                Argument arg = new Argument("components.programNicknames", ArgumentOperator.ALL_IN, combo);
                arg.setNullEvaluatesToGrantAccess(true);
                accmArgs.add(arg);
            }
        } else {
            var arg = new Argument("components.nonICmarkings", ArgumentOperator.CONTAINS, "ACCM", true);
            accmArgs.add(arg);
        }

        Expression accm = new Expression(ExpressionOperator.OR, new ArrayList<>(), accmArgs);

        var allOuterArgs = new ArrayList<Argument>();
        allOuterArgs.addAll(classificationArguments);
        allOuterArgs.addAll(disseminationArgs);

        Expression outer = new Expression();
        outer.setOperator(ExpressionOperator.AND);
        outer.setArguments(allOuterArgs);
        outer.setExpressions(List.of(accm));


        List<Expression> expressions = new ArrayList<>();
        expressions.add(outer);

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

        JSONObject json = new JSONObject(policyResponseString).getJSONObject("result");

        List<String> roles = json.getJSONArray("role_mappings").toList().stream().map(Object::toString).collect(Collectors.toList());

        OpaResult result = new OpaResult(true, roles, json.getJSONObject("user_attributes").toMap(), policies);
        return new OpaResponse(result);
    }

    public static List<List<String>> getAllCombinations(List<String> list) {
        List<List<String>> result = new ArrayList<>();
        generateCombinations(list, 0, new ArrayList<>(), result);
        return result;
    }

    private static void generateCombinations(List<String> list, int index, List<String> current, List<List<String>> result) {
        // Add the current combination to the result
        result.add(new ArrayList<>(current));

        // Generate further combinations
        for (int i = index; i < list.size(); i++) {
            current.add(list.get(i));
            generateCombinations(list, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}