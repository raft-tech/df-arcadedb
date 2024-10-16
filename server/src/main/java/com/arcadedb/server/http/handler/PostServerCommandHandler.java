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
import com.arcadedb.engine.PaginatedComponentFile;
import com.arcadedb.exception.CommandExecutionException;
import com.arcadedb.network.binary.ServerIsNotTheLeaderException;
import com.arcadedb.security.AuthorizationUtils;
import com.arcadedb.serializer.json.JSONArray;
import com.arcadedb.serializer.json.JSONObject;
import com.arcadedb.server.ArcadeDBServer;
import com.arcadedb.server.ServerDatabase;
import com.arcadedb.server.ServerException;
import com.arcadedb.server.ha.HAServer;
import com.arcadedb.server.ha.Leader2ReplicaNetworkExecutor;
import com.arcadedb.server.ha.Replica2LeaderNetworkExecutor;
import com.arcadedb.server.ha.ReplicatedDatabase;
import com.arcadedb.server.ha.message.ServerShutdownRequest;
import com.arcadedb.server.http.HttpServer;
import com.arcadedb.server.security.ServerSecurityException;
import com.arcadedb.server.security.ServerSecurityUser;
import com.arcadedb.server.security.oidc.ArcadeRole;
import com.arcadedb.server.security.oidc.KeycloakClient;
import com.arcadedb.server.security.oidc.role.CRUDPermission;
import com.arcadedb.server.security.oidc.role.DatabaseAdminRole;
import com.arcadedb.server.security.oidc.role.RoleType;
import com.arcadedb.server.security.oidc.role.ServerAdminRole;

import io.undertow.server.HttpServerExchange;

import java.io.*;
import java.util.*;

public class PostServerCommandHandler extends AbstractServerHttpHandler {
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

    // List of server commands that will check their own user permissions
    var excludedTopLevelCheckComamnds = List.of(LIST_DATABASES, CREATE_DATABASE, DROP_DATABASE);

    var isExcludedCommand = excludedTopLevelCheckComamnds.stream()
            .anyMatch(excludedCommand -> command.startsWith(excludedCommand));

    // If not a command that manages its own permissions, check if user has sa role
    if (!isExcludedCommand
            && !httpServer.getServer().getSecurity().checkUserHasAnyServerAdminRole(user, List.of(ServerAdminRole.ALL))) {
      throw new ServerSecurityException(
              String.format("User '%s' is not authorized to execute server command '%s'", user.getName(), command));
    }

    final JSONObject response = createResult(user, null).put("result","ok");
    final String command_lc = command.toLowerCase(Locale.ENGLISH);
    if (command_lc.startsWith(SHUTDOWN))
      shutdownServer(command.substring(SHUTDOWN.length()).trim());
    else if (command_lc.startsWith(CREATE_DATABASE))
      createDatabase(payload, user);
    else if (command_lc.startsWith(DROP_DATABASE))
      dropDatabase(command.substring(DROP_DATABASE.length()).trim(), user);
    else if (command_lc.startsWith(CLOSE_DATABASE))
      closeDatabase(command.substring(CLOSE_DATABASE.length()).trim());
    else if (command_lc.startsWith(OPEN_DATABASE))
      openDatabase(command.substring(OPEN_DATABASE.length()).trim());
    else if (command_lc.startsWith(CREATE_USER))
      createUser(command.substring(CREATE_USER.length()).trim());
    else if (command_lc.startsWith(DROP_USER))
      dropUser(command.substring(DROP_USER.length()).trim());
    else if (command_lc.startsWith(CONNECT_CLUSTER)) {
      if (!connectCluster(command.substring(CONNECT_CLUSTER.length()).trim(), exchange)) return null;
      else if (command_lc.equals(DISCONNECT_CLUSTER)) disconnectCluster();
    }
    else if (command_lc.startsWith(SET_DATABASE_SETTING))
      setDatabaseSetting(command.substring(SET_DATABASE_SETTING.length()).trim());
    else if (command_lc.startsWith(SET_SERVER_SETTING))
      setServerSetting(command.substring(SET_SERVER_SETTING.length()).trim());
    else if (command_lc.startsWith(GET_SERVER_EVENTS))
      return getServerEvents(command);
    else if (command_lc.startsWith(ALIGN_DATABASE))
      alignDatabase(command.substring(ALIGN_DATABASE.length()).trim());
    else {
      httpServer.getServer().getServerMetrics().meter("http.server-command.invalid").hit();
      return new ExecutionResponse(400, "{ \"error\" : \"Server command not valid\"}");
    }

    return new ExecutionResponse(200, response.toString());
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

  private boolean connectCluster(final String command, final HttpServerExchange exchange) {
    final HAServer ha = getHA();

    httpServer.getServer().getServerMetrics().meter("http.connect-cluster").hit();

    final String serverAddress = command.substring("connect cluster ".length());
    return ha.connectToLeader(serverAddress, exception -> {
      exchange.setStatusCode(500);
      exchange.getResponseSender().send("{ \"error\" : \"" + exception.getMessage() + "\"}");
      return null;
    });
  }

  private boolean isNotNullOrEmpty(String toCheck) {
    return toCheck != null && !toCheck.isEmpty();
  }

  private void createDatabase(final JSONObject command, final ServerSecurityUser user) {

    // check if user has create database role, or is root
    var anyRequiredRoles = List.of(ServerAdminRole.CREATE_DATABASE, ServerAdminRole.ALL);
    if (httpServer.getServer().getSecurity().checkUserHasAnyServerAdminRole(user, anyRequiredRoles)) {
      checkServerIsLeaderIfInHA();

      // TODO check if required database info is present in the JSON options payload, including owner, classificaiton, public/private
      final String databaseName = command.getString("command").substring("create database".length()).trim();

      final String OPTIONS = "options";
      final String CLASSIFICATION = "classification";
      final String OWNER = "owner";
      final String VISIBILITY = "visibility";
      final String CLASSIFICATION_VALIDATION_ENABLED = "classificationValidationEnabled";

      if (databaseName.isEmpty())
        throw new IllegalArgumentException("Database name empty");
      // validate json payload, needs command and options object
      if (!command.has("command"))
        throw new IllegalArgumentException("Missing command");
      if (!command.has(OPTIONS))
        throw new IllegalArgumentException(String.format("Missing %s object", OPTIONS));

      var options = command.getJSONObject(OPTIONS);

      if (!options.has(CLASSIFICATION) && !isNotNullOrEmpty(options.getString(CLASSIFICATION))) {
        throw new IllegalArgumentException(String.format("Missing %s.%s", OPTIONS, CLASSIFICATION));
      }
      if (!options.has(OWNER) && !isNotNullOrEmpty(options.getString(OWNER))) {
        throw new IllegalArgumentException(String.format("Missing %s.%s", OPTIONS, OWNER));
      }
      if (options.has(VISIBILITY) && !isNotNullOrEmpty(options.getString(VISIBILITY))) {
        throw new IllegalArgumentException(String.format("Missing %s.%s", OPTIONS, VISIBILITY));
      }

      // Handle operational database metadata
      String classification = options.getString(CLASSIFICATION);

      // TODO cap acceptable classifications at the deployment level.
      // make static util method for this
      
      if (!AuthorizationUtils.isClassificationValidForDeployment(classification)) {
        throw new IllegalArgumentException(String.format("Invalid classification %s. Acceptable values are %s", classification));
      }
      
      String owner = options.has(OWNER) ? options.getString(OWNER) : null;
      boolean isPublic = options.has(VISIBILITY) ? options.getString(VISIBILITY).equalsIgnoreCase("public") : false;

      boolean isClassificationValidationEnabled = options.has(CLASSIFICATION_VALIDATION_ENABLED)
              ? options.getBoolean(CLASSIFICATION_VALIDATION_ENABLED) : true;

      final ArcadeDBServer server = httpServer.getServer();
      server.getServerMetrics().meter("http.create-database").hit();

      final DatabaseInternal db = server.createDatabase(databaseName, PaginatedComponentFile.MODE.READ_WRITE, classification, owner, isPublic, isClassificationValidationEnabled);

      if (server.getConfiguration().getValueAsBoolean(GlobalConfiguration.HA_ENABLED))
        ((ReplicatedDatabase) db).createInReplicas();

      // Create and assign new role granting all data, schema, and settings on the new
      // database to the user who created it.
      ArcadeRole dataRole = new ArcadeRole(RoleType.USER, databaseName, "*", CRUDPermission.getAll());
      ArcadeRole schemaRole = new ArcadeRole(RoleType.DATABASE_ADMIN, databaseName, DatabaseAdminRole.ALL);
      createAndAssignRoleToUser(dataRole, user.getName());
      createAndAssignRoleToUser(schemaRole, user.getName());

      if (options.has("importOntology") && options.getBoolean("importOntology")) {
        // get the ontology file from project resources
        try {
          InputStream inputStream = PostServerCommandHandler.class.getResourceAsStream("/ontology");
          String data = readFromInputStream(inputStream);

          // get JSONArray from data
          JSONArray jsonArray = new JSONArray(data);

          // iterate over jsonArray and insert into database
          for (int i = 0; i < jsonArray.length(); i++) {
            db.command("sql", jsonArray.getString(i));
          }
        } catch (Exception e) {
          throw new ServerException("Error importing ontology: " + e.getMessage());
        }
      }
    } else {
      throw new ServerSecurityException("Create database operation not allowed for user " + user.getName());
    }
  }

  private String readFromInputStream(InputStream inputStream) throws IOException {
    StringBuilder resultStringBuilder = new StringBuilder();
    try (BufferedReader br
      = new BufferedReader(new InputStreamReader(inputStream))) {
        String line;
        while ((line = br.readLine()) != null) {
            resultStringBuilder.append(line).append("\n");
        }
    }
    return resultStringBuilder.toString();
  }

  private void createAndAssignRoleToUser(ArcadeRole arcadeRole, String username) {
    String newRole = arcadeRole.getKeycloakRoleName();
    KeycloakClient.createRole(newRole);
    KeycloakClient.assignRoleToUser(newRole, username);
    httpServer.getServer().getSecurity().appendArcadeRoleToUserCache(username, newRole);
  }

  private ExecutionResponse getServerEvents(final String command) {
    final String fileName = command.substring("get server events".length()).trim();

    final ArcadeDBServer server = httpServer.getServer();
    server.getServerMetrics().meter("http.get-server-events").hit();

    final JSONArray events = fileName.isEmpty() ? server.getEventLog().getCurrentEvents()
        : server.getEventLog().getEvents(fileName);
    final JSONArray files = server.getEventLog().getFiles();

    return new ExecutionResponse(200, "{ \"result\" : { \"events\": " + events + ", \"files\": " + files + " } }");
  }

  private ExecutionResponse listDatabases(final ServerSecurityUser user) {
    // All users can list databases, no check is needed
    final ArcadeDBServer server = httpServer.getServer();
    server.getServerMetrics().meter("http.list-databases").hit();

    final Set<String> installedDatabases = new HashSet<>(server.getDatabaseNames());
    final Set<String> allowedDatabases = user.getAuthorizedDatabases();

    if (!allowedDatabases.contains("*"))
      installedDatabases.retainAll(allowedDatabases);

    return new ExecutionResponse(200, "{ \"result\" : " + new JSONArray(installedDatabases) + "}");
  }

  private void dropDatabase(final String databaseName, final ServerSecurityUser user) {
    if (databaseName.isEmpty())
      throw new IllegalArgumentException("Database name empty");

    // check if user has create database role, or is root
    var anyRequiredRoles = List.of(ServerAdminRole.DROP_DATABASE, ServerAdminRole.ALL);
    if (httpServer.getServer().getSecurity().checkUserHasAnyServerAdminRole(user, anyRequiredRoles)) {
      final Database database = httpServer.getServer().getDatabase(databaseName);

      httpServer.getServer().getServerMetrics().meter("http.drop-database").hit();

      ((DatabaseInternal) database).getEmbedded().drop();
      httpServer.getServer().removeDatabase(database.getName());

      /**
       * TODO in keycloak:
       * 1. delete all roles for the removed database
       */
    }
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

  private void createUser(final String command) {
    // final String payload = command.substring("create user ".length()).trim();
    // final JSONObject json = new JSONObject(payload);

    // if (!json.has("name"))
    // throw new IllegalArgumentException("User name is null");

    // final String userPassword = json.getString("password");
    // if (userPassword.length() < 4)
    // throw new ServerSecurityException("User password must be 5 minimum
    // characters");
    // if (userPassword.length() > 256)
    // throw new ServerSecurityException("User password cannot be longer than 256
    // characters");

    // json.put("password",
    // httpServer.getServer().getSecurity().encodePassword(userPassword));

    // httpServer.getServer().getServerMetrics().meter("http.create-user").hit();

    // httpServer.getServer().getSecurity().createUser(json);
    throw new RuntimeException("Please create new user through keycloak");
  }

  private void dropUser(final String userName) {
    if (userName.isEmpty())
      throw new IllegalArgumentException("User name was missing");

    httpServer.getServer().getServerMetrics().meter("http.drop-user").hit();

    final boolean result = httpServer.getServer().getSecurity().dropUser(userName);
    if (!result)
      throw new IllegalArgumentException("User '" + userName + "' not found on server");
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
      throw new ServerIsNotTheLeaderException("Creation of database can be executed only on the leader server",
          ha.getLeaderName());
  }

  private HAServer getHA() {
    final HAServer ha = httpServer.getServer().getHA();
    if (ha == null)
      throw new CommandExecutionException(
          "ArcadeDB is not running with High Availability module enabled. Please add this setting at startup: -Darcadedb.ha.enabled=true");
    return ha;
  }
}
