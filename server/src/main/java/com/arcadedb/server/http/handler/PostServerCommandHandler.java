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
import com.arcadedb.engine.PaginatedFile;
import com.arcadedb.exception.CommandExecutionException;
import com.arcadedb.log.LogManager;
import com.arcadedb.network.binary.ServerIsNotTheLeaderException;
import com.arcadedb.serializer.json.JSONArray;
import com.arcadedb.server.ArcadeDBServer;
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
import com.arcadedb.server.security.oidc.role.RoleType;
import com.arcadedb.server.security.oidc.role.ServerAdminRole;
import com.google.gson.JsonParser;
import com.nimbusds.oauth2.sdk.util.StringUtils;

import io.undertow.server.HttpServerExchange;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.rmi.*;
import java.util.*;
import java.util.logging.Level;

import org.jboss.resteasy.spi.NotImplementedYetException;

@Slf4j
public class PostServerCommandHandler extends AbstractHandler {
  public PostServerCommandHandler(final HttpServer httpServer) {
    super(httpServer);
    log.info("postServerCommandHandler init");
  }

  @Override
  protected boolean mustExecuteOnWorkerThread() {
    return true;
  }

  @Override
  public ExecutionResponse execute(final HttpServerExchange exchange, final ServerSecurityUser user)
      throws IOException {
    String payloadString = parseRequestPayload(exchange);
    log.info("postServerCommandHandler execute payload {}", payloadString);
    log.info("postServerCommandHandler execute asdf {} {}", payloadString != null,
        StringUtils.isNotBlank(payloadString));

    if (payloadString != null && StringUtils.isNotBlank(payloadString)) {
      final var payload = JsonParser.parseString(payloadString).getAsJsonObject();
      log.info("postServerCommandHandler execute parsed {}", payload);

      LogManager.instance().log(this, Level.INFO, "ASDF execute: " + payload.toString());
      LogManager.instance().log(this, Level.INFO, "postServerCommandHandler execute " + payload.toString() + "adf");

      final String command = payload.has("command") ? payload.get("command").getAsString() : null;
      if (command == null)
        return new ExecutionResponse(400, "{ \"error\" : \"Server command is null\"}");

      log.info("execute: " + payload.toString());
      LogManager.instance().log(this, Level.INFO, "postServerCommandHandler execute 2");

      // List of server commands that will check their own user permissions
      var excludedTopLevelCheckComamnds = List.of("list databases", "create database", "drop database");

      var isExcludedCommand = excludedTopLevelCheckComamnds.stream()
          .anyMatch(excludedCommand -> command.startsWith(excludedCommand));

      // If not a command that manages its own permissions, check if user has sa role
      if (!isExcludedCommand) {
        if (httpServer.getServer().getSecurity().checkUserHasAnyServerAdminRole(user, List.of(ServerAdminRole.ALL))) {
          throw new ServerSecurityException(
              String.format("User '%s' is not authorized to execute server command '%s'", user.getName(), command));
        }
      }

      if (command.startsWith("shutdown"))
        shutdownServer(command);
      else if (command.startsWith("create database "))
        createDatabase(command, user);
      else if (command.equals("list databases")) {
        return listDatabases(user);
      } else if (command.startsWith("drop database "))
        dropDatabase(command, user);
      else if (command.startsWith("close database "))
        closeDatabase(command);
      else if (command.startsWith("open database "))
        openDatabase(command);
      else if (command.startsWith("create user ")) {
        // createUser(command);
        return new ExecutionResponse(400, "{ \"error\" : \"Please create new users through keycloak.\"}");
      } else if (command.startsWith("drop user "))
        dropUser(command);
      else if (command.startsWith("connect cluster ")) {
        if (!connectCluster(command, exchange))
          return null;
      } else if (command.equals("disconnect cluster"))
        disconnectCluster();
      else if (command.startsWith("set database setting "))
        setDatabaseSetting(command);
      else if (command.startsWith("set server setting "))
        setServerSetting(command);
      else if (command.startsWith("get server events"))
        return getServerEvents(command);
      else if (command.startsWith("align database "))
        alignDatabase(command);
      else {
        httpServer.getServer().getServerMetrics().meter("http.server-command.invalid").hit();
        return new ExecutionResponse(400, "{ \"error\" : \"Server command not valid\"}");
      }
    }

    return new ExecutionResponse(200, "{ \"result\" : \"ok\"}");
  }

  private void setDatabaseSetting(final String command) throws IOException {
    final String pair = command.substring("set database setting ".length());
    final String[] dbKeyValue = pair.split(" ");
    if (dbKeyValue.length != 3)
      throw new IllegalArgumentException("Expected <database> <key> <value>");

    final DatabaseInternal database = (DatabaseInternal) httpServer.getServer().getDatabase(dbKeyValue[0]);
    database.getConfiguration().setValue(dbKeyValue[1], dbKeyValue[2]);
    database.saveConfiguration();
  }

  private void setServerSetting(final String command) {
    // TODO check if user has sa role, or is root

    final String pair = command.substring("set server setting ".length());
    final String[] keyValue = pair.split(" ");
    if (keyValue.length != 2)
      throw new IllegalArgumentException("Expected <key> <value>");

    httpServer.getServer().getConfiguration().setValue(keyValue[0], keyValue[1]);
  }

  private void shutdownServer(final String command) throws IOException {
    httpServer.getServer().getServerMetrics().meter("http.server-shutdown").hit();

    if (command.equals("shutdown")) {
      // SHUTDOWN CURRENT SERVER
      httpServer.getServer().stop();
    } else if (command.startsWith("shutdown ")) {
      final String serverName = command.substring("shutdown ".length()).trim();
      final HAServer ha = getHA();
      final Leader2ReplicaNetworkExecutor replica = ha.getReplica(serverName);
      if (replica == null)
        throw new ServerException("Cannot contact server '" + serverName + "' from the current server");

      final Binary buffer = new Binary();
      ha.getMessageFactory().serializeCommand(new ServerShutdownRequest(), buffer, -1);
      replica.sendMessage(buffer);
    }
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

  private void createDatabase(final String command, final ServerSecurityUser user) {
    final String databaseName = command.substring("create database ".length()).trim();
    if (databaseName.isEmpty())
      throw new IllegalArgumentException("Database name empty");

    // check if user has create database role, or is root
    var anyRequiredRoles = List.of(ServerAdminRole.CREATE_DATABASE, ServerAdminRole.ALL);
    if (httpServer.getServer().getSecurity().checkUserHasAnyServerAdminRole(user, anyRequiredRoles)) {
      checkServerIsLeaderIfInHA();

      final ArcadeDBServer server = httpServer.getServer();
      server.getServerMetrics().meter("http.create-database").hit();

      final DatabaseInternal db = server.createDatabase(databaseName, PaginatedFile.MODE.READ_WRITE);

      if (server.getConfiguration().getValueAsBoolean(GlobalConfiguration.HA_ENABLED))
        ((ReplicatedDatabase) db).createInReplicas();

      ArcadeRole arcadeRole = new ArcadeRole(RoleType.USER, databaseName, "*", CRUDPermission.getAll());
      String newRole = arcadeRole.getKeycloakRoleName();
      KeycloakClient.createRole(newRole);
      KeycloakClient.assignRoleToUser(newRole, user.getName());

      server.getSecurity().appendArcadeRoleToUserCache(user.getName(), newRole);
      /**
       * TODO in keycloak:
       * 1. create new full permission role for the database
       * 2. assign new role to user who created the database
       * 
       * 3. add arcade role to server security user role map, to grant immediate
       * access to database
       */
    } else {
      throw new ServerSecurityException("Create database operation not allowed for user " + user.getName());
      // log.warn("Create database operation not allowed for user {}",
      // user.getName());
    }
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
    LogManager.instance().log(this, Level.INFO, "listDatabases");
    log.info("listDatabases");
    final ArcadeDBServer server = httpServer.getServer();
    server.getServerMetrics().meter("http.list-databases").hit();

    final Set<String> installedDatabases = new HashSet<>(server.getDatabaseNames());
    final Set<String> allowedDatabases = user.getAuthorizedDatabases();

    log.info("listDatabases installed: {}, allowed: {}", installedDatabases, allowedDatabases);
    LogManager.instance().log(this, Level.INFO,
        "list databases installed " + installedDatabases + " " + allowedDatabases);

    if (!allowedDatabases.contains("*"))
      installedDatabases.retainAll(allowedDatabases);

    return new ExecutionResponse(200, "{ \"result\" : " + new JSONArray(installedDatabases) + "}");
  }

  private void alignDatabase(final String command) {
    final String databaseName = command.substring("align database ".length()).trim();
    if (databaseName.isEmpty())
      throw new IllegalArgumentException("Database name empty");

    final Database database = httpServer.getServer().getDatabase(databaseName);

    httpServer.getServer().getServerMetrics().meter("http.align-database").hit();

    database.command("sql", "align database");
  }

  private void dropDatabase(final String command, final ServerSecurityUser user) {
    final String databaseName = command.substring("drop database ".length()).trim();
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

  private void closeDatabase(final String command) {
    final String databaseName = command.substring("close database ".length()).trim();
    if (databaseName.isEmpty())
      throw new IllegalArgumentException("Database name empty");

    final Database database = httpServer.getServer().getDatabase(databaseName);
    ((DatabaseInternal) database).getEmbedded().close();

    httpServer.getServer().getServerMetrics().meter("http.close-database").hit();
    httpServer.getServer().removeDatabase(database.getName());
  }

  private void openDatabase(final String command) {
    final String databaseName = command.substring("open database ".length()).trim();
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
    throw new NotImplementedYetException("Please create new user through keycloak");
  }

  private void dropUser(final String command) {
    final String userName = command.substring("drop user ".length()).trim();
    if (userName.isEmpty())
      throw new IllegalArgumentException("User name was missing");

    httpServer.getServer().getServerMetrics().meter("http.drop-user").hit();

    final boolean result = httpServer.getServer().getSecurity().dropUser(userName);
    if (!result)
      throw new IllegalArgumentException("User '" + userName + "' not found on server");
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
