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
package com.arcadedb.engine;

import com.arcadedb.database.Binary;
import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseInternal;
import com.arcadedb.database.Document;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.database.Document;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.database.RID;
import com.arcadedb.database.Record;
import com.arcadedb.exception.DatabaseOperationException;
import com.arcadedb.log.LogManager;
import com.arcadedb.security.AuthorizationUtils;
import com.arcadedb.security.SecurityDatabaseUser;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.*;

import static com.arcadedb.database.Binary.INT_SERIALIZED_SIZE;

public class BucketIterator implements Iterator<Record> {

  private final DatabaseInternal database;
  private final Bucket           bucket;
  int      nextPageNumber      = 0;
  BasePage currentPage         = null;
  short    recordCountInCurrentPage;
  int      totalPages;
  Record   next                = null;
  int      currentRecordInPage = 0;
  long     browsed             = 0;
  final long limit;

  BucketIterator(final Bucket bucket, final Database db) {
    ((DatabaseInternal) db).checkPermissionsOnFile(bucket.id, SecurityDatabaseUser.ACCESS.READ_RECORD);

    this.database = (DatabaseInternal) db;
    this.bucket = bucket;
    this.totalPages = bucket.pageCount.get();

    final Integer txPageCounter = database.getTransaction().getPageCounter(bucket.id);
    if (txPageCounter != null && txPageCounter > totalPages)
      this.totalPages = txPageCounter;

    limit = database.getResultSetLimit();
    fetchNext();
  }

  public void setPosition(final RID position) throws IOException {
    next = position.getRecord();
    nextPageNumber = (int) (position.getPosition() / bucket.getMaxRecordsInPage());
    currentRecordInPage = (int) (position.getPosition() % bucket.getMaxRecordsInPage()) + 1;
    currentPage = database.getTransaction().getPage(new PageId(position.getBucketId(), nextPageNumber), bucket.pageSize);
    recordCountInCurrentPage = currentPage.readShort(Bucket.PAGE_RECORD_COUNT_IN_PAGE_OFFSET);
  }

  private void fetchNext() {
    database.executeInReadLock(() -> {
      next = null;
      while (true) {
        if (currentPage == null) {
          if (nextPageNumber > totalPages) {
            return null;
          }
          currentPage = database.getTransaction().getPage(new PageId(bucket.file.getFileId(), nextPageNumber), bucket.pageSize);
          recordCountInCurrentPage = currentPage.readShort(Bucket.PAGE_RECORD_COUNT_IN_PAGE_OFFSET);
        }

        if (recordCountInCurrentPage > 0 && currentRecordInPage < recordCountInCurrentPage) {
          try {
            final int recordPositionInPage = (int) currentPage.readUnsignedInt(Bucket.PAGE_RECORD_TABLE_OFFSET + currentRecordInPage * INT_SERIALIZED_SIZE);
            if (recordPositionInPage == 0)
              // CLEANED CORRUPTED RECORD
              continue;

            final long[] recordSize = currentPage.readNumberAndSize(recordPositionInPage);
            if (recordSize[0] > 0 || recordSize[0] == Bucket.FIRST_CHUNK) {
              // NOT DELETED
              final RID rid = new RID(database, bucket.id, ((long) nextPageNumber) * bucket.getMaxRecordsInPage() + currentRecordInPage);

              if (!bucket.existsRecord(rid))
                continue;

              if (!checkPermissionsOnDocument(rid.asDocument(true))) {
                continue;
              }

              if (!checkPermissionsOnDocument(rid.asDocument(true))) {
                continue;
              }

              next = rid.getRecord(false);

              // TODO strip out properties the user doesn't have access to

              return null;

            } else if (recordSize[0] == Bucket.RECORD_PLACEHOLDER_POINTER) {
              // PLACEHOLDER
              final RID rid = new RID(database, bucket.id, ((long) nextPageNumber) * bucket.getMaxRecordsInPage() + currentRecordInPage);

              final Binary view = bucket.getRecordInternal(new RID(database, bucket.id, currentPage.readLong((int) (recordPositionInPage + recordSize[1]))),
                  true);

              if (view == null)
                continue;

              var record = database.getRecordFactory()
              var record = database.getRecordFactory()
                  .newImmutableRecord(database, database.getSchema().getType(database.getSchema().getTypeNameByBucketId(rid.getBucketId())), rid, view, null);

              if (!checkPermissionsOnDocument(record.asDocument(true))) {
                continue;
              }

              // TODO strip out properties the user doesn't have access to

              next = record;
              return null;
            }
          } catch (final Exception e) {
            final String msg = String.format("Error on loading record #%d:%d (error: %s)", currentPage.pageId.getFileId(),
                (nextPageNumber * bucket.getMaxRecordsInPage()) + currentRecordInPage, e.getMessage());
            LogManager.instance().log(this, Level.SEVERE, msg);
          } finally {
            currentRecordInPage++;
          }

        } else if (currentRecordInPage == recordCountInCurrentPage) {
          currentRecordInPage = 0;
          currentPage = null;
          nextPageNumber++;
        } else {
          currentRecordInPage++;
        }
      }
    });
  }

  private boolean checkPermissionsOnDocument(final Document document) {
    // start timer
    Instant start = Instant.now();

    var currentUser = database.getContext().getCurrentUser();

    // TODO short term - check classification, attribution on document

    // TODO long term - replace with filtering by low classification of related/linked document.
    // Expensive to do at read time. Include linkages and classification at write time?
    // Needs performance testing and COA analysis.

    if (currentUser.isServiceAccount() || currentUser.isDataSteward(document.getTypeName())) {
      return true;
    }

    if ((!document.has(MutableDocument.CLASSIFICATION_MARKED) || !document.getBoolean(MutableDocument.CLASSIFICATION_MARKED))) {
      return false;
    }

    // TODO detect and provide org for clearance
    var clearance = currentUser.getClearanceForCountryOrTetragraphCode("USA");
    var nationality = currentUser.getNationality();
    var tetragraphs = currentUser.getTetragraphs();

    if (document.has(MutableDocument.SOURCES)) {
      // sources will be a map, in the form of source number : (classification//ACCM) source id
      // check if user has appropriate clearance for any of the sources for the document
      var isSourceAuthorized = document.toJSON().getJSONObject(MutableDocument.SOURCES).toMap().entrySet().stream().anyMatch(s -> {
        
        var source = s.getValue().toString();
        if (source == null || source.isEmpty()) {
          return false;
        }
        if (!source.contains("(") || !source.contains(")")) {
          return false;
        }

        var sourceClassification = source.substring(source.indexOf("(") + 1, source.indexOf(")"));
        return AuthorizationUtils.isUserAuthorizedForResourceMarking(clearance, nationality, tetragraphs, sourceClassification);
      });

      if (isSourceAuthorized) {
        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toNanos();
        System.out.println("1 Time elapsed: " + timeElapsed);
        return true;
      }
    }

    if (document.has(MutableDocument.CLASSIFICATION_PROPERTY) 
          && document.toJSON().getJSONObject(MutableDocument.CLASSIFICATION_PROPERTY).has(MutableDocument.CLASSIFICATION_GENERAL_PROPERTY)) {
      var docClassification = 
          document.toJSON().getJSONObject(MutableDocument.CLASSIFICATION_PROPERTY).getString(MutableDocument.CLASSIFICATION_GENERAL_PROPERTY);
      if (docClassification != null && !docClassification.isEmpty()) {
        var isAuthorized = AuthorizationUtils.isUserAuthorizedForResourceMarking(clearance, nationality, tetragraphs, docClassification);
        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();
        System.out.println("2 Time elapsed: " + timeElapsed);
        return isAuthorized;
      } else {
        return false;
      }
    }

    return false;
  }

  @Override
  public boolean hasNext() {
    if (limit > -1 && browsed >= limit)
      return false;
    return next != null;
  }

  @Override
  public Record next() {
    if (next == null) {
      throw new IllegalStateException();
    }
    try {
      ++browsed;
      return next;
    } finally {
      try {
        fetchNext();
      } catch (final Exception e) {
        throw new DatabaseOperationException("Cannot scan bucket '" + bucket.name + "'", e);
      }
    }
  }
}
