package com.bharatpe.lending.loanV3.enums.piramal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum DocumentLanguageMap {
    BENGALI (Collections.singletonList("WEST BENGAL")),
    GUJARATI(Collections.singletonList("GUJARAT")),
    KANNADA(Collections.singletonList("KARNATAKA")),
    MARATHI(Collections.singletonList("MAHARASHTRA")),
    MALAYALAM(Collections.singletonList("KERALA")),
    TAMIL(Collections.singletonList("TAMIL NADU")),
    HINDI(Collections.singletonList("")),
    TELUGU(Arrays.asList("TELANGANA","ANDHRA PRADESH")),
    ENGLISH(Collections.singletonList(""));

    private final List<String> languageMap;

    public List<String> getLanguageMap() {
        return languageMap;
    }

    DocumentLanguageMap(List<String> state) {
        this.languageMap = state;
    }

    public static DocumentLanguageMap getDocumentLanguage (String name) {
        for (DocumentLanguageMap stateName: DocumentLanguageMap.values()) {
            if (stateName.getLanguageMap().contains(name.trim().toUpperCase())) {
                return stateName;
            }
        }
        return ENGLISH;
    }
}
