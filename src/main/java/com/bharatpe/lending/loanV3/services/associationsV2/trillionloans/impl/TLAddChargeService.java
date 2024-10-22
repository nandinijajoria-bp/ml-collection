package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLAddChargeRequestDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.service.MerchantLoansService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class TLAddChargeService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    MerchantLoansService merchantLoansService;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Transactional
    public boolean invokeAddCharge(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ADD_CHARGE_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            NBFCRequestDTO<?> addChargeRequest = getPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(addChargeRequest)) {
                log.info("error in AddCharge payload of TrillionLoans for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ADD_CHARGE_FAILED.name());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.ADD_CHARGE.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return false;
            }
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(addChargeRequest, LenderAssociationStages.ADD_CHARGE);
            log.info("AddCharge response of TrillionLoans from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ADD_CHARGE_SUCCESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.info("exception occurred while processing AddCharge of TrillionLoans for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ADD_CHARGE_FAILED.name());
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.ADD_CHARGE.name());
        commonService.manageApplicationState(lenderAssociationDetailsDto);
        return false;
    }

    private NBFCRequestDTO<?> getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) throws Exception {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        if (ObjectUtils.isEmpty(lendingApplication) || ObjectUtils.isEmpty(lendingApplicationLenderDetails) || ObjectUtils.isEmpty(lendingApplicationDetails)) {
            log.info("LendingApplication/LendingApplicationLenderDetails/LendingApplicationDetails not found for application {} with merchantId {}", lendingApplication.getId(), lendingApplication.getMerchantId());
            throw new RuntimeException("LendingApplication/LendingApplicationLenderDetails/LendingApplicationDetails not found for application " + lendingApplication.getId());
        }

        LendingPaymentSchedule currActiveLendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(lendingApplicationDetails.getPrevAppId());
        if (ObjectUtils.isEmpty(currActiveLendingPaymentSchedule)) {
            log.info("CurrentActiveLendingPaymentSchedule not found for application {}", lendingApplicationDetails.getPrevAppId());
            throw new RuntimeException("CurrentActiveLendingPaymentSchedule not found for application " + lendingApplicationDetails.getPrevAppId());
        }

        double foreclosureAmount = merchantLoansService.getPreviousLoanAmount(currActiveLendingPaymentSchedule);
        if (foreclosureAmount <= 0) {
            log.error("foreclosureAmount <= 0 for merchantId {}, loan : {}", currActiveLendingPaymentSchedule.getMerchantId(), currActiveLendingPaymentSchedule.getId());
            throw new Exception("Unable to fetch foreclosure amount for LPS: " + currActiveLendingPaymentSchedule);
        }

        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(TLAddChargeRequestDto.builder()
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .chargeId(7)
                            .amount(foreclosureAmount)
                            .isAmountNonEditable(Boolean.FALSE)
                            .isMandatory(Boolean.FALSE)
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating payload of AddCharge of TrillionLoans for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
