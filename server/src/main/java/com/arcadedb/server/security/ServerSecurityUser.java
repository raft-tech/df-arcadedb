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
package com.arcadedb.server.security;

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseInternal;
import com.arcadedb.log.LogManager;
import com.arcadedb.log.Logger;
import com.arcadedb.security.SecurityManager;
import com.arcadedb.security.SecurityUser;
import com.arcadedb.server.ArcadeDBServer;
import com.arcadedb.server.security.oidc.ArcadeRole;

import com.arcadedb.security.serializers.OpaPolicy;

import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;

import com.arcadedb.serializer.json.JSONArray;
import com.arcadedb.serializer.json.JSONObject;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Slf4j
public class ServerSecurityUser implements SecurityUser {
  private final ArcadeDBServer                                        server;
  private final JSONObject                                            userConfiguration;
  private final String                                                name;
  private       Set<String>                                           databasesNames;
  private       String                                                password;
  private final ConcurrentHashMap<String, ServerSecurityDatabaseUser> databaseCache = new ConcurrentHashMap<>();
  private final List<ArcadeRole> arcadeRoles;
  private final Map<String,Object> attributes;
  private final long createTime;
  private final List<OpaPolicy> policy;


  public ServerSecurityUser(final ArcadeDBServer server, final JSONObject userConfiguration) {
    this(server, userConfiguration, new ArrayList<>(), null, System.currentTimeMillis(), null);
  }

  public ServerSecurityUser(final ArcadeDBServer server, final JSONObject userConfiguration, List<ArcadeRole> arcadeRoles, Map<String, Object> attributes, long createTime, List<OpaPolicy> policy) {
    this.server = server;
    this.userConfiguration = userConfiguration;

    this.name = userConfiguration.getString("name");
    this.password = userConfiguration.has("password") ? userConfiguration.getString("password") : null;

    if (userConfiguration.has("databases")) {
      final JSONObject userDatabases = userConfiguration.getJSONObject("databases");
      databasesNames = Collections.unmodifiableSet(userDatabases.keySet());

      if (databasesNames.contains(ArcadeRole.ALL_WILDCARD)) {
        // User has some level of access to all databases
        databasesNames = Collections.unmodifiableSet(server.getDatabaseNames());
      }

    } else {
      databasesNames = Collections.emptySet();
    }

    LogManager.instance().log(this, Level.FINE, "User %s created with databases %s", null, name, databasesNames);

    this.arcadeRoles = arcadeRoles;
    this.attributes = attributes;
    this.createTime = createTime;
    this.policy = policy;
  }

  @Override
  public ServerSecurityUser addDatabase(final String databaseName, final String[] groups) {
    final Set<String> newDatabaseName = new HashSet<>(databasesNames);

    final JSONObject userDatabases = userConfiguration.getJSONObject("databases");
    final Set<Object> groupSet;
    if (userDatabases.has(databaseName)) {
      groupSet = new HashSet(userDatabases.getJSONArray(databaseName).toList());
      Collections.addAll(groupSet, groups);
    } else {
      groupSet = new HashSet(Arrays.asList(groups));
      newDatabaseName.add(databaseName);
    }

    userDatabases.put(databaseName, new JSONArray(groupSet));

    newDatabaseName.add(databaseName);
    databasesNames = Collections.unmodifiableSet(newDatabaseName);

    return this;
  }

  public ServerSecurityDatabaseUser getDatabaseUser(final Database database) {
    final String databaseName = database.getName();

    ServerSecurityDatabaseUser dbu = databaseCache.get(databaseName);
    if (dbu != null)
      return dbu;

    if (userConfiguration.has("databases")) {
      final JSONObject userDatabases = userConfiguration.getJSONObject("databases");
      if (userDatabases.has(databaseName))
        dbu = registerDatabaseUser(server, database, databaseName);
      else if (userDatabases.has(SecurityManager.ANY))
        dbu = registerDatabaseUser(server, database, SecurityManager.ANY);
    }

    if (dbu == null) {
      // USER HAS NO ACCESS TO THE DATABASE, RETURN A USER WITH NO AX
      dbu = new ServerSecurityDatabaseUser(databaseName, name, new String[0], getRelevantRoles(arcadeRoles, databaseName), attributes, policy);
      LogManager.instance().log(this, Level.INFO, "User %s has no access to database '%s'", null, name, databaseName);
    }


    final ServerSecurityDatabaseUser prev = databaseCache.putIfAbsent(databaseName, dbu);
    if (prev != null)
      // USE THE EXISTENT ONE
      dbu = prev;

    return dbu;
  }

  /**
   * Return roles that are applicable to the user conducting operations against the database.
   * @param arcadeRoles
   * @param databaseName
   * @return
   */
  private List<ArcadeRole> getRelevantRoles(List<ArcadeRole> arcadeRoles, String databaseName) {
    LogManager.instance().log(this, Level.FINE, "getRelevantRoles: {} {}", name, databaseName);
    return arcadeRoles.stream()
                .filter(role -> role.isDatabaseMatch(databaseName))
                .collect(Collectors.toList());
  }

  public JSONObject toJSON() {
    return userConfiguration;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getPassword() {
    return password;
  }

  public ServerSecurityUser setPassword(final String password) {
    this.password = password;
    userConfiguration.put("password", password);
    return this;
  }

  @Override
  public Set<String> getAuthorizedDatabases() {
    // Allow root user to access all databases for HA syncing between nodes
    if (this.name.equals("root")) {
      return server.getDatabaseNames();
    }

    return databasesNames;
  }

  @Override
  public boolean canAccessToDatabase(final String databaseName) {
    // Allow root user to access all databases for HA syncing between nodes
    if (name.equals("root")) {
      return true;
    }

    log.debug("canAccessToDatabase: {} {} {} {}", name, databaseName, databasesNames.contains(SecurityManager.ANY), databasesNames.contains(databaseName));
    return databasesNames.contains(SecurityManager.ANY) || databasesNames.contains(databaseName);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o)
      return true;
    if (!(o instanceof ServerSecurityUser))
      return false;
    final ServerSecurityUser that = (ServerSecurityUser) o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  private ServerSecurityDatabaseUser registerDatabaseUser(final ArcadeDBServer server, final Database database, final String databaseName) {
    final JSONObject userDatabases = userConfiguration.getJSONObject("databases");
    final List<Object> groupList = userDatabases.getJSONArray(databaseName).toList();
    log.debug("XX registerDatabaseUser: name: {}; database: {}; groupList: {}", name, databaseName, groupList.toString());
    
    ServerSecurityDatabaseUser dbu = new ServerSecurityDatabaseUser(databaseName, name, groupList.toArray(new String[groupList.size()]), 
            getRelevantRoles(arcadeRoles, databaseName), attributes, policy);

    final ServerSecurityDatabaseUser prev = databaseCache.putIfAbsent(databaseName, dbu);
    if (prev != null)
      // USE THE EXISTENT ONE
      dbu = prev;
    
    // Parsing the database groups configuration is unnecessary for the root user, we pass all requests
    if (database != null && !name.equals("root")) {
      if (!SecurityManager.ANY.equals(database.getName())) {
        final JSONObject databaseGroups = server.getSecurity().getDatabaseGroupsConfiguration(database.getName());
        dbu.updateDatabaseConfiguration(databaseGroups);
        log.debug("registerDatabaseUser, calling updateFileAccess {} {}", databaseName, databaseGroups);
        dbu.updateFileAccess((DatabaseInternal) database, databaseGroups);
      }
    }

    return dbu;
  }

  public long getCreateTime() {
    return createTime;
  }

  public List<OpaPolicy> getPolicy() {
    return this.policy;
  }
}
