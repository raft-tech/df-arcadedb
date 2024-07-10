package com.arcadedb.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.arcadedb.database.Document;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.database.EmbeddedDatabase.RecordAction;
import com.arcadedb.exception.ValidationException;
import com.arcadedb.log.LogManager;
import com.arcadedb.serializer.json.JSONArray;
import com.arcadedb.serializer.json.JSONObject;
import com.arcadedb.security.ACCM.Argument;
import com.arcadedb.security.ACCM.ArgumentOperator;
import com.arcadedb.security.ACCM.Expression;
import com.arcadedb.security.ACCM.ExpressionOperator;
import com.arcadedb.security.ACCM.GraphType;
import com.arcadedb.security.ACCM.TypeRestriction;

public class AuthorizationUtils {

  /**
   * The valid classification options, in order of increasing sensitivity. Permits math comparisons.
   * There may be a faster/better way of doing this, but bigger fish to fry first
   */

  // todo move to opa since it needs to be configurable
  public static final Map<String, Integer> classificationOptions = Map.of("U", 0, "CUI", 1, "C", 2, "S", 3, "TS", 4);

  // private static TypeRestriction getTypeRestriction() {
  //   Argument classificationArg = new Argument("classificationTest", ArgumentOperator.ANY_OF, new String[]{"U", "S"});
  //   Argument releasableToArg = new Argument("releaseableTo", ArgumentOperator.ANY_IN, new String[]{"USA"});

  //   Expression expression = new Expression(ExpressionOperator.AND, classificationArg, releasableToArg);
  //   TypeRestriction typeRestriction = new TypeRestriction("beta", GraphType.VERTEX, List.of(expression), List.of(expression), List.of(expression), List.of(expression));
  //   return typeRestriction;
  // }

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
   * Checks if the user is authorized to view the resource, taking into account the user's clearance, attributes, the full resouce ACCM markings.
   * @param userClearance
   * @param nationality
   * @param resourceClassification
   * @return
   */
  // public static boolean isUserAuthorizedForResourceMarking(final String userClearance, final String nationality, final String tetragraphs,
  //       final String resourceClassification) {
    
  //   // TODO are tetrapgrahs that group countries auto applicable to users of those countires, or do users need explicit authoirzation for their data?
  //   var processedResourceClassification = resourceClassification.replace("FVEY", "USA,AUS,CAN,GBR,NZL");
  //   if (!isClearanceAuthorized(userClearance, processedResourceClassification)) {
  //     // System.out.println("blocked by clearance");
  //     return false;
  //   }

  //   // NO FORN takes precedence over REL TO?
  //   if (isBlockedByNoForn(nationality, processedResourceClassification)) {
  //     // System.out.println("blocked by noforn");
  //     return false;
  //   }

  //   if (isBlockedByReleaseableTo(nationality, tetragraphs, processedResourceClassification)) {
  //     // System.out.println("blocked by noforn");
  //     return false;
  //   }

  //   return true;
  // }

  /**
   * Checks if the user has sufficient clearance to view the resource, outside of any other ACCM restrictions.
   * @param userClearance
   * @param resourceClassificationMarking
   * @return
   */
  // public static boolean isClearanceAuthorized(final String userClearance, final String resourceClassificationMarking) {
  //   if (userClearance == null || userClearance.isEmpty())
  //     return false;

  //   String processedUserClearance = userClearance.toUpperCase();
  //   processedUserClearance = processedUserClearance.trim();

  //   String processedResourceClearance = getClassificationFromResourceMarkings(resourceClassificationMarking);
  //   processedResourceClearance = processedResourceClearance.trim();

  //   if (!classificationOptions.containsKey(processedUserClearance))
  //     throw new IllegalArgumentException("Clearance must be one of " + classificationOptions);

  //   if (resourceClassificationMarking == null || resourceClassificationMarking.isEmpty())
  //     return false;

  //   if (!classificationOptions.containsKey(processedResourceClearance))
  //     throw new IllegalArgumentException("Invalid resource classification " + processedResourceClearance);

  //   return classificationOptions.get(processedUserClearance) >= classificationOptions.get(processedResourceClearance);
  // }

  /**
   * Checks if the classification markings contains a releaseable to block, and if so, checks if the user
   * belongs to an allowable nationality.
   * @param nationality
   * @return
   */
  // private static boolean isBlockedByReleaseableTo(final String nationality, final String tetragraphs, 
  //       final String resourceClassificationMarkings) {
  //   // TODO add support for banner barking AUTHORIZED FOR RELEASE TO
  //   if (resourceClassificationMarkings.contains("REL TO")) {
  //       if (nationality == null || nationality.isEmpty()) {
  //         return true;
  //       }

  //       var releaseableTo = resourceClassificationMarkings.substring(resourceClassificationMarkings.indexOf("REL TO"));
  //       if (releaseableTo.contains("//")) {
  //           releaseableTo.substring(0, releaseableTo.indexOf("//"));
  //       }

  //       releaseableTo = releaseableTo.substring("REL TO".length() + 1);
  //       releaseableTo = releaseableTo.replaceAll(" ", "");

  //       if (releaseableTo.contains(",")) {
  //           return !Set.of(releaseableTo.split(",")).stream().map(r -> r.toString()).anyMatch(r -> {
  //             if (r.trim().isEmpty()) {
  //               return false;
  //             } else if (r.length() == 3) {
  //               return r.equals(nationality);
  //             } else if (tetragraphs != null && !tetragraphs.isEmpty() && r.length() == 4) {
  //               return tetragraphs.contains(r);
  //             }
  //             return false;
  //           });
  //       } else {
  //           return !releaseableTo.equals(nationality);
  //       }
  //   }

  //   return false;
  // }

  /**
   * Checks if the user is unable to see the resource due to a NOFORN marking.
   * @param nationality
   * @param resourceClassification
   * @return
   */
  // private static boolean isBlockedByNoForn(final String nationality, final String resourceClassification) {
  //   return (containsBlockText("NOFORN", resourceClassification) || containsBlockText("NF", resourceClassification)) 
  //       && (nationality == null || !nationality.equals("USA"));
  // }

  /**
   * Checks if the resource classification markings contains the text between single forward slash blocks.
   * @param text
   * @param resourceClassification
   * @return
   */
  private static boolean containsBlockText(final String text, final String resourceClassification) {
    if (resourceClassification.contains("/")) {
      return Set.of(resourceClassification.split("/")).contains(text);
    } else if (resourceClassification.equals(text)) {
      return true;
    } else {
      // if the string ends with the text, or starts with the text with a space after it, or contains the text with a space before and after it
      return resourceClassification.endsWith(" " + text) || resourceClassification.endsWith("-" + text) 
          || resourceClassification.startsWith(text + " ") || resourceClassification.contains(" " + text + " ");
    }
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

  public static boolean checkPermissionsOnDocumentToCreate(final Document document, final SecurityDatabaseUser currentUser) {
    return checkPermissionsOnDocument(document, currentUser, RecordAction.CREATE);
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

  public static boolean checkPermissionsOnDocumentToUpdate(final Document document, final SecurityDatabaseUser currentUser) {
    return checkPermissionsOnDocument(document, currentUser, RecordAction.UPDATE);
  }

  public static boolean checkPermissionsOnDocumentToDelete(final Document document, final SecurityDatabaseUser currentUser) {
    return checkPermissionsOnDocument(document, currentUser, RecordAction.DELETE);
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
