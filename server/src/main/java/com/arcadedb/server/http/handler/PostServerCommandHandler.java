/*
 * Copyright © 2021-present Arcade Data Ltd (info@arcadedata.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-FileCopyrightText: 2021-present Arcade Data Ltd (info@arcadedata.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package com.arcadedb.server.http.handler;

import com.arcadedb.GlobalConfiguration;
import com.arcadedb.database.Binary;
import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseInternal;
import com.arcadedb.engine.ComponentFile;
import com.arcadedb.exception.CommandExecutionException;
import com.arcadedb.network.binary.ServerIsNotTheLeaderException;
import com.arcadedb.serializer.json.JSONArray;
import com.arcadedb.serializer.json.JSONObject;
import com.arcadedb.server.ArcadeDBServer;
import com.arcadedb.server.ServerDatabase;
import com.arcadedb.server.ha.HAServer;
import com.arcadedb.server.ha.Leader2ReplicaNetworkExecutor;
import com.arcadedb.server.ha.Replica2LeaderNetworkExecutor;
import com.arcadedb.server.ha.ReplicatedDatabase;
import com.arcadedb.server.ha.message.ServerShutdownRequest;
import com.arcadedb.server.http.HttpServer;
import com.arcadedb.server.security.ServerSecurityException;
import com.arcadedb.server.security.ServerSecurityUser;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;

import java.io.*;
import java.rmi.*;
import java.util.*;

public class PostServerCommandHandler extends AbstractHandler {
  public PostServerCommandHandler(final HttpServer httpServer) {
    super(httpServer);
  }

  @Override
  protected boolean mustExecuteOnWorkerThread() {
    return true;
  }

  @Override
  public ExecutionResponse execute(final HttpServerExchange exchange, final ServerSecurityUser user) throws IOException {

    final String LIST_DATABASES = "list databases";
    final String SHUTDOWN = "shutdown";
    final String CREATE_DATABASE = "create database";
    final String DROP_DATABASE = "drop database";
    final String CLOSE_DATABASE = "close database";
    final String OPEN_DATABASE = "open database";
    final String CREATE_USER = "create user";
    final String DROP_USER = "drop user";
    final String CONNECT_CLUSTER = "connect cluster";
    final String DISCONNECT_CLUSTER = "disconnect cluster";
    final String SET_DATABASE_SETTING = "set database setting";
    final String SET_SERVER_SETTING = "set server setting";
    final String GET_SERVER_EVENTS = "get server events";
    final String ALIGN_DATABASE = "align database";

    final JSONObject payload = new JSONObject(parseRequestPayload(exchange));

    final String command = payload.has("command") ? payload.getString("command") : null;
    if (command == null)
      return new ExecutionResponse(400, "{ \"error\" : \"Server command is null\"}");

    final JSONObject response = createResult(user, null).put("result","ok");

    final String command_lc = command.toLowerCase();

    if (command_lc.equals(LIST_DATABASES))
      return listDatabases(user);
    else
      checkRootUser(user);

    if (command_lc.startsWith(SHUTDOWN))
      shutdownServer(command.substring(SHUTDOWN.length()).trim());
    else if (command_lc.startsWith(CREATE_DATABASE))
      createDatabase(command.substring(CREATE_DATABASE.length()).trim());
    else if (command_lc.startsWith(DROP_DATABASE))
      dropDatabase(command.substring(DROP_DATABASE.length()).trim());
    else if (command_lc.startsWith(CLOSE_DATABASE))
      closeDatabase(command.substring(CLOSE_DATABASE.length()).trim());
    else if (command_lc.startsWith(OPEN_DATABASE))
      openDatabase(command.substring(OPEN_DATABASE.length()).trim());
    else if (command_lc.startsWith(CREATE_USER))
      createUser(command.substring(CREATE_USER.length()).trim());
    else if (command_lc.startsWith(DROP_USER))
      dropUser(command.substring(DROP_USER.length()).trim());
    else if (command_lc.startsWith(CONNECT_CLUSTER)) {
      if (!connectCluster(command.substring(CONNECT_CLUSTER.length()).trim(), exchange))
        return null;
    } else if (command_lc.equals(DISCONNECT_CLUSTER))
      disconnectCluster();
    else if (command_lc.startsWith(SET_DATABASE_SETTING))
      setDatabaseSetting(command.substring(SET_DATABASE_SETTING.length()).trim());
    else if (command_lc.startsWith(SET_SERVER_SETTING))
      setServerSetting(command.substring(SET_SERVER_SETTING.length()).trim());
    else if (command_lc.startsWith(GET_SERVER_EVENTS))
      response.put("result",getServerEvents(command.substring(GET_SERVER_EVENTS.length()).trim()));
    else if (command_lc.startsWith(ALIGN_DATABASE))
      alignDatabase(command.substring(ALIGN_DATABASE.length()).trim());
    else {
      httpServer.getServer().getServerMetrics().meter("http.server-command.invalid").hit();
      return new ExecutionResponse(400, "{ \"error\" : \"Server command not valid\"}");
    }

    return new ExecutionResponse(200, response.toString());
  }

  private ExecutionResponse listDatabases(final ServerSecurityUser user) {
    final ArcadeDBServer server = httpServer.getServer();
    server.getServerMetrics().meter("http.list-databases").hit();

    final Set<String> installedDatabases = new HashSet<>(server.getDatabaseNames());
    final Set<String> allowedDatabases = user.getAuthorizedDatabases();

    if (!allowedDatabases.contains("*"))
      installedDatabases.retainAll(allowedDatabases);

    final JSONObject response = createResult(user, null).put("result",new JSONArray(installedDatabases));

    return new ExecutionResponse(200,response.toString());
  }

  private void shutdownServer(final String serverName) throws IOException {
    httpServer.getServer().getServerMetrics().meter("http.server-shutdown").hit();

    if (serverName.isEmpty()) {
      // SHUTDOWN CURRENT SERVER
      httpServer.getServer().stop();
    } else {
      final HAServer ha = getHA();
      final Leader2ReplicaNetworkExecutor replica = ha.getReplica(serverName);
      if (replica == null)
        throw new ServerException("Cannot contact server '" + serverName + "' from the current server");

      final Binary buffer = new Binary();
      ha.getMessageFactory().serializeCommand(new ServerShutdownRequest(), buffer, -1);
      replica.sendMessage(buffer);
    }
  }

  private void createDatabase(final String databaseName) {
    if (databaseName.isEmpty())
      throw new IllegalArgumentException("Database name empty");

    checkServerIsLeaderIfInHA();

    final ArcadeDBServer server = httpServer.getServer();
    server.getServerMetrics().meter("http.create-database").hit();

    final ServerDatabase db = server.createDatabase(databaseName, ComponentFile.MODE.READ_WRITE);

    if (server.getConfiguration().getValueAsBoolean(GlobalConfiguration.HA_ENABLED)) {
      final ReplicatedDatabase replicatedDatabase = (ReplicatedDatabase) db.getEmbedded();
      replicatedDatabase.createInReplicas();
    }
  }

  private void dropDatabase(final String databaseName) {
    if (databaseName.isEmpty())
      throw new IllegalArgumentException("Database name empty");

    final ServerDatabase database = httpServer.getServer().getDatabase(databaseName);

    httpServer.getServer().getServerMetrics().meter("http.drop-database").hit();

    database.getEmbedded().drop();
    httpServer.getServer().removeDatabase(database.getName());
  }

  private void closeDatabase(final String databaseName) {
    if (databaseName.isEmpty())
      throw new IllegalArgumentException("Database name empty");

    final ServerDatabase database = httpServer.getServer().getDatabase(databaseName);
    database.getEmbedded().close();

    httpServer.getServer().getServerMetrics().meter("http.close-database").hit();
    httpServer.getServer().removeDatabase(database.getName());
  }

  private void openDatabase(final String databaseName) {
    if (databaseName.isEmpty())
      throw new IllegalArgumentException("Database name empty");

    httpServer.getServer().getDatabase(databaseName);
    httpServer.getServer().getServerMetrics().meter("http.open-database").hit();
  }

  private void createUser(final String payload) {
    final JSONObject json = new JSONObject(payload);

    if (!json.has("name"))
      throw new IllegalArgumentException("User name is null");

    final String userPassword = json.getString("password");
    if (userPassword.length() < 4)
      throw new ServerSecurityException("User password must be 5 minimum characters");
    if (userPassword.length() > 256)
      throw new ServerSecurityException("User password cannot be longer than 256 characters");

    json.put("password", httpServer.getServer().getSecurity().encodePassword(userPassword));

    httpServer.getServer().getServerMetrics().meter("http.create-user").hit();

    httpServer.getServer().getSecurity().createUser(json);
  }

  private void dropUser(final String userName) {
    if (userName.isEmpty())
      throw new IllegalArgumentException("User name was missing");

    httpServer.getServer().getServerMetrics().meter("http.drop-user").hit();

    final boolean result = httpServer.getServer().getSecurity().dropUser(userName);
    if (!result)
      throw new IllegalArgumentException("User '" + userName + "' not found on server");
  }

  private boolean connectCluster(final String serverAddress, final HttpServerExchange exchange) {
    final HAServer ha = getHA();

    httpServer.getServer().getServerMetrics().meter("http.connect-cluster").hit();

    return ha.connectToLeader(serverAddress, exception -> {
      exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
      exchange.getResponseSender().send("{ \"error\" : \"" + exception.getMessage() + "\"}");
      return null;
    });
  }

  private void disconnectCluster() {
    httpServer.getServer().getServerMetrics().meter("http.server-disconnect").hit();
    final HAServer ha = getHA();

    final Replica2LeaderNetworkExecutor leader = ha.getLeader();
    if (leader != null)
      leader.close();
    else
      ha.disconnectAllReplicas();
  }

  private void setDatabaseSetting(final String pair) throws IOException {
    final String[] dbKeyValue = pair.split(" ");
    if (dbKeyValue.length != 3)
      throw new IllegalArgumentException("Expected <database> <key> <value>");

    final DatabaseInternal database = (DatabaseInternal) httpServer.getServer().getDatabase(dbKeyValue[0]);
    database.getConfiguration().setValue(dbKeyValue[1], dbKeyValue[2]);
    database.saveConfiguration();
  }

  private void setServerSetting(final String pair) {
    final String[] keyValue = pair.split(" ");
    if (keyValue.length != 2)
      throw new IllegalArgumentException("Expected <key> <value>");

    httpServer.getServer().getConfiguration().setValue(keyValue[0], keyValue[1]);
  }

  private String getServerEvents(final String fileName) {
    final ArcadeDBServer server = httpServer.getServer();
    server.getServerMetrics().meter("http.get-server-events").hit();

    final JSONArray events = fileName.isEmpty() ? server.getEventLog().getCurrentEvents() : server.getEventLog().getEvents(fileName);
    final JSONArray files = server.getEventLog().getFiles();

    return "{ \"events\": " + events + ", \"files\": " + files + " }";
  }

  private void alignDatabase(final String databaseName) {
    if (databaseName.isEmpty())
      throw new IllegalArgumentException("Database name empty");

    final Database database = httpServer.getServer().getDatabase(databaseName);

    httpServer.getServer().getServerMetrics().meter("http.align-database").hit();

    database.command("sql", "align database");
  }

  private void checkServerIsLeaderIfInHA() {
    final HAServer ha = httpServer.getServer().getHA();
    if (ha != null && !ha.isLeader())
      // NOT THE LEADER
      throw new ServerIsNotTheLeaderException("Creation of database can be executed only on the leader server", ha.getLeaderName());
  }

  private HAServer getHA() {
    final HAServer ha = httpServer.getServer().getHA();
    if (ha == null)
      throw new CommandExecutionException(
          "ArcadeDB is not running with High Availability module enabled. Please add this setting at startup: -Darcadedb.ha.enabled=true");
    return ha;
  }
}
