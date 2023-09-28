package com.arcadedb.server.security.oidc;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.arcadedb.serializer.json.JSONArray;
import com.arcadedb.serializer.json.JSONObject;
import com.google.gson.JsonElement;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KeycloakClient {

    private static String getBaseKeycloakUrl() {
        return "http://df-keycloak.auth:8080/auth/realms/data-fabric";
    }

    private static String getBaseKeycloakAdminUrl() {
        return "http://df-keycloak.auth:8080/auth/admin/realms/data-fabric";
    }

    private static String getLoginUrl() {
        return getBaseKeycloakUrl() + "/protocol/openid-connect/token";
    }

    private static String login(String username, String password) {
        // TODO replace with keycloak config, or use keycloak login GUI
        Map<String, String> formData = new HashMap<>();
        formData.put("username", username);
        formData.put("password", password);
        formData.put("grant_type", "password");
        formData.put("scope", "openid");
        formData.put("client_id", "df-backend");
        formData.put("client_secret", System.getenv("KEYCLOAK_CLIENT_SECRET"));
        // log.info("login req {}", formData.toString());
        // log.info("getFormDataAsString {}", getFormDataAsString(formData));
        return postUnauthenticatedAndGetResponse(getLoginUrl(), formData);
    }

    private static String loginAndGetEncodedAccessString() {
        var login = login("admin", System.getenv("KEYCLOAK_ADMIN_PASSWORD"));
        // log.debug("getUserRoles login {}", login);

        JSONObject tokenJO = new JSONObject(login);
        return tokenJO.getString("access_token");
    }

    public static String getAccessTokenJsonFromResponse(String token) {
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

    private static String postUnauthenticatedAndGetResponse(String url, Map<String, String> formData) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(formData)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        return sendAndGetResponse(request);
    }

    private static String postAuthenticatedAndGetResponse(String url, String jsonPayload) {
        String accessTokenString = loginAndGetEncodedAccessString();

        log.info("postAuthenticatedAndGetResponse json {}", jsonPayload.toString());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessTokenString)
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

    private static String sendAuthenticatedGetAndGetResponse(String url) {
        String accessTokenString = loginAndGetEncodedAccessString();

        // get user info
        // "http://localhost/auth/admin/realms/data-fabric/users";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Authorization", "Bearer " + accessTokenString)
                .build();

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

    private static String getUserId(String username) {
        String url = getBaseKeycloakAdminUrl() + "/users";
        log.info("getUserId url {}", url);
        var userResponse = sendAuthenticatedGetAndGetResponse(url);
      //  log.info("getUserRoles usersResponse {}", userResponse);

        if (userResponse != null) {
            JSONArray usersJA = new JSONArray(userResponse);
            for (int i = 0; i < usersJA.length(); i++) {
                var user = usersJA.getJSONObject(i);
      //          log.info("getUserId loop check {} {}", user.get("username").toString(), user.getString("username"));
                if (user.getString("username").equals(username)) {
                    // TODO make debug
                    log.info("getUserId for user {}; id {}", username, user.getString("id"));
                    return user.getString("id");
                }
            }
        }

        return null;
    }

    private static String getClientId(String clientName) {
        String url = getBaseKeycloakAdminUrl() + "/clients";
        log.info("getClientId url {}", url);
        var userReponse = sendAuthenticatedGetAndGetResponse(url);
        log.info("getUserRoles userReponse {}", userReponse);

        if (userReponse != null) {
            JSONArray ja = new JSONArray(userReponse);
            for (int i = 0; i < ja.length(); i++) {
                var client = ja.getJSONObject(i);
                if (client.getString("clientId").equals(clientName)) {
                    // TODO make debug
                    log.info("getClientId for client {}; id {}", clientName, client.getString("id"));
                    return client.getString("id");
                }
            }
        }

        return null;
    }

    private static String getClientRoleId(String userId, String clientId, String roleName) {
        // get the role id to assign
        String url = String.format("%s/users/%s/role-mappings/clients/%s/available", getBaseKeycloakAdminUrl(),
                userId, clientId);
        log.info("getClientRoleId url {}", url);
        var userReponse = sendAuthenticatedGetAndGetResponse(url);
   //     log.info("assignRoleToUser responseString {}", userReponse);

        if (userReponse != null) {
            JSONArray ja = new JSONArray(userReponse);
            for (int i = 0; i < ja.length(); i++) {
                var role = ja.getJSONObject(i);
                if (role.getString("name").equals(roleName)) {
                    // TODO make debug
                    log.info("getClientRoleId for role {}; id {}", roleName, role.getString("id"));
                    return role.getString("id");
                }
            }
        }

        return null;
    }

    public static List<String> getUserRoles(String username) {
        String userId = getUserId(username);
        log.info("getUserRoles username {}; userId {}", username, userId);
        if (userId != null) {
            // get user roles
            // http://localhost/auth/admin/realms/data-fabric/users/c8019daf-b6a0-410a-a81a-f91530f1ae36/role-mappings/clients/c4892c81-0c07-4283-b269-2339fb7472ca/available
            String url = String.format("%s/users/%s/role-mappings", getBaseKeycloakAdminUrl(), userId);
            log.info("getUserRoles url {}", url);
            // String url = String.format("%s/users/%s/role-mappings/clients/%s/available",
            // getBaseKeycloakAdminUrl(), userId);
            var rolesResponse = sendAuthenticatedGetAndGetResponse(url);
        //    log.info("getUserRoles rolesResponse {}", rolesResponse);

            if (rolesResponse != null) {
                JSONObject rolesJO = new JSONObject(rolesResponse);
                var realmMappings = rolesJO.getJSONArray("realmMappings");

                if (rolesJO.has("clientMappings")) {
                    var clientMappings = rolesJO.getJSONObject("clientMappings");

         //           log.info("getUserRoles realmMappings {}", realmMappings);
         //           log.info("getUserRoles clientMappings {}", clientMappings);
                    var dfBackend = clientMappings.getJSONObject("df-backend");
                    var mappings = dfBackend.getJSONArray("mappings");

                    List<String> roles = mappings.toList().stream().map(m -> {
                        var jsonObject = (LinkedHashMap<String, Object>) m;
                        return jsonObject.get("name").toString();
                    }).collect(Collectors.toList());

                    log.info("getUserRoles roles {}", roles);
                    return roles;
                }
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

        return new ArrayList<>();
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

    public static void createRole(String roleName) {
        String clientId = getClientId("df-backend");
        log.info("createRole roleName {}, clientId {}", roleName, clientId);

        // TODO parameterize below url with config
        String url = String.format("%s/clients/%s/roles", getBaseKeycloakAdminUrl(), clientId);
        log.info("createRole url {}", url);
        JSONObject request = new JSONObject();
        request.put("name", roleName);

        // TODO extend arcade role to generate a human readable description, and
        // reference it here.
        // formData.put("description", description));
        log.info("creating role {}; {}", roleName, request.toString());
        postAuthenticatedAndGetResponse(url, request.toString());
    }

    public static void deleteRole(String roleName) {

    }

    public static void assignRoleToUser(String roleName, String username) {
        // get the id of the user to assign the role to
        String userId = getUserId(username);
        String clientId = getClientId("df-backend");
        log.info("1 assignRoleToUser username {}, roleName {}, userId {}; clientId {}", username, roleName, userId,
                clientId);
        if (userId != null && clientId != null) {

            // get the role id to assign
            String roleId = getClientRoleId(userId, clientId, roleName);
            log.info("2 assign role to user roleId {}", roleId);
            if (roleId != null) {
                String url = String.format("%s/users/%s/role-mappings/clients/%s", getBaseKeycloakAdminUrl(), userId,
                        clientId);
                log.info("3 assignRoleToUser url {}", url);
                /// auth/admin/realms/{realm}/users/{user_id}/role-mappings/clients/{client_uuid}

                JSONObject jo = new JSONObject();
                jo.put("id", roleId);
                jo.put("name", roleName);
                JSONArray ja = new JSONArray();
                ja.put(jo);
                postAuthenticatedAndGetResponse(url, ja.toString());
            }
        }
    }
}
