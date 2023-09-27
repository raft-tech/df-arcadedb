package com.arcadedb.server.security.oidc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.arcadedb.server.security.oidc.role.CRUDPermission;
import com.arcadedb.server.security.oidc.role.DatabaseAdminRole;
import com.arcadedb.server.security.oidc.role.RoleType;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * ArcadeRole is a representation of a role in ArcadeDB in the process of converting between Keycloak and ArcadeDB.
 */
@Data
@Slf4j
public class ArcadeRole {
    /** Characters to use to breakup a role string into discrete components */
    public static final String PERMISSION_DELIMITER = "__";

    /** Keycloak role prefix that signifies it is for arcade data access enforcement */
    public static final String ROLE_PREFIX = "arcade" + PERMISSION_DELIMITER;
    public static final String ALL_WILDCARD = "*";

    /* The following markers are used to indicate the following role components, and lessen the likelihood of 
     * awkward database and table names escaping the delimiter. 
     */
    /** Permission delimited text after this marker will contain the database name the role applies to */
    public static final String DATABASE_MARKER = "d-";
    /** Permission delimited text after this marker will contain the table regex the role applies to */
    public static final String TABLE_MARKER = "t-";
    /** Permission delimited text after this marker will contain the CRUD permissions the role applies to */
    public static final String PERMISSION_MARKER = "p-";

    public static final String SERVER_ADMIN_CREATE_DATABASE = "createDatabase";

    private String name;
    private RoleType roleType;
    private String database;
    private String tableRegex;
    private int readTimeout = -1;
    private int resultSetLimit = -1;

    private DatabaseAdminRole databaseAdminRole;
    private List<CRUDPermission> crudPermissions = new ArrayList<>(0);

    /** check for non empty parts after marker. consolidate all stream checks to single method that confirms count == 1 and part is not empty after marker */
    private static boolean validateRolePart(String keycloakRole, String marker) {
        return Arrays.stream(keycloakRole.split(PERMISSION_DELIMITER))
                .filter(part -> part.startsWith(marker) && part.length() > marker.length())
                .count() == 1;
    }

    /** Validates that the keycloak user role is constructed correctly with parsible components and no duplicates */
    private static boolean isValidKeycloakUserRole(String keycloakRole) {
        boolean containsDatabaseMarker = validateRolePart(keycloakRole, DATABASE_MARKER);
        boolean containsTableMarker = validateRolePart(keycloakRole, TABLE_MARKER);
        boolean containsPermissionMarker = validateRolePart(keycloakRole, PERMISSION_MARKER);
        return isArcadeRole(keycloakRole) && containsDatabaseMarker && containsTableMarker && containsPermissionMarker;
    }

    /**
     * Parse JWT role. Has format of
     * 
     * [arcade prefix]__[role type]__permission
     * [arcade prefix]__[role type]__d-[database]__t-[table regex]__p-[crud]
     * 
     */
    public static ArcadeRole valueOf(String role) {
        if (isArcadeRole(role)) {
            ArcadeRole arcadeRole = new ArcadeRole();
            arcadeRole.name = role;
            arcadeRole.roleType = arcadeRole.getRoleTypeFromString(role);
            log.info("role type: {}", arcadeRole.roleType);

            if (arcadeRole.getRoleType() == RoleType.USER) {
                if (isValidKeycloakUserRole(role)) {
                    String[] parts = role.split(PERMISSION_DELIMITER);
                    for (String part : parts) {
                        if (part.startsWith(DATABASE_MARKER)) {
                            arcadeRole.database = part.substring(2);
                        } else if (part.startsWith(TABLE_MARKER)) {
                            arcadeRole.tableRegex = part.substring(2);
                        } else if (part.startsWith(PERMISSION_MARKER)) {
                            arcadeRole.crudPermissions = part.substring(2)
                                    .chars()
                                    .mapToObj(c -> (char) c)
                                    .map(c -> CRUDPermission.fromKeycloakPermissionAbbreviation(String.valueOf(c)))
                                    .collect(Collectors.toList());
                        }
                    }
                } else {
                    log.warn("invalid arcade role assigned to user: {}", role);
                    return null;
                }
            } else {
                String adminRoleName = role.substring((ROLE_PREFIX + arcadeRole.roleType.name() + PERMISSION_DELIMITER).length());
                arcadeRole.databaseAdminRole = DatabaseAdminRole.fromKeycloakName(adminRoleName);
            }
            return arcadeRole;
        }
        return null;
    }

    public static boolean isArcadeRole(String role) {
        return role.startsWith(ROLE_PREFIX);
    }

    private RoleType getRoleTypeFromString(String role) {
        String prefixRemoved = role.substring((ROLE_PREFIX).length());
        String roleString = prefixRemoved.substring(0, prefixRemoved.indexOf(PERMISSION_DELIMITER));
        log.info("1 2 {} {}", prefixRemoved, roleString);
        return List.of(RoleType.values()).stream().filter(roleType -> roleType.name().equalsIgnoreCase(roleString)).findFirst().orElse(null);
    }
}
