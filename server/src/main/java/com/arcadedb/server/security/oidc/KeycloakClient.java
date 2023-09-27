package com.arcadedb.server.security.oidc;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.arcadedb.serializer.json.JSONArray;
import com.arcadedb.serializer.json.JSONObject;
import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KeycloakClient {

    public static String login(String username, String password) {
        // TODO replace with keycloak config, or use keycloak login GUI
        Map<String, String> formData = new HashMap<>();
        formData.put("username", username);
        formData.put("password", password);
        formData.put("grant_type", "password");
        formData.put("scope", "openid");
        formData.put("client_id", "df-backend");
        formData.put("client_secret", System.getenv("KEYCLOAK_CLIENT_SECRET"));
        log.info("login req {}", formData.toString());
        log.info("getFormDataAsString {}", getFormDataAsString(formData));
        return postAndGetResponse(formData);
    }

    public static String getAccessTokenFromResponse(String token) {
        if (token != null) {
            JSONObject tokenJO = new JSONObject(token);
            String accessTokenString = tokenJO.getString("access_token");
            String encodedString = accessTokenString.substring(accessTokenString.indexOf(".") + 1,
                    accessTokenString.lastIndexOf("."));
            byte[] decodedBytes = Base64.getDecoder().decode(encodedString);
            String decodedString = new String(decodedBytes);
            log.info("getAccessTokenFromResponse {}", decodedString);

            return decodedString;
        }

        return null;
    }

    private static String postAndGetResponse(Map<String, String> formData) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://df-keycloak.auth:8080/auth/realms/data-fabric/protocol/openid-connect/token"))
                .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(formData)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        return sendAndGetResponse(request);
    }

    private static String sendAndGetResponse(HttpRequest request) {
        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                log.warn("sendAndGetResponse {} {}", response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            log.error("sendAndGetResponse", e);
            return null;
        }

        return null;
    }

    public static List<String> getUserRoles(String username) {

        var login = login("admin", System.getenv("KEYCLOAK_ADMIN_PASSWORD"));
        // var token = getAccessTokenFromResponse(login);
        log.info("getUserRoles login {}", login);
        // log.info("getUserRoles token {}", token);

        JSONObject tokenJO = new JSONObject(login);
        String accessTokenString = tokenJO.getString("access_token");

        log.info("getUserRoles accessTokenString {}", accessTokenString);

        // get user info
        // "http://localhost/auth/admin/realms/data-fabric/users";
        HttpRequest userRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://df-keycloak.auth:8080/auth/admin/realms/data-fabric/users"))
                .GET()
                .header("Authorization", "Bearer " + accessTokenString)
                .build();

        var userReponse = sendAndGetResponse(userRequest);
        log.info("getUserRoles userReponse {}", userReponse);

        String userId = null;

        if (userReponse != null) {
            // Gson gson = new Gson();
            // String jsonString = gson.toJson(userReponse);
            // JSONObject userJO = new JSONObject(jsonString);
            JSONArray usersJA = new JSONArray(userReponse);
         //   var users = usersJA.getJSONArray("users");
            for (int i = 0; i < usersJA.length(); i++) {
                var user = usersJA.getJSONObject(i);
                if (user.getString("username").equals(username)) {
                    userId = user.getString("id");
                    log.info("id {}", userId);
                }
            }
        }

        if (userId != null) {
            // get user roles
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "http://df-keycloak.auth:8080/auth/admin/realms/data-fabric/users/" + userId
                                    + "/role-mappings"))
                    .GET()
                    .header("Authorization", "Bearer " + accessTokenString)
                    .build();

            var rolesResponse = sendAndGetResponse(request);
            log.info("getUserRoles rolesResponse {}", rolesResponse);

            if (rolesResponse != null) {
                JSONObject rolesJO = new JSONObject(rolesResponse);
                var realmMappings = rolesJO.getJSONArray("realmMappings");
                var clientMappings = rolesJO.getJSONObject("clientMappings");
                log.info("getUserRoles realmMappings {}", realmMappings);
                log.info("getUserRoles clientMappings {}", clientMappings);
                var dfBackend = clientMappings.getJSONObject("df-backend");
                var mappings = dfBackend.getJSONArray("mappings");

               // Object o;
                // cast object to linkedhashmap<String, Object>
                // var jsonObject = (LinkedHashMap<String, Object>) o;



                List<String> roles = mappings.toList().stream().map(m -> {
                    var jsonObject = (LinkedHashMap<String, Object>) m;
                    return jsonObject.get("name").toString();
                }).collect(Collectors.toList());

                log.info("getUserRoles roles {}", roles);
                return roles;
            }
            // {
            // "realmMappings": [
            // {
            // "id": "e5c133b6-6207-4b0a-ac22-5cb6b5d4d450",
            // "name": "default-roles-data-fabric",
            // "description": "${role_default-roles}",
            // "composite": true,
            // "clientRole": false,
            // "containerId": "data-fabric"
            // }
            // ],
            // "clientMappings": {
            // "df-backend": {
            // "id": "c4892c81-0c07-4283-b269-2339fb7472ca",
            // "client": "df-backend",
            // "mappings": [
            // {
            // "id": "5188b4b3-25c3-4ac7-8700-d78bdccb682f",
            // "name": "arcade__admin__updateSchema",
            // "description": "",
            // "composite": false,
            // "clientRole": true,
            // "containerId": "c4892c81-0c07-4283-b269-2339fb7472ca"
            // }
            // ]
            // }
            // }
            // }

        }

        return null;
    }

    // public static String impersonateLogin(String username) {
    // var token = login(System.getenv("KEYCLOAK_ADMIN_USERNAME"),
    // System.getenv("KEYCLOAK_ADMIN_PASSWORD"));
    // Map<String, String> formData = new HashMap<>();
    // formData.put("grant_type",
    // "urn:ietf:params:oauth:grant-type:token-exchange");
    // formData.put("subject_token", getAccessTokenFromResponse(token));
    // formData.put("client_id", "df-backend");
    // formData.put("requested_subject", username);

    // log.info("impersonateLogin req {}", getFormDataAsString(formData));

    // return postAndGetResponse(formData);
    // }

    public static String getFormDataAsString(Map<String, String> formData) {
        StringBuilder formBodyBuilder = new StringBuilder();
        for (Map.Entry<String, String> singleEntry : formData.entrySet()) {
            if (formBodyBuilder.length() > 0) {
                formBodyBuilder.append("&");
            }
            formBodyBuilder.append(URLEncoder.encode(singleEntry.getKey(), StandardCharsets.UTF_8));
            formBodyBuilder.append("=");
            formBodyBuilder.append(URLEncoder.encode(singleEntry.getValue(), StandardCharsets.UTF_8));
        }
        return formBodyBuilder.toString();
    }
}
