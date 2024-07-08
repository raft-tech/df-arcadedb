package com.arcadedb.server.http.handler;

import java.util.stream.Collectors;

import com.arcadedb.database.Database;
import com.arcadedb.serializer.json.JSONArray;
import com.arcadedb.server.http.HttpServer;
import com.arcadedb.server.security.ServerSecurityUser;

import io.undertow.server.HttpServerExchange;

public class GetTablePropertiesHandler extends AbstractServerHttpHandler {
    public GetTablePropertiesHandler(final HttpServer httpServer) {
        super(httpServer);
    }

    @Override
    protected boolean mustExecuteOnWorkerThread() {
        return true;
    }

    @Override
    public ExecutionResponse execute(final HttpServerExchange exchange, final ServerSecurityUser user) {

    var databaseName = exchange.getQueryParameters().get("database").getFirst();
    var typeName = exchange.getQueryParameters().get("table").getFirst();

    final Database database = httpServer.getServer().getDatabase(databaseName);
    var type = database.getSchema().getType(typeName);

    if (type == null) {
      return new ExecutionResponse(404, "{ \"error\" : \"Type not found\"}");
    }

    var props = type.getProperties().stream().map(p -> p.toJSON()).collect(Collectors.toList());

    return new ExecutionResponse(200, "{ \"result\" : " + new JSONArray(props) + "}");
  }
}

