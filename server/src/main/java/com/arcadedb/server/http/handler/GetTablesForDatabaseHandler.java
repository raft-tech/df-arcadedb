package com.arcadedb.server.http.handler;

import java.util.List;
import java.util.stream.Collectors;

import com.arcadedb.database.Database;
import com.arcadedb.serializer.json.JSONArray;
import com.arcadedb.server.http.HttpServer;
import com.arcadedb.server.security.ServerSecurityUser;

import io.undertow.server.HttpServerExchange;

public class GetTablesForDatabaseHandler extends AbstractServerHttpHandler {
    public GetTablesForDatabaseHandler(final HttpServer httpServer) {
        super(httpServer);
    }

    @Override
    protected boolean mustExecuteOnWorkerThread() {
        return true;
    }

    @Override
    public ExecutionResponse execute(final HttpServerExchange exchange, final ServerSecurityUser user) {

    var databaseName = exchange.getQueryParameters().get("database").getFirst();

    final Database database = httpServer.getServer().getDatabase(databaseName);
    List<String> types = database.getSchema().getTypes().stream().map(bucket -> bucket.getName()).collect(Collectors.toList());

    return new ExecutionResponse(200, "{ \"result\" : " + new JSONArray(types) + "}");
  }
}
