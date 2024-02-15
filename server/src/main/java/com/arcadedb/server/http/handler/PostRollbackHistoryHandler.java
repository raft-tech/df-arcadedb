package com.arcadedb.server.http.handler;

import java.util.Deque;

import com.arcadedb.server.http.HttpServer;
import com.arcadedb.server.security.ServerSecurityUser;

import io.undertow.server.HttpServerExchange;

public class PostRollbackHistoryHandler extends AbstractHandler {
    public PostRollbackHistoryHandler(final HttpServer httpServer) {
      super(httpServer);
    }
  
    @Override
    protected ExecutionResponse execute(HttpServerExchange exchange, ServerSecurityUser user) throws Exception {
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

        final Deque<String> eventIdParam = exchange.getQueryParameters().get("eventId");
        String eventId = eventIdParam.isEmpty() ? null : eventIdParam.getFirst().trim();
        if (eventId != null && eventId.isEmpty()) {
            eventId = null;
        }

        if (eventId == null) {
            return new ExecutionResponse(400, "{ \"error\" : \"EventId parameter is null\"}");
        }

        // get specified event payload from lakehouse, if it exists

        // get document

        // apply payload to document

        // save

        return new ExecutionResponse(200, "{ \"result\" : \"successful\"}");
    }
    
}
