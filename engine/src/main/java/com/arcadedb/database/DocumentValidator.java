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
package com.arcadedb.database;

import com.arcadedb.database.EmbeddedDatabase.RecordAction;
import com.arcadedb.exception.ValidationException;
import com.arcadedb.log.LogManager;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.Property;
import com.arcadedb.schema.Type;
import com.arcadedb.security.AuthorizationUtils;
import com.arcadedb.security.DataFabricClassificationClient;
import com.arcadedb.security.SecurityDatabaseUser;
import com.arcadedb.serializer.json.JSONObject;

import java.math.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Validates documents against constraints defined in the schema.
 *
 * @author Luca Garulli (l.garulli@arcadedata.com)
 */
public class DocumentValidator {

  /**
   * Map of ordered classification abbreviations, permitting math comparisons to check classification validity.
   */
  public static final Map<String, Integer> classificationOptions = 
      Map.of("U", 0, "CUI", 1, "C", 2, "S", 3, "TS", 4);

  public static void verifyDocumentClassificationValidForDeployment(String toCheck, String databaseClassification) {
    if (toCheck == null || toCheck.isEmpty())
      throw new IllegalArgumentException("Classification cannot be null or empty");

    if (!classificationOptions.containsKey(toCheck))
      throw new ValidationException("Classification must be one of " + classificationOptions);

    // TODO: Do not default to S. App should not come up if no deployment classification is set.
    var deploymentClassification = System.getProperty("deploymentClassification", "S");
    if (classificationOptions.get(deploymentClassification) < classificationOptions.get(toCheck))
      throw new ValidationException("Classification " + toCheck + " is not allowed in this deployment");

    if (classificationOptions.get(databaseClassification) < classificationOptions.get(toCheck))
      throw new ValidationException("Classification " + toCheck + " is not allowed in this database");
  }

  public static void validateClassificationMarkings(final MutableDocument document, 
          SecurityDatabaseUser securityDatabaseUser, RecordAction action) throws ValidationException {

    if (document == null) {
      throw new ValidationException("Document is null!");
    }

    String message = "Validating classification markings on " + document.toJSON(true)
            + ". CRUD op '" + action + "'...";
    LogManager.instance().log(DocumentValidator.class, Level.INFO, message);

    if (document instanceof MutableEmbeddedDocument) {
      LogManager.instance().log(DocumentValidator.class, Level.INFO,
              "Document is a MutableEmbeddedDocument. Skipping validation.");
      return;
    }

    if (document.getRecordType() == EmbeddedDocument.RECORD_TYPE) {
      LogManager.instance().log(DocumentValidator.class, Level.INFO,
              "Document is a RECORD_TYPE. Skipping validation.");
      return;
    }

    // Skip validation checks if classification validation is disabled for the database
    if (!document.getDatabase().getSchema().getEmbedded().isClassificationValidationEnabled()) {
      LogManager.instance().log(DocumentValidator.class, Level.INFO,
              "Classification validation is disabled on database. Skipping validation.");
      return;
    }

    // TODO: Add sources validation back in.

    if (!document.has(MutableDocument.CLASSIFICATION_PROPERTY)) {
        LogManager.instance().log(DocumentValidator.class, Level.INFO, document.toJSON(true).toString(3));
        throw new ValidationException("Document has no classification property!");
    }

    if (!document.toJSON().getJSONObject(MutableDocument.CLASSIFICATION_PROPERTY).has("components")) {
      throw new ValidationException("Document has no classification.components property!");
    }

    var component = document.toJSON().getJSONObject(MutableDocument.CLASSIFICATION_PROPERTY).getJSONObject("components").toString();
    boolean valid = DataFabricClassificationClient.validateDocumentClassification(component);
    if (!valid) {
      throw new ValidationException("Document has no valid classification defined!");
    }
    // TODO handle SBU, LES, etc.

    // Validate the user can set the classification of the document. Can't create higher than what you can access.
    if (!AuthorizationUtils.checkPermissionsOnDocument(document, securityDatabaseUser, action)) {
      throw new ValidationException("User cannot set classification markings on documents higher than or outside their current access.");
    }

    var classificationObj = new JSONObject(document.get(MutableDocument.CLASSIFICATION_PROPERTY).toString());
    validateAttributeClassificationTagging(document, classificationObj.getJSONObject(MutableDocument.CLASSIFICATION_ATTRIBUTES_PROPERTY), securityDatabaseUser, action);
  }

  private static void validateAttributeClassificationTagging(final MutableDocument document, final JSONObject attributes, SecurityDatabaseUser securityDatabaseUser, RecordAction action) {

    // confirm each json key in document has a matching key in attributes
    // have counter for each key in document, and decrement when found in attributes
    var propNames = new HashSet<>(document.getPropertyNames());
    propNames.remove(MutableDocument.CLASSIFICATION_PROPERTY);
    propNames.remove(MutableDocument.SOURCES_ARRAY_ATTRIBUTE);
    propNames.remove(MutableDocument.CLASSIFICATION_MARKED);
    propNames.remove(Utils.CREATED_BY);
    propNames.remove(Utils.CREATED_DATE);
    propNames.remove(Utils.LAST_MODIFIED_BY);
    propNames.remove(Utils.LAST_MODIFIED_DATE);

    var numProps = propNames.size();
    LogManager.instance().log(DocumentValidator.class, Level.INFO, "Validating document attribute classifications: " + attributes);
    attributes.toMap().forEach((attribute, value) -> {

        // validate valid key
        if (!document.has(attribute)) {
            throw new ValidationException("Invalid attribute key: " + attribute);
        }

        var attributeClassification = value.toString();

        if (attributeClassification != null && !attributeClassification.trim().isEmpty()) {
            // TODO: This call might not be necessary after integrating with df-classification.
            verifyDocumentClassificationValidForDeployment(
                    attributeClassification,
                    document.getDatabase().getSchema().getEmbedded().getClassification());

            // Taking the attribute's classification value and fetching its numerical representation.
            var inputIndex = AuthorizationUtils.classificationOptions.get(attributeClassification);
            var userClearance = securityDatabaseUser.getClearanceForCountryOrTetragraphCode("USA");

            // TODO temp fix. refactor this to depend on the same deployment config specifying classification/clearance options.
            int userClearanceIndex = 0;

            switch(userClearance.toUpperCase()) {
              case "UNCLASSIFIED": userClearanceIndex = 0;
              case "CUI": userClearanceIndex = 1;
              case "CONFIDENTIAL": userClearanceIndex = 2;
              case "SECRET": userClearanceIndex = 3;
              case "TOP SECRET": userClearanceIndex = 4;
            }

           // var userClearanceIndex = AuthorizationUtils.classificationOptions.get(userClearance);

            LogManager.instance().log(DocumentValidator.class, Level.INFO, inputIndex + "_" + userClearanceIndex);

            if (inputIndex > userClearanceIndex) {
                throw new ValidationException(
                        "User cannot set attribute classification markings on documents higher than or outside their current access!" +
                                " User clearance is " + userClearance + " but attribute classification was " + attributeClassification);
            }
        } else {
            throw new ValidationException("Invalid attribute classification marking for: " + attribute);
        }
    });

    if (attributes.length() < numProps) {
      throw new ValidationException("Missing attribute classification data on document: " + attributes.length() + "/" + numProps);
    }
  }

  /**
   * Validate the sources on the document are properly portion marked.
   * Sources are stored in the document as a JSON object, with the key being a numbered list, and the values being the portion marked source id.
   * @param document
   */
  private static void validateSources(final MutableDocument document, SecurityDatabaseUser securityDatabaseUser, RecordAction action) {
    var sources = document.toJSON().getJSONArray(MutableDocument.SOURCES_ARRAY_ATTRIBUTE);

    LogManager.instance().log(DocumentValidator.class, Level.INFO, "Validating source classifications...");
    sources.forEach(obj -> {

      var jo = (JSONObject) obj;

      if (!jo.has(MutableDocument.CLASSIFICATION_GENERAL_PROPERTY)) {
        throw new ValidationException("Source " + jo + " is missing classification property");
      }

      var classification = jo.getString(MutableDocument.CLASSIFICATION_GENERAL_PROPERTY);

      if (!AuthorizationUtils.checkPermissionsOnDocument(document, securityDatabaseUser, action)) {
        throw new ValidationException("User cannot set classification markings on documents higher than or outside their current access.");
      }

      // Classification will end with a double separator if there are any additional ACCM markings.
      if (classification.contains("//")) {
        classification = classification.substring(0, classification.indexOf("//"));
      }

      try {
        var databaseClassification = document.getDatabase().getSchema().getEmbedded().getClassification();
        verifyDocumentClassificationValidForDeployment(classification, databaseClassification);
      } catch (IllegalArgumentException e) {
        throw new ValidationException("Invalid classification for source: " + classification);
      }

      validateAttributeClassificationTagging(document, jo.getJSONObject(MutableDocument.CLASSIFICATION_ATTRIBUTES_PROPERTY), securityDatabaseUser, action);
    });
  }

  public static void validate(final MutableDocument document) throws ValidationException {
    document.checkForLazyLoadingProperties();
    for (Property entry : document.getType().getProperties())
      validateField(document, entry);
  }

  public static void validateField(final MutableDocument document, final Property p) throws ValidationException {

    if (p.isMandatory() && !document.has(p.getName()))
      throwValidationException(p, "is mandatory, but not found on record: " + document);

    final Object fieldValue = document.get(p.getName());

    if (fieldValue == null) {
      if (p.isNotNull() && document.has(p.getName()))
        // NULLITY
        throwValidationException(p, "cannot be null, record: " + document);
    } else {
      if (p.getRegexp() != null)
        // REGEXP
        if (!(fieldValue.toString()).matches(p.getRegexp()))
          throwValidationException(p, "does not match the regular expression '" + p.getRegexp() + "'. Field value is: " 
              + fieldValue + ", record: " + document);

      final Type propertyType = p.getType();

      if (propertyType != null) {
        final String ofType = p.getOfType();

        // CHECK EMBEDDED VALUES
        switch (propertyType) {
        case LINK: {
          if (fieldValue instanceof EmbeddedDocument)
            throwValidationException(p, "has been declared as LINK but an EMBEDDED document is used. Value: " + fieldValue);

          if (ofType != null) {
            final RID rid = ((Identifiable) fieldValue).getIdentity();
            final DocumentType embSchemaType = document.getDatabase().getSchema().getTypeByBucketId(rid.getBucketId());
            if (!embSchemaType.instanceOf(ofType))
              throwValidationException(p,
                  "has been declared as LINK of '" + ofType + "' but a link to type '" + embSchemaType + "' is used. Value: "
                      + fieldValue);
          }
        }
        break;

        case EMBEDDED: {
          if (!(fieldValue instanceof EmbeddedDocument))
            throwValidationException(p, "has been declared as EMBEDDED but an incompatible type is used. Value: " + fieldValue);

          if (ofType != null) {
            final DocumentType embSchemaType = ((EmbeddedDocument) fieldValue).getType();
            if (!embSchemaType.instanceOf(ofType))
              throwValidationException(p,
                  "has been declared as EMBEDDED of '" + ofType + "' but a document of type '" + embSchemaType
                      + "' is used. Value: " + fieldValue);
          }
          if (fieldValue instanceof MutableEmbeddedDocument)
            ((MutableEmbeddedDocument) fieldValue).validate();
        }
        break;

        case LIST: {
          if (!(fieldValue instanceof List))
            throwValidationException(p, "has been declared as LIST but an incompatible type is used. Value: " + fieldValue);

          final Type embType = ofType != null ? Type.getTypeByName(ofType) : null;

          for (final Object item : ((List<?>) fieldValue)) {
            if (ofType != null) {
              if (embType != null) {
                if (Type.getTypeByValue(item) != embType)
                  throwValidationException(p,
                      "has been declared as LIST of '" + ofType + "' but a value of type '" + Type.getTypeByValue(item)
                          + "' is used. Value: " + fieldValue);
              } else if (item instanceof EmbeddedDocument) {
                if (!((EmbeddedDocument) item).getType().instanceOf(ofType))
                  throwValidationException(p, "has been declared as LIST of '" + ofType + "' but an embedded document of type '"
                      + ((EmbeddedDocument) item).getType().getName() + "' is used. Value: " + fieldValue);
              } else if (item instanceof Identifiable) {
                final RID rid = ((Identifiable) item).getIdentity();
                final DocumentType embSchemaType = document.getDatabase().getSchema().getTypeByBucketId(rid.getBucketId());
                if (!embSchemaType.instanceOf(ofType))
                  throwValidationException(p,
                      "has been declared as LIST of '" + ofType + "' but a link to type '" + embSchemaType + "' is used. Value: "
                          + fieldValue);
              }
            }

            if (item instanceof MutableEmbeddedDocument)
              ((MutableEmbeddedDocument) item).validate();
          }
        }
        break;

        case MAP: {
          if (!(fieldValue instanceof Map))
            throwValidationException(p, "has been declared as MAP but an incompatible type is used. Value: " + fieldValue);

          final Type embType = ofType != null ? Type.getTypeByName(ofType) : null;

          for (final Object item : ((Map<?, ?>) fieldValue).values()) {
            if (ofType != null) {
              if (embType != null) {
                if (Type.getTypeByValue(item) != embType)
                  throwValidationException(p,
                      "has been declared as MAP of <String,'" + ofType + "'> but a value of type '" + Type.getTypeByValue(item)
                          + "' is used. Value: " + fieldValue);
              } else if (item instanceof EmbeddedDocument) {
                if (!((EmbeddedDocument) item).getType().instanceOf(ofType))
                  throwValidationException(p,
                      "has been declared as MAP of <String,'" + ofType + "'> but an embedded document of type '" + embType
                          + "' is used. Value: " + fieldValue);
              } else if (item instanceof Identifiable) {
                final RID rid = ((Identifiable) item).getIdentity();
                final DocumentType embSchemaType = document.getDatabase().getSchema().getTypeByBucketId(rid.getBucketId());
                if (!embSchemaType.instanceOf(ofType))
                  throwValidationException(p,
                      "has been declared as LIST of '" + ofType + "' but a link to type '" + embType + "' is used. Value: "
                          + fieldValue);
              }
            }

            if (item instanceof MutableEmbeddedDocument)
              ((MutableEmbeddedDocument) item).validate();
          }
        }
        break;
        }
      }

      if (p.getMin() != null) {
        // CHECK MIN VALUE
        final String min = p.getMin();
        switch (p.getType()) {

        case LONG: {
          final long minAsLong = Long.parseLong(min);
          if (((Number) fieldValue).longValue() < minAsLong)
            throwValidationException(p, "value " + fieldValue + " is less than " + min);
          break;
        }

        case INTEGER: {
          final int minAsInteger = Integer.parseInt(min);
          if (((Number) fieldValue).intValue() < minAsInteger)
            throwValidationException(p, "value " + fieldValue + " is less than " + min);
          break;
        }

        case SHORT: {
          final int minAsInteger = Integer.parseInt(min);
          if (((Number) fieldValue).shortValue() < minAsInteger)
            throwValidationException(p, "value " + fieldValue + " is less than " + min);
          break;
        }

        case BYTE: {
          final int minAsInteger = Integer.parseInt(min);
          if (((Number) fieldValue).byteValue() < minAsInteger)
            throwValidationException(p, "value " + fieldValue + " is less than " + min);
          break;
        }

        case FLOAT: {
          final float minAsFloat = Float.parseFloat(min);
          if (((Number) fieldValue).floatValue() < minAsFloat)
            throwValidationException(p, "value " + fieldValue + " is less than " + min);
          break;
        }

        case DOUBLE: {
          final double minAsDouble = Double.parseDouble(min);
          if (((Number) fieldValue).floatValue() < minAsDouble)
            throwValidationException(p, "value " + fieldValue + " is less than " + min);
          break;
        }

        case DECIMAL: {
          final BigDecimal minAsDecimal = new BigDecimal(min);
          if (((BigDecimal) fieldValue).compareTo(minAsDecimal) < 0)
            throwValidationException(p, "value " + fieldValue + " is less than " + min);
          break;
        }

        case STRING: {
          final int minAsInteger = Integer.parseInt(min);
          if (fieldValue.toString().length() < minAsInteger)
            throwValidationException(p, "contains fewer characters than " + min + " requested");
          break;
        }

        case DATE:
        case DATETIME: {
          final Database database = document.getDatabase();
          final Date minAsDate = (Date) Type.convert(database, min, Date.class);
          final Date fieldValueAsDate = (Date) Type.convert(database, fieldValue, Date.class);

          if (fieldValueAsDate.compareTo(minAsDate) < 0)
            throwValidationException(p,
                "contains the date " + fieldValue + " which precedes the first acceptable date (" + min + ")");
          break;
        }

        case BINARY: {
          final int minAsInteger = Integer.parseInt(min);
          if (fieldValue instanceof Binary) {
            if (((Binary) fieldValue).size() < minAsInteger)
              throwValidationException(p, "contains fewer bytes than " + min + " requested");
          } else if (((byte[]) fieldValue).length < minAsInteger)
            throwValidationException(p, "contains fewer bytes than " + min + " requested");
          break;
        }

        case LIST: {
          final int minAsInteger = Integer.parseInt(min);
          if (((Collection) fieldValue).size() < minAsInteger)
            throwValidationException(p, "contains fewer items than " + min + " requested");
          break;
        }

        case MAP: {
          final int minAsInteger = Integer.parseInt(min);
          if (((Map) fieldValue).size() < minAsInteger)
            throwValidationException(p, "contains fewer items than " + min + " requested");
          break;
        }

        default:
          throwValidationException(p, "value " + fieldValue + " is less than " + min);
        }
      }

      if (p.getMax() != null) {
        // CHECK MAX VALUE
        final String max = p.getMax();

        switch (p.getType()) {
        case LONG: {
          final long maxAsLong = Long.parseLong(max);
          if (((Number) fieldValue).longValue() > maxAsLong)
            throwValidationException(p, "value " + fieldValue + " is greater than " + max);
          break;
        }

        case INTEGER: {
          final int maxAsInteger = Integer.parseInt(max);
          if (((Number) fieldValue).intValue() > maxAsInteger)
            throwValidationException(p, "value " + fieldValue + " is greater than " + max);
          break;
        }

        case SHORT: {
          final int maxAsInteger = Integer.parseInt(max);
          if (((Number) fieldValue).shortValue() > maxAsInteger)
            throwValidationException(p, "value " + fieldValue + " is greater than " + max);
          break;
        }

        case BYTE: {
          final int maxAsInteger = Integer.parseInt(max);
          if (((Number) fieldValue).byteValue() > maxAsInteger)
            throwValidationException(p, "value " + fieldValue + " is greater than " + max);
          break;
        }

        case FLOAT: {
          final float maxAsFloat = Float.parseFloat(max);
          if (((Number) fieldValue).floatValue() > maxAsFloat)
            throwValidationException(p, "value " + fieldValue + " is greater than " + max);
          break;
        }

        case DOUBLE: {
          final double maxAsDouble = Double.parseDouble(max);
          if (((Number) fieldValue).floatValue() > maxAsDouble)
            throwValidationException(p, "value " + fieldValue + " is greater than " + max);
          break;
        }

        case DECIMAL: {
          final BigDecimal maxAsDecimal = new BigDecimal(max);
          if (((BigDecimal) fieldValue).compareTo(maxAsDecimal) > 0)
            throwValidationException(p, "value " + fieldValue + " is greater than " + max);
          break;
        }

        case STRING: {
          final int maxAsInteger = Integer.parseInt(max);
          if (fieldValue.toString().length() > maxAsInteger)
            throwValidationException(p, "contains more characters than " + max + " requested");
          break;
        }

        case DATE:
        case DATETIME: {
          final Database database = document.getDatabase();
          final Date maxAsDate = (Date) Type.convert(database, max, Date.class);
          final Date fieldValueAsDate = (Date) Type.convert(database, fieldValue, Date.class);

          if (fieldValueAsDate.compareTo(maxAsDate) > 0)
            throwValidationException(p,
                "contains the date " + fieldValue + " which is after the last acceptable date (" + max + ")");
          break;
        }

        case BINARY: {
          final int maxAsInteger = Integer.parseInt(max);
          if (fieldValue instanceof Binary) {
            if (((Binary) fieldValue).size() > maxAsInteger)
              throwValidationException(p, "contains more bytes than " + max + " requested");
          } else if (((byte[]) fieldValue).length > maxAsInteger)
            throwValidationException(p, "contains more bytes than " + max + " requested");
          break;
        }

        case LIST: {
          final int maxAsInteger = Integer.parseInt(max);
          if (((Collection) fieldValue).size() > maxAsInteger)
            throwValidationException(p, "contains more items than " + max + " requested");
          break;
        }

        case MAP: {
          final int maxAsInteger = Integer.parseInt(max);
          if (((Map) fieldValue).size() > maxAsInteger)
            throwValidationException(p, "contains more items than " + max + " requested");
          break;
        }

        default:
          throwValidationException(p, "value " + fieldValue + " is greater than " + max);
        }
      }
    }

    if (p.isReadonly()) {
      if (document.isDirty() && document.getIdentity() != null) {
        final Document originalDocument = ((EmbeddedDatabase) document.getDatabase()).getOriginalDocument(document);
        final Object originalFieldValue = originalDocument.get(p.getName());
        if (!Objects.equals(fieldValue, originalFieldValue))
          throwValidationException(p, "is immutable and cannot be altered. Field value is: " + fieldValue);
      }
    }
  }

  private static void throwValidationException(final Property p, final String message) throws ValidationException {
    throw new ValidationException("The property '" + p.getName() + "' " + message);
  }
}
