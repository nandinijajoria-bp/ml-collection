package com.bharatpe.lending.loanV3.revamp.util;

import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.enums.KycDocStatus;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.Lender;
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

    public static KycStatusDTO getKycStatusDTO(List<KycDoc> kycDocs, String lender){
        if(CollectionUtils.isEmpty(kycDocs)){
            return KycStatusDTO.builder().kycStatus(KycStatus.NEW).build();
        }
        if (CollectionUtils.isEmpty(kycDocs)) {
            return KycStatusDTO.builder().kycStatus(KycStatus.NEW).build();
        }

        boolean isPanApproved = false;
        boolean isSelfieApprovedOrDraft = false;
        boolean isSelfieApproved = false;
        boolean isPoaApproved = false;

        for (KycDoc kycDoc : kycDocs) {
            if (kycDoc == null || kycDoc.getStatus() == null) {
                continue;
            }

            if (KycDocType.PAN_NO.equals(kycDoc.getDocType()) && KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                isPanApproved = true;
            }

            if (KycDocType.SELFIE.equals(kycDoc.getDocType())) {
                if (KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                    isSelfieApproved = true;
                }
                if (KycDocStatus.DRAFT.equals(kycDoc.getStatus()) || KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                    isSelfieApprovedOrDraft = true;
                }
            }

            if (KycDocType.POA.equals(kycDoc.getDocType()) && KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                isPoaApproved = true;
            }
        }

        if (Lender.LIQUILOANS_P2P.name().equalsIgnoreCase(lender)) {
            if (isPanApproved && isSelfieApproved && isPoaApproved) {
                return KycStatusDTO.builder().kycStatus(KycStatus.APPROVED).build();
            }
        } else {
            if (isPanApproved && isSelfieApprovedOrDraft) {
                return KycStatusDTO.builder().kycStatus(KycStatus.APPROVED).build();
            }
        }
        return KycStatusDTO.builder().kycStatus(KycStatus.REJECTED).build();
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
