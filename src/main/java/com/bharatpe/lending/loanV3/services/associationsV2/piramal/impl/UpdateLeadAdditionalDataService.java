package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.loanV3.dto.piramal.CreateLeadRequestDTO;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcRequestDto;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.services.gateway.piramal.ILenderGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class UpdateLeadAdditionalDataService {

    @Autowired
    ILenderGateway iLenderGateway;

    public Boolean updateLeadAditionalData(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        try {
            // check this once and add validation layer
            NbfcRequestDto updateLeadRequestDto = getPayload(lendingApplication, lendingApplicationLenderDetails);
            if (Objects.isNull(updateLeadRequestDto)) {
                log.info("error in update lead payload for applicationId: {}", lendingApplication.getId());
                return false;
            }
            NbfcResponseDto updateLeadResponseDTO = iLenderGateway.invokeStage(updateLeadRequestDto, LenderAssociationStages.PiramalAssociationStages.UPDATE_LEAD);
            log.info("update lead response from nbfc: {} with applicationId: {}", updateLeadResponseDTO, lendingApplication.getId());
            if (Objects.nonNull(updateLeadResponseDTO) && updateLeadResponseDTO.getSuccess() && Objects.nonNull(updateLeadResponseDTO.getData())) {
                return true;
            }
        } catch (Exception e) {
            log.error("error while pushing update lead for  {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return Boolean.FALSE;
    }

    private NbfcRequestDto getPayload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {


        List<CreateLeadRequestDTO.ApplicantsDetail> applicant = new ArrayList<>();
        applicant.add(getApplicantDetails(lendingApplication, lendingApplicationLenderDetails));
        CreateLeadRequestDTO createLeadRequestDTO = CreateLeadRequestDTO.builder()
                .leadId(lendingApplicationLenderDetails.getLeadId())
                .partnerApplicationId(lendingApplication.getExternalLoanId())
                .auditTrailInformation(createAuditTrailList(lendingApplication, lendingApplicationLenderDetails))
                .build();
        return NbfcRequestDto.builder()
                .applicationId(lendingApplication.getId())
                .payload(createLeadRequestDTO)
                .lender("PIRAMAL")
                .productName("LENDING")
                .build();
    }

    private CreateLeadRequestDTO.ApplicantsDetail getApplicantDetails(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {

        CreateLeadRequestDTO.ApplicantsDetail applicantsDetail = CreateLeadRequestDTO.ApplicantsDetail.builder()
                .customerId(lendingApplicationLenderDetails.getCccId())
                .build();
        return applicantsDetail;
    }

    private CreateLeadRequestDTO.AuditTrailInformation createAuditTrailList(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        List<CreateLeadRequestDTO.AuditTrailInformation.AuditTrailList> auditTrailLists = new ArrayList<>();
        CreateLeadRequestDTO.AuditTrailInformation.AuditTrailList agreementAuditTrail = CreateLeadRequestDTO.AuditTrailInformation.AuditTrailList.builder()
                .auditCode("BRTPE_AGREEMENT_CONSENT")
                .auditName("By clicking on I Agree, I accept the Key Facts Statement, Sanction and loan agreement, Privacy Policy and Terms & Conditions of LSP")
                .ipAddress(lendingApplication.getIp())
                .timeStamp(DateTimeUtil.getDateInFormat(lendingApplication.getAgreementAt(), "yyyy-MM-dd'T'HH:mm:ss.000'Z'"))
                .build();
        CreateLeadRequestDTO.AuditTrailInformation.AuditTrailList privacyPolicyAuditTrail = CreateLeadRequestDTO.AuditTrailInformation.AuditTrailList.builder()
                .auditCode("BRTPE_PRIVACY_POLICY_CONSENT")
                .auditName("Please read our Privacy policy and Terms & Conditions")
                .ipAddress(lendingApplication.getIp())
                .timeStamp(DateTimeUtil.getDateInFormat(lendingApplication.getAgreementAt(), "yyyy-MM-dd'T'HH:mm:ss.000'Z'"))
                .build();
        auditTrailLists.add(agreementAuditTrail);
        auditTrailLists.add(privacyPolicyAuditTrail);
        CreateLeadRequestDTO.AuditTrailInformation auditTrailInformation = CreateLeadRequestDTO.AuditTrailInformation.builder()
                .auditTrailList(auditTrailLists)
                .build();
        return auditTrailInformation;
    }
}
