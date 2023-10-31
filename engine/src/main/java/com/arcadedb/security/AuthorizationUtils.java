package com.arcadedb.security;

import java.util.Map;
import java.util.Set;

public class AuthorizationUtils {

  /**
   * The valid classification options, in order of increasing sensitivity. Permits math comparisons.
   * There may be a faster/better way of doing this, but bigger fish to fry first
   */
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

    var deploymentClassification = System.getProperty("deploymentClassifcation", "S");
    return classificationOptions.get(deploymentClassification) >= classificationOptions.get(c);
  }

  /**
   * Checks if the user is authorized to view the resource, taking into account the user's clearance, attributes, the full resouce ACCM markings.
   * @param userClearance
   * @param nationality
   * @param resourceClassification
   * @return
   */
  public static boolean isUserAuthorizedForResourceMarking(final String userClearance, final String nationality, final String tetragraphs,
        final String resourceClassification) {
    
    // TODO are tetrapgrahs that group countries auto applicable to users of those countires, or do users need explicit authoirzation for their data?
    var processedResourceClassification = resourceClassification.replace("FVEY", "USA,AUS,CAN,GBR,NZL");

    if (!isClearanceAuthorized(userClearance, processedResourceClassification)) {
      return false;
    }

    // NO FORN takes precedence over REL TO?
    if (isBlockedByNoForn(nationality, processedResourceClassification)) {
        return false;
    }

    if (isBlockedByReleaseableTo(nationality, tetragraphs, processedResourceClassification)) {
        return false;
    }

    return true;
  }

  /**
   * Checks if the user has sufficient clearance to view the resource, outside of any other ACCM restrictions.
   * @param userClearance
   * @param resourceClassificationMarking
   * @return
   */
  public static boolean isClearanceAuthorized(final String userClearance, final String resourceClassificationMarking) {
    if (userClearance == null || userClearance.isEmpty())
      return false;

    String processedUserClearance = userClearance.toUpperCase();

    String processedResourceClearance = getClassificationFromResourceMarkings(resourceClassificationMarking);

    if (!classificationOptions.containsKey(processedUserClearance))
      throw new IllegalArgumentException("Clearance must be one of " + classificationOptions);

    if (resourceClassificationMarking == null || resourceClassificationMarking.isEmpty())
      return false;

    if (!classificationOptions.containsKey(processedResourceClearance))
      throw new IllegalArgumentException("Invalid resource classification " + processedResourceClearance);

    return classificationOptions.get(processedUserClearance) >= classificationOptions.get(processedResourceClearance);
  }

  /**
   * Checks if the classification markings contains a releaseable to block, and if so, checks if the user
   * belongs to an allowable nationality.
   * @param nationality
   * @param resouceClassificationMarkings
   * @return
   */
  private static boolean isBlockedByReleaseableTo(final String nationality, final String tetragraphs, 
        final String resourceClassificationMarkings) {
    // TODO add support for banner barking AUTHORIZED FOR RELEASE TO
    if (resourceClassificationMarkings.contains("REL TO")) {
        if (nationality == null || nationality.isEmpty()) {
          return true;
        }

        var releaseableTo = resourceClassificationMarkings.substring(resourceClassificationMarkings.indexOf("REL TO"));
        if (releaseableTo.contains("//")) {
            releaseableTo.substring(0, releaseableTo.indexOf("//"));
        }

        releaseableTo = releaseableTo.substring("REL TO".length() + 1);
        releaseableTo = releaseableTo.replaceAll(" ", "");

        if (releaseableTo.contains(",")) {
            return !Set.of(releaseableTo.split(",")).stream().map(r -> r.toString()).anyMatch(r -> {
              if (r.trim().isEmpty()) {
                return false;
              } else if (r.length() == 3) {
                return r.equals(nationality);
              } else if (tetragraphs != null && !tetragraphs.isEmpty() && r.length() == 4) {
                return tetragraphs.contains(r);
              }
              return false;
            });
        } else {
            return !releaseableTo.equals(nationality);
        }
    }

    return false;
  }

  /**
   * Checks if the user is unable to see the resource due to a NOFORN marking.
   * @param nationality
   * @param resourceClassification
   * @return
   */
  private static boolean isBlockedByNoForn(final String nationality, final String resourceClassification) {
    return (containsBlockText("NOFORN", resourceClassification) || containsBlockText("NF", resourceClassification)) 
        && (nationality == null || !nationality.equals("USA"));
  }

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
}
