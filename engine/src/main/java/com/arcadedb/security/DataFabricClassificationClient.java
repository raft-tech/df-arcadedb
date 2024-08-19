package com.arcadedb.security;

import com.arcadedb.GlobalConfiguration;
import com.arcadedb.log.LogManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
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
        String urlString = String.format("%s/api/v1/classification/validate", GlobalConfiguration.DF_CLASSIFICATION_URL.getValueAsString());

        URL url = null;
        try {
            url = new URL(urlString);
            LogManager.instance().log(DataFabricClassificationClient.class, Level.INFO, "Validating " + classification);
            LogManager.instance().log(DataFabricClassificationClient.class, Level.INFO, "Validation URL " + urlString);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            OutputStream os = conn.getOutputStream();
            os.write(classification.getBytes());
            os.flush();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                LogManager.instance().log(
                        DataFabricClassificationClient.class,
                        Level.WARNING,
                        "Validation failed!");
                return false;
            }

            conn.disconnect();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }
}
