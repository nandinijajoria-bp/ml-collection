package com.bharatpe.lending.util;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility class to map technical error descriptions from payment systems
 * to user-friendly messages that can be displayed to customers.
 */
@Component
public class ErrorDescriptionMapper {

    public static final String INSUFFICIENT_BALANCE_MESSAGE =
            "You did not have enough balance in your linked bank account on this date.";

    public static final String DAILY_LIMIT_BREACHED_MESSAGE =
            "Your daily UPI limit had been breached.";

    public static final String TECHNICAL_ISSUE_MESSAGE =
            "There was an interim technical issue. Please pay using Pay Now or link another bank account.";

    public static final String MOBILE_MISMATCH_MESSAGE =
            "The mobile number linked to your bank account does not match the number registered on your UPI app.";

    public static final String DEFAULT_MESSAGE =
            "Your bank is not allowing us to debit. Please visit your nearest bank branch to get this resolved.";

    private static final Map<Pattern, String> ERROR_PATTERNS = new LinkedHashMap<>();

    static {
        // 1. INSUFFICIENT BALANCE PATTERNS
        addPattern("(?i).*insufficient.*", INSUFFICIENT_BALANCE_MESSAGE);
        addPattern("(?i).*balance.*low.*", INSUFFICIENT_BALANCE_MESSAGE);
        addPattern("(?i).*low.*balance.*", INSUFFICIENT_BALANCE_MESSAGE);
        addPattern("(?i).*adequate.*funds.*not.*available.*", INSUFFICIENT_BALANCE_MESSAGE);

        // 2. DAILY/TRANSACTION LIMIT BREACHED PATTERNS
        addPattern("(?i).*limit.*exceed.*", DAILY_LIMIT_BREACHED_MESSAGE);
        addPattern("(?i).*limit.*breach.*", DAILY_LIMIT_BREACHED_MESSAGE);
        addPattern("(?i).*limit.*reached.*", DAILY_LIMIT_BREACHED_MESSAGE);
        addPattern("(?i).*limit.*cross.*", DAILY_LIMIT_BREACHED_MESSAGE);
        addPattern("(?i).*exceeded.*limit.*", DAILY_LIMIT_BREACHED_MESSAGE);
        addPattern("(?i).*beyond.*cap.*", DAILY_LIMIT_BREACHED_MESSAGE);
        addPattern("(?i).*cap.*exceeded.*", DAILY_LIMIT_BREACHED_MESSAGE);
        addPattern("(?i).*amount.*exceeds.*", DAILY_LIMIT_BREACHED_MESSAGE);
        addPattern("(?i).*max.*debit.*limit.*", DAILY_LIMIT_BREACHED_MESSAGE);
        addPattern("(?i).*order.*amount.*should.*", DAILY_LIMIT_BREACHED_MESSAGE);

        // 3. TECHNICAL ISSUE / TIMEOUT PATTERNS
        addPattern("(?i).*timeout.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*time.?out.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*timed.*out.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*server.*error.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*internal.*server.*error.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*service.*unavailable.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*bad.*gateway.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*gateway.*time.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*50[0-4].*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*technical.*issue.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*technical.*error.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*system.*error.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*connection.*reset.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*connection.*error.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*network.*error.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*network.*failure.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*internal.*error.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*internal.*exception.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*psp.*not.*available.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*bank.*not.*available.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*cbs.*offline.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*hsm.*down.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*cut.*off.*in.*process.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*socket.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*dispatch.*failed.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*response.*not.*received.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*issuer.*unavailable.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*high.*response.*time.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*throttl.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*site.*down.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*no.*response.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*ssl.*handshake.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*handshake.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*unknown.*status.*code.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*acknowledgement.*not.*received.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*payment.*service.*error.*", TECHNICAL_ISSUE_MESSAGE);
        addPattern("(?i).*(u90|u92|u93|u80|u86|s95|s96|s97).*", TECHNICAL_ISSUE_MESSAGE);

        // 4. MOBILE NUMBER MISMATCH PATTERNS
        addPattern("(?i).*mobile.*mismatch.*", MOBILE_MISMATCH_MESSAGE);
        addPattern("(?i).*mobile.*changed.*", MOBILE_MISMATCH_MESSAGE);
        addPattern("(?i).*mobile.*removed.*", MOBILE_MISMATCH_MESSAGE);
        addPattern("(?i).*mobile.*not.*match.*", MOBILE_MISMATCH_MESSAGE);
        addPattern("(?i).*phone.*mismatch.*", MOBILE_MISMATCH_MESSAGE);
        addPattern("(?i).*registered.*mobile.*", MOBILE_MISMATCH_MESSAGE);

        // All other errors (bank restrictions, mandate issues, fraud, PIN issues, etc.)
        // will automatically fall through to DEFAULT_MESSAGE
    }

    private static void addPattern(String regex, String message) {
        ERROR_PATTERNS.put(Pattern.compile(regex), message);
    }

    public static String mapToUserMessage(String errorDescription) {
        if (errorDescription == null || errorDescription.trim().isEmpty()) {
            return DEFAULT_MESSAGE;
        }
        for (Map.Entry<Pattern, String> entry : ERROR_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(errorDescription).matches()) {
                return entry.getValue();
            }
        }
        return DEFAULT_MESSAGE;
    }
}