package com.arcadedb.server.http.handler;


import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Deque;

import com.arcadedb.database.Database;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.database.RID;
import com.arcadedb.database.Record;
import com.arcadedb.database.Utils;
import com.arcadedb.serializer.json.JSONObject;
import com.arcadedb.server.ArcadeDBServer;
import com.arcadedb.server.DataFabricRestClient;
import com.arcadedb.server.http.HttpServer;
import com.arcadedb.server.security.ServerSecurityUser;

import io.undertow.server.HttpServerExchange;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostRollbackHistoryHandler extends AbstractHandler {
    public PostRollbackHistoryHandler(final HttpServer httpServer) {
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

        final Deque<String> eventIdParam = exchange.getQueryParameters().get("eventId");
        String eventId = eventIdParam.isEmpty() ? null : eventIdParam.getFirst().trim();
        if (eventId != null && eventId.isEmpty()) {
            eventId = null;
        }

        if (eventId == null) {
            return new ExecutionResponse(400, "{ \"error\" : \"EventId parameter is null\"}");
        }

        // Make REST request to lakehouse
        String url = "http://df-lakehouse/api/v1/lakehouse/schemas/arcadedbcdc_" + database;
        log.info("history request url {}", url);
        String query = String.format(
            "SELECT CAST(MAP_FROM_ENTRIES(ARRAY[('eventId', eventid ), ('timestamp ', CAST(from_unixtime(CAST(timestamp AS BIGINT)/1000) AS VARCHAR)), " +
            " ('entityId', entityid ), ('user', username), ('eventType', eventType), ('entity', eventpayload)]) AS JSON) AS history " +
            "FROM arcadedbcdc_%s.admin_%s WHERE entityname = '%s' AND entityid = '%s' AND eventid = '%s'", database, database, entityType, rid, eventId);
        JSONObject body = new JSONObject();
        body.put("sql", query);
        log.info("query {}", body);

        String response = DataFabricRestClient.postAuthenticatedAndGetResponse(url, body.toString());
        log.info("response {}", response);
        if (response != null) {

            var ja = new JSONObject(response);
            var arr = ja.getJSONArray("data");
            log.info("data {}", arr);
            if (arr.length() == 1) {

                String value = arr.getString(0);

                // value = value.replaceAll("(\\w+):", "\"$1\":")
                //                            .replaceAll(":\\s*(\\w+)", ":\"$1\"");

                log.info("cleaned value {}", value);

            //    log.info("1 {} {}", arr.get(0).getClass().getSimpleName(), arr.get(0));
                log.info("1 {} {}", new JSONObject(value));
                var payload = new JSONObject(value).getJSONObject("history").getString("entity");
                log.info("payload {}", payload);
                var content = new JSONObject(payload);
                log.info("content {}", content);
                
                final ArcadeDBServer server = httpServer.getServer();
                var activeDatabase = server.getDatabase(database);
                Record record = server.getDatabase(database).lookupByRID(new RID(activeDatabase, rid), true);
               // Document mutableDocument = MutableDocument;

                MutableDocument mutable = record.asDocument().modify();
                mutable.fromJSON(content);

            // LocalDateTime createdDate = null;
            // if (record.get(Utils.CREATED_DATE) instanceof Long) {
            //   createdDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(originalRecord.getLong(Utils.CREATED_DATE)), ZoneId.systemDefault());
            // } else {
            //   createdDate = originalRecord.getLocalDateTime(Utils.CREATED_DATE);
            // }

            // Document recordDoc = localRecord.asDocument(true);

            // // Overwrite created by and date with the original record value to keep a user from changing it...
            // ((MutableDocument) recordDoc).set(Utils.CREATED_DATE, createdDate);
            // ((MutableDocument) recordDoc).set(Utils.LAST_MODIFIED_BY, httpServer.getServer().getSecurity().);
            // ((MutableDocument) recordDoc).set(Utils.LAST_MODIFIED_DATE, LocalDateTime.now());


                mutable.setIdentity(new RID(activeDatabase, rid));
                mutable.save();
            }
        }

        return new ExecutionResponse(200, "{ \"result\" : \"successful\"}");
    } catch (Exception e) {
        e.printStackTrace();
    }
    return new ExecutionResponse(400, "{ \"result\" : \"failed\"}");
    }
}
