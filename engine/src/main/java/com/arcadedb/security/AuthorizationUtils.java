package com.arcadedb.security;

import java.util.Map;
import java.util.logging.Level;

import com.arcadedb.database.Document;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.database.EmbeddedDatabase.RecordAction;
import com.arcadedb.exception.ValidationException;
import com.arcadedb.log.LogManager;
import com.arcadedb.serializer.json.JSONObject;
import com.arcadedb.security.ACCM.TypeRestriction;

public class AuthorizationUtils {

  /**
   * The valid classification options, in order of increasing sensitivity. Permits math comparisons.
   * There may be a faster/better way of doing this, but bigger fish to fry first
   */

  // todo move to opa since it needs to be configurable
  public static final Map<String, Integer> classificationOptions = Map.of("U", 0, "CUI", 1, "C", 2, "S", 3, "TS", 4);

  /**
   * Checks if the provided classification is permitted given the deployment classification.
   * @param classification
   * @return
   */
  public static boolean isClassificationValidForDeployment(final String classification) {
    if (classification == null || classification.isEmpty())
      return false;

    var c = classification.toUpperCase();

    if (c.contains("//")) {
        c = getClassificationFromResourceMarkings(c);
    }

    if (!classificationOptions.containsKey(c))
      throw new IllegalArgumentException("Classification must be one of " + classificationOptions);

    var deploymentClassification = System.getProperty("deploymentClassification", "S");
    return classificationOptions.get(deploymentClassification) >= classificationOptions.get(c);
  }

  /**
   * Remove the parenthesis and any spaces from the classification string that may be a part of a portion marking.
   * @param resourceClassification
   * @return
   */
  private static String cleanResourceClassification(String resourceClassification) {
    resourceClassification = resourceClassification.toUpperCase();
    resourceClassification = resourceClassification.replace("(", "");
    resourceClassification = resourceClassification.replace(")", "");
    resourceClassification = resourceClassification.trim();
    return resourceClassification;
  }

  /**
   * Parse out just the classification from the full classification attribution string.
   * @param resourceMarkings
   * @return
   */
  private static String getClassificationFromResourceMarkings(final String resourceMarkings) {

    if (resourceMarkings == null || resourceMarkings.isEmpty()) {
      return null;
    }

    String classification = cleanResourceClassification(resourceMarkings);

    if (classification.contains("//")) {
        return classification.substring(0, classification.indexOf("//"));
    }
    return classification;
  }

  public static boolean checkPermissionsOnDocumentToRead(final Document document, final SecurityDatabaseUser currentUser) {
    // log duration in ns
    long startTime = System.nanoTime();
    boolean result = checkPermissionsOnDocument(document, currentUser, RecordAction.READ);

    long endTime = System.nanoTime();
    long duration = (endTime - startTime);
    LogManager.instance().log(AuthorizationUtils.class, Level.INFO, "checkPermissionsOnDocumentToRead took " + duration + " ns");

    return result;
  }

  // split out crud actions
  public static boolean checkPermissionsOnDocument(final Document document, final SecurityDatabaseUser currentUser, final RecordAction action) {
    // Allow root user to access all documents for HA syncing between nodes
    if (currentUser.getName().equals("root")) {
      return true;
    }

    LogManager.instance().log(AuthorizationUtils.class, Level.WARNING, "check perms on doc: " + action);

    // TODO short term - check classification, attribution on document

    // TODO long term - replace with filtering by low classification of related/linked document.
    // Expensive to do at read time. Include linkages and classification at write time?
    // Needs performance testing and COA analysis.

    // TODO prevent data stewards from seeing data outside their access
    if (currentUser.isServiceAccount() || currentUser.isDataSteward(document.getTypeName())) {
      return true;
    }

    // If classification is not enabled on database it does not make sense to keep going. is not enabled.
    if (!document.getDatabase().getSchema().getEmbedded().isClassificationValidationEnabled()) {
      return true;
    }

    // Prevent users from accessing documents that have not been marked, unless we're evaluating a user's permission to a doc that hasn't been created yet.
    // The action where we do not want to raise exception is on create and update. The update is included because on edge creation there is technically
    // an update in place where we actually link two vertices.
    if ( (!document.has(MutableDocument.CLASSIFICATION_MARKED) || !document.getBoolean(MutableDocument.CLASSIFICATION_MARKED)) &&
            (RecordAction.CREATE != action && RecordAction.UPDATE != action)) {
      throw new ValidationException("Classification markings are missing on document");
    }
    
    // todo add check for type if edge or vertex. Check if vertex or edge can have the same names.
    String dbName = document.getDatabase().getName();
    var databasePolicy = currentUser.getOpaPolicy().stream().filter(policy -> policy.getDatabase().equals(dbName)).findFirst().orElse(null);

    // Supporting regex match on dbnames as well
    if (databasePolicy == null) {
      // TODO change back to regex match HERE
      databasePolicy = currentUser.getOpaPolicy().stream().filter(policy -> policy.getDatabase().equals("*")).findFirst().orElse(null);
    }

    if (databasePolicy == null) {
      throw new ValidationException("Missing policy for database");
    }

    // get typerestriction for document type name, support regex type restriction name
    var typeRestriction = databasePolicy.getTypeRestrictions().stream().filter(tr -> tr.getName().matches(document.getTypeName())).findFirst().orElse(null);

    // java regex string matcher
    if (typeRestriction == null) {
      // TODO change back to regex match HERE
      typeRestriction = databasePolicy.getTypeRestrictions().stream().filter(tr -> tr.getName().equals("*")).findFirst().orElse(null);
    }

    if (typeRestriction == null) {
      throw new ValidationException("Missing type restrictions for user");
    }

    // if (document.toJSON().has("sources")) {
    //   // Combining all results
    //   boolean results = true;
    //   JSONArray sources = document.toJSON().getJSONArray("sources");
    //   for (int i = 0; i < sources.length(); i++) {
    //     results &= evalutateAccm(typeRestriction, sources.getJSONObject(i), action);
    //   }
    //   return results;
    // } else if (document.toJSON().has("classification")) {
    //   return evalutateAccm(typeRestriction, document.toJSON().getJSONObject("classification"), action);
    // } else {
    //   throw new ValidationException("Misformated classification payload");
    // }

    if (document.has("classification")) {

      var map = document.getMap("classification");
      // map to json
      JSONObject classification = new JSONObject(map);

      return evalutateAccm(typeRestriction, classification, action);
    }
   //)

   // TODO add sources back in
   return true;

  }

  // TODO looking at classification payload only to determine if document has the proper markings. Since OPA is not aware of record schema columns/record attributes will not be available hence we are narrowing down the validation to classification object only.
  private static boolean evalutateAccm(final TypeRestriction typeRestriction, final JSONObject classificationJson, final RecordAction action) {
    // TODO support multiple type restrictions for a single document type. Could be an explicit, and multiple regex matches.
    // System.out.println("evaluateAccm");
    // LogManager.instance().log(AuthorizationUtils.class, Level.WARNING, "evaluateAccm: " + action);
    
    switch (action) {
      case CREATE:
        return typeRestriction.evaluateCreateRestrictions(classificationJson);
      case READ:
        return typeRestriction.evaluateReadRestrictions(classificationJson);
      case UPDATE:
        return typeRestriction.evaluateUpdateRestrictions(classificationJson);
      case DELETE:
        return typeRestriction.evaluateDeleteRestrictions(classificationJson);
      default:
        LogManager.instance().log(AuthorizationUtils.class, Level.SEVERE, "Invalid action: " + action);
        return false;
    }
  }
}
