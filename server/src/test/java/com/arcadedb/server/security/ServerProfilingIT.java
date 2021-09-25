/*
 * Copyright 2021 Arcade Data Ltd
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.arcadedb.server.security;

import com.arcadedb.GlobalConfiguration;
import com.arcadedb.database.DatabaseContext;
import com.arcadedb.database.DatabaseInternal;
import com.arcadedb.database.Document;
import com.arcadedb.database.RID;
import com.arcadedb.graph.MutableVertex;
import com.arcadedb.graph.Vertex;
import com.arcadedb.index.lsm.LSMTreeIndexAbstract;
import com.arcadedb.schema.Schema;
import com.arcadedb.security.SecurityDatabaseUser;
import com.arcadedb.server.ArcadeDBServer;
import com.arcadedb.utility.CallableNoReturn;
import com.arcadedb.utility.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.concurrent.atomic.*;

public class ServerProfilingIT {
  private static       ArcadeDBServer SERVER;
  private static       ServerSecurity SECURITY;
  private final static String         DATABASE_NAME = "ServerProfilingIT";

  @Test
  void userDefaultAccessCannotAccessDatabase() throws Throwable {
    SECURITY.createUser(new JSONObject().put("name", "elon").put("password", SECURITY.encodePassword("musk")));

    try (DatabaseInternal database = (DatabaseInternal) SERVER.getDatabase(DATABASE_NAME)) {
      checkElonUser(setCurrentUser("elon", database));

      createSchemaNotAllowed(database);

      // SWITCH TO ROOT TO CREATE SOME TYPES FOR FURTHER TESTS
      setCurrentUser("root", database);

      createSchema(database);

      final RID validRID = createSomeRecords(database, true);

      // SWITCH BACK TO ELON
      checkElonUser(setCurrentUser("elon", database));

      expectedSecurityException(() -> database.newVertex("Vertex1").save());
      expectedSecurityException(() -> database.newDocument("Document1").save());
      expectedSecurityException(() -> database.iterateType("Document1", true));
      expectedSecurityException(() -> database.lookupByRID(validRID, true));

      // SWITCH TO ROOT TO DROP THE SCHEMA
      setCurrentUser("root", database);
      dropSchema(database);

    } finally {
      SECURITY.dropUser("elon");
    }
  }

  @Test
  void notRootAdminAccess() {
    SECURITY.createUser(new JSONObject().put("name", "elon").put("password", SECURITY.encodePassword("musk"))
        .put("databases", new JSONObject().put(DATABASE_NAME, new JSONArray(new String[] { "admin" }))));

    try (DatabaseInternal database = (DatabaseInternal) SERVER.getDatabase(DATABASE_NAME)) {
      checkElonUser(setCurrentUser("elon", database));

      createSchema(database);

      final RID validRID = createSomeRecords(database, true);

      database.newVertex("Vertex1").save();
      database.newDocument("Document1").save();
      database.iterateType("Document1", true);
      database.lookupByRID(validRID, true);

      dropSchema(database);
    } finally {
      SECURITY.dropUser("elon");
    }
  }

  @Test
  void testMultipleGroupsAnyType() {
    SECURITY.createUser(new JSONObject().put("name", "elon").put("password", SECURITY.encodePassword("musk"))
        .put("databases", new JSONObject().put(DATABASE_NAME, new JSONArray(new String[] { "creator", "reader", "updater", "deleter" }))));

    try (DatabaseInternal database = (DatabaseInternal) SERVER.getDatabase(DATABASE_NAME)) {
      setCurrentUser("root", database);
      createSchema(database);

      checkElonUser(setCurrentUser("elon", database));

      final RID validRID = createSomeRecords(database, true);

      database.newVertex("Vertex1").save();
      database.newDocument("Document1").save();
      database.iterateType("Document1", true);
      database.lookupByRID(validRID, true);

      setCurrentUser("root", database);
      dropSchema(database);
    } finally {
      SECURITY.dropUser("elon");
    }
  }

  @Test
  void testMultipleGroupsSpecificType() throws Throwable {
    SECURITY.createUser(new JSONObject().put("name", "elon").put("password", SECURITY.encodePassword("musk")).put("databases",
        new JSONObject().put(DATABASE_NAME,
            new JSONArray(new String[] { "creatorOfDocuments", "readerOfDocuments", "updaterOfDocuments", "deleterOfDocuments" }))));

    try (DatabaseInternal database = (DatabaseInternal) SERVER.getDatabase(DATABASE_NAME)) {
      setCurrentUser("root", database);
      createSchema(database);

      checkElonUser(setCurrentUser("elon", database));

      expectedSecurityException(() -> database.newVertex("Vertex1").save());
      database.newDocument("Document1").save();
      database.iterateType("Document1", true);
      database.transaction((tx) -> {
        database.iterateType("Document1", true).next().asDocument().modify().set("modified", true).save();
      });
      database.transaction((tx) -> {
        database.iterateType("Document1", true).next().asDocument().delete();
      });

      setCurrentUser("root", database);
      dropSchema(database);
    } finally {
      SECURITY.dropUser("elon");
    }
  }

  @Test
  void createOnlyAccess() throws Throwable {
    SECURITY.createUser(new JSONObject().put("name", "elon").put("password", SECURITY.encodePassword("musk"))
        .put("databases", new JSONObject().put(DATABASE_NAME, new JSONArray(new String[] { "creator" }))));

    try (DatabaseInternal database = (DatabaseInternal) SERVER.getDatabase(DATABASE_NAME)) {
      checkElonUser(setCurrentUser("elon", database));

      createSchemaNotAllowed(database);

      // SWITCH TO ROOT TO CREATE SOME TYPES FOR FURTHER TESTS
      setCurrentUser("root", database);

      createSchema(database);

      // SWITCH BACK TO ELON
      checkElonUser(setCurrentUser("elon", database));

      final RID validRID = createSomeRecords(database, false);

      database.newVertex("Vertex1").save();
      database.newDocument("Document1").save();
      expectedSecurityException(() -> database.iterateType("Document1", true));
      expectedSecurityException(() -> database.lookupByRID(validRID, true));

      // SWITCH TO ROOT TO DROP THE SCHEMA
      setCurrentUser("root", database);
      dropSchema(database);

    } finally {
      SECURITY.dropUser("elon");
    }
  }

  @Test
  void readOnlyAccess() throws Throwable {
    SECURITY.createUser(new JSONObject().put("name", "elon").put("password", SECURITY.encodePassword("musk"))
        .put("databases", new JSONObject().put(DATABASE_NAME, new JSONArray(new String[] { "reader" }))));

    try (DatabaseInternal database = (DatabaseInternal) SERVER.getDatabase(DATABASE_NAME)) {
      checkElonUser(setCurrentUser("elon", database));

      createSchemaNotAllowed(database);

      // SWITCH TO ROOT TO CREATE SOME TYPES FOR FURTHER TESTS
      setCurrentUser("root", database);

      createSchema(database);

      final RID validRID = createSomeRecords(database, true);

      // SWITCH BACK TO ELON
      checkElonUser(setCurrentUser("elon", database));

      expectedSecurityException(() -> database.newVertex("Vertex1").save());
      expectedSecurityException(() -> database.newDocument("Document1").save());
      database.iterateType("Document1", true);
      database.lookupByRID(validRID, true);

      // SWITCH TO ROOT TO DROP THE SCHEMA
      setCurrentUser("root", database);
      dropSchema(database);

    } finally {
      SECURITY.dropUser("elon");
    }
  }

  @Test
  void updateOnlyAccess() throws Throwable {
    SECURITY.createUser(new JSONObject().put("name", "elon").put("password", SECURITY.encodePassword("musk"))
        .put("databases", new JSONObject().put(DATABASE_NAME, new JSONArray(new String[] { "updater" }))));

    try (DatabaseInternal database = (DatabaseInternal) SERVER.getDatabase(DATABASE_NAME)) {
      checkElonUser(setCurrentUser("elon", database));

      createSchemaNotAllowed(database);

      // SWITCH TO ROOT TO CREATE SOME TYPES FOR FURTHER TESTS
      setCurrentUser("root", database);

      createSchema(database);

      final RID validRID = createSomeRecords(database, true);

      Vertex v = validRID.getRecord(true).asVertex();

      // SWITCH BACK TO ELON
      checkElonUser(setCurrentUser("elon", database));

      expectedSecurityException(() -> database.newVertex("Vertex1").save());
      expectedSecurityException(() -> database.newDocument("Document1").save());
      expectedSecurityException(() -> database.iterateType("Document1", true));
      expectedSecurityException(() -> database.lookupByRID(validRID, true));

      database.transaction((tx) -> {
        v.modify().set("justModified", true).save();
      });

      // SWITCH TO ROOT TO DROP THE SCHEMA
      setCurrentUser("root", database);
      dropSchema(database);

    } finally {
      SECURITY.dropUser("elon");
    }
  }

  @Test
  void deleteOnlyAccess() throws Throwable {
    SECURITY.createUser(new JSONObject().put("name", "elon").put("password", SECURITY.encodePassword("musk"))
        .put("databases", new JSONObject().put(DATABASE_NAME, new JSONArray(new String[] { "deleter" }))));

    try (DatabaseInternal database = (DatabaseInternal) SERVER.getDatabase(DATABASE_NAME)) {
      checkElonUser(setCurrentUser("elon", database));

      createSchemaNotAllowed(database);

      // SWITCH TO ROOT TO CREATE SOME TYPES FOR FURTHER TESTS
      setCurrentUser("root", database);

      createSchema(database);

      createSomeRecords(database, true);
      Document doc = database.iterateType("Document1", true).next().asDocument();

      // SWITCH BACK TO ELON
      checkElonUser(setCurrentUser("elon", database));

      expectedSecurityException(() -> database.newVertex("Vertex1").save());
      expectedSecurityException(() -> database.newDocument("Document1").save());
      expectedSecurityException(() -> database.iterateType("Document1", true));
      expectedSecurityException(() -> database.lookupByRID(doc.getIdentity(), true));

      database.transaction((tx) -> {
        database.deleteRecord(doc);
      });

      // SWITCH TO ROOT TO DROP THE SCHEMA
      setCurrentUser("root", database);
      dropSchema(database);

    } finally {
      SECURITY.dropUser("elon");
    }
  }

  private void createSchemaNotAllowed(DatabaseInternal database) throws Throwable {
    expectedSecurityException(() -> database.getSchema().createBucket("Bucket1"));
    expectedSecurityException(() -> database.getSchema().createVertexType("Vertex1"));
    expectedSecurityException(() -> database.getSchema().createEdgeType("Edge1"));
    expectedSecurityException(() -> database.getSchema().createDocumentType("Document1"));

    expectedSecurityException(() -> database.getSchema()
        .createBucketIndex(Schema.INDEX_TYPE.LSM_TREE, true, "Document1", "Bucket1", new String[] { "id" }, 10000, LSMTreeIndexAbstract.NULL_STRATEGY.ERROR,
            null));
    expectedSecurityException(() -> database.getSchema().createTypeIndex(Schema.INDEX_TYPE.LSM_TREE, true, "Document1", "id"));

    expectedSecurityException(() -> database.getSchema().dropType("Document1"));
    expectedSecurityException(() -> database.getSchema().dropIndex("Idx1"));
    expectedSecurityException(() -> database.getSchema().dropBucket("Bucket1"));
  }

  private void expectedSecurityException(CallableNoReturn callback) throws Throwable {
    try {
      callback.call();
      Assertions.fail();
    } catch (SecurityException e) {
      // EXPECTED
    }
  }

  private void checkElonUser(ServerSecurityUser elon) {
    Assertions.assertNotNull(elon);
    ServerSecurityUser authElon = SECURITY.authenticate("elon", "musk", null);
    Assertions.assertNotNull(authElon);
    Assertions.assertEquals(elon.getName(), authElon.getName());

    SecurityUserFileRepository repository = new SecurityUserFileRepository("./target/config");
    Assertions.assertEquals(2, repository.getUsers().size());
    Assertions.assertEquals("elon", repository.getUsers().get(1).getString("name"));
  }

  private ServerSecurityUser setCurrentUser(final String userName, DatabaseInternal database) {
    final ServerSecurityUser user = SECURITY.getUser(userName);
    SecurityDatabaseUser dbUser = user.getDatabaseUser(database);
    DatabaseContext.INSTANCE.init(database).setCurrentUser(dbUser);
    Assertions.assertEquals(dbUser, DatabaseContext.INSTANCE.getContext(database.getDatabasePath()).getCurrentUser());
    return user;
  }

  private RID createSomeRecords(DatabaseInternal database, boolean createEdge) {
    final AtomicReference<RID> validRID = new AtomicReference<>();
    database.transaction((tx) -> {
      MutableVertex v1 = database.newVertex("Vertex1").save();
      MutableVertex v2 = database.newVertex("Vertex1").save();
      if (createEdge)
        v1.newEdge("Edge1", v2, true);
      database.newDocument("Document1").save();

      validRID.set(v1.getIdentity());
    });
    return validRID.get();
  }

  private void createSchema(DatabaseInternal database) {
    database.getSchema().createBucket("Bucket1");
    database.getSchema().createVertexType("Vertex1");
    database.getSchema().createEdgeType("Edge1");
    database.getSchema().createDocumentType("Document1");
  }

  private void dropSchema(DatabaseInternal database) {
    database.getSchema().dropBucket("Bucket1");
    database.getSchema().dropType("Vertex1");
    database.getSchema().dropType("Edge1");
    database.getSchema().dropType("Document1");
  }

  @BeforeAll
  public static void beforeAll() {
    FileUtils.deleteRecursively(new File("./target/config"));
    FileUtils.deleteRecursively(new File("./target/databases"));
    GlobalConfiguration.SERVER_ROOT_PASSWORD.setValue("dD5ed08c");
    GlobalConfiguration.SERVER_DATABASE_DIRECTORY.setValue("./target/databases");
    GlobalConfiguration.SERVER_ROOT_PATH.setValue("./target");

    SERVER = new ArcadeDBServer();
    SERVER.start();
    SECURITY = SERVER.getSecurity();
    SECURITY.getDatabaseGroupsConfiguration(DATABASE_NAME)
        .put("reader", new JSONObject().put("types", new JSONObject().put("*", new JSONObject().put("access", new JSONArray(new String[] { "readRecord" })))));
    SECURITY.getDatabaseGroupsConfiguration(DATABASE_NAME).put("creator",
        new JSONObject().put("types", new JSONObject().put("*", new JSONObject().put("access", new JSONArray(new String[] { "createRecord" })))));
    SECURITY.getDatabaseGroupsConfiguration(DATABASE_NAME).put("updater",
        new JSONObject().put("types", new JSONObject().put("*", new JSONObject().put("access", new JSONArray(new String[] { "updateRecord" })))));
    SECURITY.getDatabaseGroupsConfiguration(DATABASE_NAME).put("deleter",
        new JSONObject().put("types", new JSONObject().put("*", new JSONObject().put("access", new JSONArray(new String[] { "deleteRecord" })))));

    SECURITY.getDatabaseGroupsConfiguration(DATABASE_NAME).put("readerOfDocuments",//
        new JSONObject().put("types", new JSONObject().put("Document1", new JSONObject().put("access", new JSONArray(new String[] { "readRecord" })))));
    SECURITY.getDatabaseGroupsConfiguration(DATABASE_NAME).put("creatorOfDocuments",
        new JSONObject().put("types", new JSONObject().put("Document1", new JSONObject().put("access", new JSONArray(new String[] { "createRecord" })))));
    SECURITY.getDatabaseGroupsConfiguration(DATABASE_NAME).put("updaterOfDocuments",
        new JSONObject().put("types", new JSONObject().put("Document1", new JSONObject().put("access", new JSONArray(new String[] { "updateRecord" })))));
    SECURITY.getDatabaseGroupsConfiguration(DATABASE_NAME).put("deleterOfDocuments",
        new JSONObject().put("types", new JSONObject().put("Document1", new JSONObject().put("access", new JSONArray(new String[] { "deleteRecord" })))));

    SERVER.getOrCreateDatabase(DATABASE_NAME);
  }

  @AfterAll
  public static void afterAll() throws IOException {
    SERVER.start();
    GlobalConfiguration.SERVER_ROOT_PASSWORD.setValue(null);

    FileUtils.deleteRecursively(new File("./target/config"));
    FileUtils.deleteRecursively(new File("./target/databases"));
  }
}
