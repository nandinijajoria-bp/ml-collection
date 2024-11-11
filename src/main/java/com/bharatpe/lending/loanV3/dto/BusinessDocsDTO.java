package com.bharatpe.lending.loanV3.dto;

import com.bharatpe.lending.enums.KycDocType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BusinessDocsDTO {
    KycDocType docType;
    String pdfUrl;
    Integer priority;
    String docIdentifier;

    private static final Map<String, Integer> lenderMapping = new HashMap<>();
    static {
        lenderMapping.put("UDYAM_CERTIFICATE:IIFL", 1);
        lenderMapping.put("VAT:IIFL", 2);
        lenderMapping.put("GST_CERTIFICATE:IIFL", 2);
        lenderMapping.put("SHOP_AND_ESTABLISHMENT:IIFL", 3);
        lenderMapping.put("REGISTRATION_CERTIFICATE:IIFL", 4);
        lenderMapping.put("MCD_CERTIFICATE:IIFL", 5);
        lenderMapping.put("UDYAM_CERTIFICATE:SMFG", 1);
    }

    public static int getDocPriorityForLender(KycDocType docType, String lender) {
        String key = docType.name() + ":" + lender;
        return lenderMapping.getOrDefault(key, Integer.MAX_VALUE);
    }
}
