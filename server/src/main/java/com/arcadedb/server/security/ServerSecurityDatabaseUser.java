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

import com.arcadedb.database.DatabaseInternal;
import com.arcadedb.engine.PaginatedFile;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.security.SecurityDatabaseUser;
import com.arcadedb.security.SecurityManager;
import com.arcadedb.serializer.json.JSONArray;
import com.arcadedb.serializer.json.JSONObject;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ServerSecurityDatabaseUser implements SecurityDatabaseUser {
  private static final JSONObject NO_ACCESS_GROUP = new JSONObject().put("types",
      new JSONObject().put(SecurityManager.ANY, new JSONObject().put("access", new JSONArray())));
  private final String databaseName;
  private final String userName;
  private String[] groups;
  private boolean[][] fileAccessMap = null;
  private long resultSetLimit = -1;
  private long readTimeout = -1;
  private final boolean[] databaseAccessMap = new boolean[DATABASE_ACCESS.values().length];

  public ServerSecurityDatabaseUser(final String databaseName, final String userName, final String[] groups) {
    this.databaseName = databaseName;
    this.userName = userName;
    this.groups = groups;
  }

  public String[] getGroups() {
    return groups;
  }

  public void addGroup(final String group) {
    final Set<String> set = new HashSet<>(List.of(groups));
    if (set.add(group))
      this.groups = set.toArray(new String[set.size()]);
  }

  public String getName() {
    return userName;
  }

  @Override
  public long getResultSetLimit() {
    return resultSetLimit;
  }

  @Override
  public long getReadTimeout() {
    return readTimeout;
  }

  public String getDatabaseName() {
    return databaseName;
  }

  @Override
  public boolean requestAccessOnDatabase(final DATABASE_ACCESS access) {
    // log.info("requestAccessOnDatabase: access: {}, decision: {}", access,
    // databaseAccessMap[access.ordinal()]);
    return databaseAccessMap[access.ordinal()];
  }

  @Override
  public boolean requestAccessOnFile(final int fileId, final ACCESS access) {

    final boolean[] permissions = fileAccessMap[fileId];
    log.info("requestAccessOnFile: database: {}; fileId: {}, access: {}, permissions: {}", databaseName, fileId, access,
        permissions);
    log.info("requestAccessOnFile decision {} {}", permissions != null,
    permissions!= null ? permissions[access.ordinal()]:"false");
    // log.info("requestAccessOnFile decision groups {}",
    // List.of(groups).toString());
    // log.info("santiy check {}", false && false);
    return permissions != null && permissions[access.ordinal()];
  }

  public void updateDatabaseConfiguration(final JSONObject configuredGroups) {
    // RESET THE ARRAY
    for (int i = 0; i < DATABASE_ACCESS.values().length; i++)
      databaseAccessMap[i] = false;

    if (configuredGroups == null)
      return;

    JSONArray access = null;
    for (final String groupName : groups) {
      if (!configuredGroups.has(groupName))
        // GROUP NOT DEFINED
        continue;

      final JSONObject group = configuredGroups.getJSONObject(groupName);
      if (group.has("access"))
        access = group.getJSONArray("access");

      if (group.has("resultSetLimit")) {
        final long value = group.getLong("resultSetLimit");
        if (value > -1 && (resultSetLimit == -1 || value < resultSetLimit))
          // SET THE MOST RESTRICTIVE TIMEOUT IN CASE OF MULTIPLE GROUP SETTINGS
          resultSetLimit = value;
      }

      if (group.has("readTimeout")) {
        final long value = group.getLong("readTimeout");
        if (value > -1 && (readTimeout == -1 || value < readTimeout))
          // SET THE MOST RESTRICTIVE TIMEOUT IN CASE OF MULTIPLE GROUP SETTINGS
          readTimeout = value;
      }
    }

    log.info("updateDatabaseConfiguration: access: {}", access);

    if (access == null && configuredGroups.has(SecurityManager.ANY)) {
      log.info("updateDatabaseConfiguration: shouldn't be here");
      // NOT FOUND, GET DEFAULT GROUP ACCESS
      final JSONObject defaultGroup = configuredGroups.getJSONObject(SecurityManager.ANY);
      if (defaultGroup.has("access"))
        access = defaultGroup.getJSONArray("access");

      if (defaultGroup.has("resultSetLimit")) {
        final long value = defaultGroup.getLong("resultSetLimit");
        if (value > -1 && (resultSetLimit == -1 || value < resultSetLimit))
          // SET THE MOST RESTRICTIVE TIMEOUT IN CASE OF MULTIPLE GROUP SETTINGS
          resultSetLimit = value;
      }

      if (defaultGroup.has("readTimeout")) {
        final long value = defaultGroup.getLong("readTimeout");
        if (value > -1 && (readTimeout == -1 || value < readTimeout))
          // SET THE MOST RESTRICTIVE TIMEOUT IN CASE OF MULTIPLE GROUP SETTINGS
          readTimeout = value;
      }
    }

    if (access != null) {
      // UPDATE THE ARRAY WITH LATEST CONFIGURATION
      for (int i = 0; i < access.length(); i++)
        databaseAccessMap[DATABASE_ACCESS.getByName(access.getString(i)).ordinal()] = true;
    }
  }

  public synchronized void updateFileAccess(final DatabaseInternal database, final JSONObject configuredGroups) {

    log.info("updateFileAccess: database {} configuredGroups: {}", database.getName(), configuredGroups.toString());

    if (configuredGroups == null)
      return;

    final List<PaginatedFile> files = database.getFileManager().getFiles();
    for (int i = 0; i < files.size(); ++i) {
      log.info("111 updateFileAccess fileId: {}; fileName: {}; cn: {}", files.get(i).getFileId(),
          files.get(i).getFileName(), files.get(i).getComponentName());
    }

    fileAccessMap = new boolean[files.size()][];

    final JSONObject defaultGroup = configuredGroups.has(SecurityManager.ANY)
        ? configuredGroups.getJSONObject(SecurityManager.ANY)
        : NO_ACCESS_GROUP;

    final JSONObject defaultType = defaultGroup.getJSONObject("types").getJSONObject(SecurityManager.ANY);

    // log.info("type count {}", database.getSchema().getTypes().size());
    database.getSchema().getTypes().stream().forEach(t -> log.info("type {}", t.getName()));

    for (int i = 0; i < files.size(); ++i) {

      // log.info("updateFileAccess fileId {}", i, );
      final DocumentType type = database.getSchema().getTypeByBucketId(i);
      if (type == null)
        continue;

      // database.getSchema().get

      final String typeName = type.getName();
      // log.info("updateFileAccess fileName {} typeName {}",
      // files.get(i).getFileName(), typeName);

      for (final String groupName : groups) {
        // log.info("updateFileAccess groupName {}", groupName);
        if (!configuredGroups.has(groupName))
          // GROUP NOT DEFINED
          continue;

        final JSONObject group = configuredGroups.getJSONObject(groupName);
        // log.info("parsing group {}", group.toString());

        if (!group.has("types"))
          continue;

        // log.info("updateFileAccess group {} has type {}", groupName, typeName);

        final JSONObject types = group.getJSONObject("types");

        JSONObject groupType = types.has(typeName) ? types.getJSONObject(typeName) : null;
        // log.info("updateFileAccess group {} has groupType {}", groupName, groupType);
        if (groupType == null)
          // GET DEFAULT TYPE FOR THE GROUP IF ANY
          groupType = types.has(SecurityManager.ANY) ? types.getJSONObject(SecurityManager.ANY) : null;

        // log.info("updateFileAccess null 2nd chance group {} has groupType {}",
        // groupName, groupType);

        if (groupType == null)
          continue;

        if (fileAccessMap[i] == null)
          // FIRST DEFINITION ENCOUNTERED: START FROM ALL REVOKED
          fileAccessMap[i] = new boolean[] { false, false, false, false };

        // APPLY THE FOUND TYPE FROM THE FOUND GROUP
        updateAccessArray(fileAccessMap[i], groupType.getJSONArray("access"));

        // if (fileAccessMap[i] == null) {
        //   // NO GROUP+TYPE FOUND, APPLY SETTINGS FROM DEFAULT GROUP/TYPE
        //   fileAccessMap[i] = new boolean[] { false, false, false, false };
        //   final JSONObject t;
        //   if (defaultGroup.has(typeName)) {
        //     // APPLY THE FOUND TYPE FROM DEFAULT GROUP
        //     t = defaultGroup.getJSONObject(typeName);
        //   } else
        //     // APPLY DEFAULT TYPE FROM DEFAULT GROUP
        //     t = defaultType;
        //   updateAccessArray(fileAccessMap[i], t.getJSONArray("access"));
        // }
      }

      // loop through fileAccessMap, and for any files with name ...out_edge, set the
      // access to the same as the file with no suffix
      // if (fileAccessMap[i] == null) {

      // }

      // schema.getFileById(fileId).getName()

      // if (fileAccessMap[i] == null) {
      // // NO GROUP+TYPE FOUND, APPLY SETTINGS FROM DEFAULT GROUP/TYPE
      // fileAccessMap[i] = new boolean[] { false, false, false, false };

      // final JSONObject t;
      // if (defaultGroup.has(typeName)) {
      // // APPLY THE FOUND TYPE FROM DEFAULT GROUP
      // t = defaultGroup.getJSONObject(typeName);
      // } else
      // // APPLY DEFAULT TYPE FROM DEFAULT GROUP
      // t = defaultType;

      // updateAccessArray(fileAccessMap[i], t.getJSONArray("access"));
      // }
    }

    var typeSuffixesToAdd = List.of("_out_edges", "_in_edges");

    for (String typeSuffixToAdd : typeSuffixesToAdd) {
      for (int i = 0; i < files.size(); ++i) {
        // log.info("222 updateFileAccess fileId: {}; fileName: {}; cn: {}", files.get(i).getFileId(),
        //     files.get(i).getFileName(), files.get(i).getComponentName());

        String fileName = files.get(i).getFileName();
   //     log.info("221 updateFileAccess fileName {}", fileName);
        if (fileName.split("\\.")[0].endsWith(typeSuffixToAdd)) {
          fileName = fileName.split("\\.")[0].substring(0, (fileName.split("\\.")[0].length() - typeSuffixToAdd.length()));
   //       log.info("222 updateFileAccess fileName {}", fileName);
          for (int j = 0; j < files.size(); ++j) {
            // files.get(j).get
            // if filename matches regex for "name"_anynumber_"out_edges"
            // Pattern pattern = Pattern.compile("_(\\d+)_out_edges");
            // Matcher matcher = pattern.matcher(files.get(j).getFileName());
            // boolean matchFound = matcher.find();
            // if (matchFound) {
            // fileAccessMap[i] = fileAccessMap[j];
            // log.info("updateFileAccess fileName {} found match {}", fileName, j);
            // break;
            // }

            if (files.get(j).getFileName().split("\\.")[0].equals(fileName)) {
              fileAccessMap[i] = fileAccessMap[j];
      //        log.info("223 updateFileAccess fileName {} found match {}", fileName, j);
              break;
            }
          }
        }
      }
    }

    var extraTypesToAdd = List.of("HAS_CATEGORY", "INPUT");

    for(String extraType : extraTypesToAdd) {
      // loop through all files, and if file name matches extra type, set access to null
      for (int i = 0; i < files.size(); ++i) {
        log.info("331 updateFileAccess fileId: {}; fileName: {}; cn: {}", files.get(i).getFileId(),
            files.get(i).getFileName(), files.get(i).getComponentName());

        String fileName = files.get(i).getFileName();
        log.info("332 updateFileAccess fileName {}", fileName);
        if (fileName.split("\\.")[0].startsWith(extraType)) {
          fileAccessMap[i] = new boolean[] {true, true, true, true};
          log.info("333 updateFileAccess fileName {} found match {}", fileName, i);
        }
      }
    }
  }

  public static boolean[] updateAccessArray(final boolean[] array, final JSONArray access) {
    for (int i = 0; i < access.length(); i++) {
      switch (access.getString(i)) {
        case "createRecord":
          array[0] = true;
          break;
        case "readRecord":
          array[1] = true;
          break;
        case "updateRecord":
          array[2] = true;
          break;
        case "deleteRecord":
          array[3] = true;
          break;
      }
    }
    log.info("updateAccessArray: accessObj: {}; array: {}", access, array);
    return array;
  }
}
