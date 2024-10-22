package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLTopupApproveRequestDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class TLTopupApproveService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Transactional
    public boolean invokeTopupApprove(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.TOPUP_APPROVE_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            NBFCRequestDTO<?> addChargeRequest = getPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(addChargeRequest)) {
                log.info("error in TopupApprove payload of TrillionLoans for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.TOPUP_APPROVE_FAILED.name());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.TOPUP_APPROVE.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return false;
            }
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(addChargeRequest, LenderAssociationStages.TOPUP_APPROVE);
            log.info("TopupApprove response of TrillionLoans from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.TOPUP_APPROVE_SUCCESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.info("exception occurred while processing TopupApprove of TrillionLoans for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.TOPUP_APPROVE_FAILED.name());
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.TOPUP_APPROVE.name());
        commonService.manageApplicationState(lenderAssociationDetailsDto);
        return false;
    }

    private NBFCRequestDTO<?> getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();

        if (ObjectUtils.isEmpty(lendingApplication) || ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("LendingApplication/LendingApplicationLenderDetails not found for application {} with merchantId {}", lendingApplication.getId(), lendingApplication.getMerchantId());
            throw new RuntimeException("LendingApplication/LendingApplicationLenderDetails not found for application " + lendingApplication.getId());
        }

        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(TLTopupApproveRequestDto.builder()
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .approvedDate(LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")))
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating payload of TopupApprove of TrillionLoans for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
