package com.bharatpe.lending.loanV3.revamp.util;

import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.enums.KycDocStatus;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KycUtils {

    private static final List<KycDocType> kycMandatoryDocs = Arrays.asList(KycDocType.PAN_NO, KycDocType.SELFIE, KycDocType.POA);

    public static KycStatusDTO getKycStatusDTO(List<KycDoc> kycDocs){
        if(CollectionUtils.isEmpty(kycDocs)){
            return KycStatusDTO.builder().kycStatus(KycStatus.NEW).build();
        }
        if (kycDocs.size() < kycMandatoryDocs.size()){
            return KycStatusDTO.builder().kycStatus(KycStatus.DRAFT).build();
        }
        for (KycDoc kycDoc : kycDocs) {
            if (KycDocStatus.REJECTED.equals(kycDoc.getStatus())) {
                return KycStatusDTO.builder().kycDocType(kycDoc.getDocType()).kycStatus(KycStatus.REJECTED).remarks(kycDoc.getRemarks()).build();
            }
        }
        for (KycDoc kycDoc : kycDocs) {
            if (kycDoc.getStatus() != null && !KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                return KycStatusDTO.builder().kycDocType(kycDoc.getDocType()).kycStatus(KycStatus.valueOf(kycDoc.getStatus().name())).build();
            }
        }
        return KycStatusDTO.builder().kycStatus(KycStatus.APPROVED).build();
    }

    public static int getAgeFromKycDoc(List<KycDoc> kycDocs){
        if(CollectionUtils.isEmpty(kycDocs)){
            return 0;
        }
        return kycDocs.stream()
                .filter(kycDoc -> Objects.nonNull(kycDoc) && Objects.nonNull(kycDoc.getDob()))
                .filter(kycDoc -> KycDocType.POA.equals(kycDoc.getDocType()))
                .filter(kycDoc -> KycDocStatus.APPROVED.equals(kycDoc.getStatus()))
                .map(kycDoc -> DateUtils.parseDob(kycDoc.getDob()))
                .filter(Objects::nonNull)
                .findFirst()
                .map(DateUtils::getAge)
                .orElse(0);
    }
}
