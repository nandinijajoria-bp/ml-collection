package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.dto.LoanInsuranceDTO;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV2.dto.AgreementResponse;
import com.bharatpe.lending.loanV2.service.InsuranceService;
import com.bharatpe.lending.loanV3.revamp.dto.AgreementStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Component
@Slf4j
public class AgreementStageDataService implements IStageDataService<AgreementStateDTO> {

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Autowired
    InsuranceService insuranceService;

    @Value("${udyam.registration.required.lenders:}")
    String udyamRegistrationRequiredLenders;

    private final List<String> udyamPendingStatus = Arrays.asList(LenderAssociationStatus.UDYAM_PENDING.name(), LenderAssociationStatus.UDYAM_REGISTRATION_PENDING.name());

    @Override
    public LendingStateDTO<AgreementStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        return fetchScopedData(scopeDataArgs);
    }

    @Override
    public LendingStateDTO<AgreementStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {

        // get ApplicationId from frontEnd (mandatory)
        LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
        }
        LoanInsuranceDTO insuranceDetails = insuranceService.fetchLenderInsurancePremiumDetails(lendingApplication);
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(lendingApplication.getId(), lendingApplication.getLender());
        AgreementStateDTO agreementResponseV3 = AgreementStateDTO.builder()
                .applicationId(lendingApplication.getId())
                .lender(lendingApplication.getLender())
                .loanAmount(lendingApplication.getLoanAmount())
                .interestRate(lendingApplication.getInterestRate())
                .arrangerFee(lendingApplication.getProcessingFee().intValue())
                .disbursalAmount(lendingApplication.getDisbursalAmount())
                .tenure(lendingApplication.getTenure())
                .ediAmount(lendingApplication.getEdi().intValue())
                .ediCount(lendingApplication.getPayableDays().intValue())
                .merchantId(scopeDataArgs.getMerchant().getId())
                .ediModelModified(!ObjectUtils.isEmpty(lendingApplicationDetails) && Optional.ofNullable(lendingApplicationDetails.getEdiModelModified()).orElse(false))
                .repayment(AgreementResponse.Repayment.builder()
                        .principal(lendingApplication.getLoanAmount())
                        .interest(lendingApplication.getRepayment() - lendingApplication.getLoanAmount())
                        .total(lendingApplication.getRepayment())
                        .build())
                .accountDetails(loanUtil.getAccountDetails(lendingApplication.getMerchantId()))
                .enachBank(loanUtil.isEnachBank(lendingApplication.getMerchantId()))
                .loanInsurances(insuranceDetails.getInsurances())
                .isInsured(insuranceDetails.isSelected())
                .externalLoanId(lendingApplication.getExternalLoanId())
                .build();

        if(ObjectUtils.isEmpty(lendingApplicationDetails)) {
            log.info("Lending Application Details not found for applicationId: {}", lendingApplication.getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(), LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }

        agreementResponseV3.setTopup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()));
        agreementResponseV3.setIsAadhaarAddressVerified(!ObjectUtils.isEmpty(lendingApplicationDetails.getCurrentAddressSameAsPermanentAddress()));
        agreementResponseV3.setLoanPurpose(Lender.PIRAMAL.name().equalsIgnoreCase(lendingApplication.getLender()) && ObjectUtils.isEmpty(lendingApplicationDetails.getLoanPurpose()));

        if(udyamRegistrationRequiredLenders.contains(lendingApplication.getLender())
           && udyamPendingStatus.contains(lendingApplicationLenderDetails.getDataUploadStatus())) {
           return new LendingStateDTO<>(agreementResponseV3 , LendingViewStates.UDYAM_REGISTRATION_PAGE, LendingViewStates.AGREEMENT_PAGE);
        }
        return new LendingStateDTO<>(agreementResponseV3 , LendingViewStates.KEY_FACTOR_STATEMENT_PAGE, LendingViewStates.AGREEMENT_PAGE);
    }
}
