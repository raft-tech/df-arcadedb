package com.arcadedb.server.http.handler;

import java.util.Deque;

import com.arcadedb.serializer.json.JSONArray;
import com.arcadedb.serializer.json.JSONObject;
import com.arcadedb.server.DataFabricRestClient;
import com.arcadedb.server.http.HttpServer;
import com.arcadedb.server.security.ServerSecurityUser;

import io.undertow.server.HttpServerExchange;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetHistoryHandler extends AbstractHandler {
    public GetHistoryHandler(final HttpServer httpServer) {
      super(httpServer);
    }
  
    @Override
    protected ExecutionResponse execute(HttpServerExchange exchange, ServerSecurityUser user) {
        try {
            // Grab all params from request path
            final Deque<String> databaseParam = exchange.getQueryParameters().get("database");
            String database = databaseParam.isEmpty() ? null : databaseParam.getFirst().trim();
            if (database != null && database.isEmpty()) {
            database = null;
            }

            if (database == null) {
                return new ExecutionResponse(400, "{ \"error\" : \"Database parameter is null\"}");
            }

            final Deque<String> entityTypeParam = exchange.getQueryParameters().get("entityType");
            String entityType = entityTypeParam.isEmpty() ? null : entityTypeParam.getFirst().trim();
            if (entityType != null && entityType.isEmpty()) {
                entityType = null;
            }

            if (entityType == null) {
                return new ExecutionResponse(400, "{ \"error\" : \"EntityType parameter is null\"}");
            }

            final Deque<String> ridParam = exchange.getQueryParameters().get("rid");
            String rid = ridParam.isEmpty() ? null : ridParam.getFirst().trim();
            if (rid != null && rid.isEmpty()) {
            rid = null;
            }

            if (rid == null) {
                return new ExecutionResponse(400, "{ \"error\" : \"Rid parameter is null\"}");
            }

            rid = "#" + rid;

            // Make REST request to lakehouse
            String url = "http://df-lakehouse/api/v1/lakehouse/schemas/arcadedbcdc_" + database;
            log.info("history request url {}", url);
            String query = String.format(
                "SELECT eventid, from_unixtime(CAST(timestamp AS BIGINT)/1000) as datetime, eventtype, username, entityid, eventpayload " +
                "FROM arcadedbcdc_%s.admin_%s WHERE entityname = '%s' AND entityid = '%s' ORDER BY timestamp DESC", database, database, entityType, rid);
            JSONObject body = new JSONObject();
            body.put("sql", query);
            log.info("query {}", body);

            String response = DataFabricRestClient.postAuthenticatedAndGetResponse(url, body.toString());
            log.info("response {}", response);
            if (response != null) {
                
                if(response.trim().startsWith("{")) {
                    var ja = new JSONObject(response);
                    return new ExecutionResponse(200, "{ \"result\" : " + ja + "}");
                } else {
                    var ja = new JSONArray(response);
                    return new ExecutionResponse(200, "{ \"result\" : " + ja + "}");
                }
            } else {
                return new ExecutionResponse(400, "{ \"result\" : bad request}");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ExecutionResponse(400, "{ \"result\" : bad request}");
    }
}
