package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLTopupDataRequestDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.service.MerchantLoansService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class TLTopupDataService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    MerchantLoansService merchantLoansService;

    @Transactional
    public boolean invokeTopupData(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.TOPUP_DATA_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            NBFCRequestDTO<?> addChargeRequest = getPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(addChargeRequest)) {
                log.info("error in TopupData payload of TrillionLoans for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.TOPUP_DATA_FAILED.name());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.TOPUP_DATA.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return false;
            }
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(addChargeRequest, LenderAssociationStages.TOPUP_DATA);
            log.info("TopupData response of TrillionLoans from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.TOPUP_DATA_SUCCESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.info("exception occurred while processing TopupData of TrillionLoans for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.TOPUP_DATA_FAILED.name());
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.TOPUP_DATA.name());
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
        Optional<LendingApplication> currActiveLendingApplication = lendingApplicationDao.findById(currActiveLendingPaymentSchedule.getApplicationId());

        if (ObjectUtils.isEmpty(currActiveLendingPaymentSchedule) || !currActiveLendingApplication.isPresent()) {
            log.info("CurrentActiveLendingPaymentSchedule/CurrentActiveLendingApplication not found for application {}", lendingApplicationDetails.getPrevAppId());
            throw new RuntimeException("CurrentActiveLendingPaymentSchedule/CurrentActiveLendingApplication not found for application " + lendingApplicationDetails.getPrevAppId());
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
                    .payload(TLTopupDataRequestDto.builder()
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .sourcingChannel(currActiveLendingApplication.get().getLender())
                            .topupId(currActiveLendingApplication.get().getNbfcId())
                            .outstandingAmount(foreclosureAmount)
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating payload of TopupData of TrillionLoans for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
