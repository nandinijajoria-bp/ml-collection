package com.bharatpe.lending.enums;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
public enum LenderDocLanguageMap {


    ABFL(Arrays.asList("HINDI","TAMIL","TELUGU","BENGALI","MALAYALAM","GUJARATI","MARATHI","KANNADA")),

    TRILLIONLOANS(Arrays.asList("HINDI","TAMIL","TELUGU","BENGALI","MALAYALAM","GUJARATI","MARATHI","KANNADA")),

    LIQUILOANS_NBFC(Arrays.asList("HINDI","TAMIL","TELUGU","BENGALI","MALAYALAM","GUJARATI","MARATHI","KANNADA")),

    PIRAMAL(Arrays.asList("HINDI","TAMIL","BENGALI","MALAYALAM","GUJARATI","MARATHI","KANNADA")),

    MUTHOOT(Arrays.asList("HINDI","TAMIL","TELUGU","BENGALI","MALAYALAM","MARATHI","KANNADA"));


    private final List<String> languages;

    LenderDocLanguageMap(List<String> languages) {
        this.languages = languages;
    }

    public static String fetchSupportedLanguageByLender(String lender , String language) {

        for (LenderDocLanguageMap lenderName : LenderDocLanguageMap.values()) {
            if (lenderName.name().equalsIgnoreCase(lender)) {
                return lenderName.languages.contains(language) ? language : "";
            }
        }

        return "";
    }

}
