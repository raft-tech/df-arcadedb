package com.arcadedb.security;

import com.arcadedb.GlobalConfiguration;
import com.arcadedb.log.LogManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;

public class DataFabricClassificationClient {

    /**
     * Returns true if the given classification document is a valid classification structure for this data fabric
     * deployment. If the given classification is greater than the global configuration, then is it still
     * considered invalid.
     *
     * @return true if the classification structure is valid and meets classification level of the data fabric deployment
     */
    public static boolean validateDocumentClassification(String classification) {
        String url = String.format("%s/api/v1/classification/validate", GlobalConfiguration.DF_CLASSIFICATION_URL);
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(classification)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return true;
            }
        } catch (JsonProcessingException e) {
            LogManager.instance().log(DataFabricClassificationClient.class, Level.WARNING, "Failed to parse classification!");
            throw new RuntimeException(e);
        } catch (IOException | InterruptedException e) {
            LogManager.instance().log(DataFabricClassificationClient.class, Level.WARNING, "Failed to send validation request!");
            throw new RuntimeException(e);
        }

        return false;
    }
}
