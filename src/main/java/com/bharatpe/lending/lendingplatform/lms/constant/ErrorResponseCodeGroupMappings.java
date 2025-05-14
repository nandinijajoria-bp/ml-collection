package com.bharatpe.lending.lendingplatform.lms.constant;

import java.util.*;

import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.Consumer.*;

/**
 * This class is responsible for managing error code group mappings for the lending platform response.
 * <p>
 * Notes:
 * - This class handles the error codes received from the lending platform.
 * - Please ensure that the error codes and their groupings are in sync with the corresponding
 * class in the lending platform service.
 * - If any updates or changes are made to the error codes or their groupings in the lending platform,
 * reflect those changes here to maintain consistency.
 * - Ensure the group names and error codes match the ones used in the lending platform for easier
 * identification and synchronization.
 * - After making updates, verify the changes with the lending platform to ensure compatibility
 * and correctness.
 */

public class ErrorResponseCodeGroupMappings {
    private static final Map<String, Set<String>> errorCodeGroups = initializeErrorCodeGroups();

    private ErrorResponseCodeGroupMappings() {
        // Prevent instantiation
    }

    private static Map<String, Set<String>> initializeErrorCodeGroups() {
        Map<String, Set<String>> groups = new HashMap<>();

        // Group LMS Errors (1XXX)
        groups.put(LMS_ERRORS, Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                "1001", // TOKEN_FAILED
                "1002", // INVALID_APPLICATION_STATUS
                "1003", // LOAN_DATA_NOT_FOUND
                "1004", // CLIENT_REQUEST_FAILURE
                "1005", // EMPTY_TOKEN_RESPONSE
                "1006", // DB_DUPLICATE_ENTRY
                "1007", // RESPONSE_STATUS_FAILURE
                "1101", // UNKNOWN_ERROR
                "1102"  // UNEXPECTED_RESPONSE
        ))));

        // Group Custom LMS Errors (2XXX)
        groups.put(CUSTOM_LMS_ERRORS, Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                "2001", // SOCKET_ERROR
                "2002", // INVALID_RESPONSE
                "2003", // SERVER_ERROR
                "2004", // RESOURCE_ACCESS_ERROR
                "2005"  // RETRYING_PROCESS
        ))));

        // Group RestControllerAdvice Constants (3XXX)
        groups.put(REST_ADVICE_ERRORS, Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                "3001", // FIELD_NOT_VALID
                "3002", // MALFORMED_JSON
                "3003", // MISSING_PARAMETER
                "3004", // OBJECT_CONVERSION_ERROR
                "3005"  // GLOBAL_UNKNOWN_EXCEPTION
        ))));

        // Group Field Validation Errors (4XXX)
        groups.put(FIELD_VALIDATION_ERRORS, Collections.unmodifiableSet(new HashSet<>(Collections.singletonList(
                "4001" // INVALID_ENUMS
        ))));

        // Group Retry Errors (5XXX)
        groups.put(RETRY_EXHAUSTED, Collections.unmodifiableSet(new HashSet<>(Collections.singletonList(
                "5001" // SYNC_RETRY_EXHAUSTED
        ))));

        return Collections.unmodifiableMap(groups);
    }

    /**
     * Checks if the given error code belongs to the specified group.
     *
     * @param groupName the name of the group
     * @param errorCode the error code to check
     * @return true if the error code belongs to the group, false otherwise
     */
    public static boolean doesErrorCodeBelongToGroup(String groupName, String errorCode) {
        Set<String> group = errorCodeGroups.get(groupName);
        return group != null && group.contains(errorCode);
    }

    /**
     * Retrieves the group name for a given error code.
     *
     * @param errorCode the error code to check
     * @return the group name if found, null otherwise
     */
    public static String findGroupForErrorCode(String errorCode) {
        return errorCodeGroups.entrySet().stream()
                .filter(entry -> entry.getValue().contains(errorCode))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}