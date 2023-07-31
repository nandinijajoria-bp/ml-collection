package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.loanV2.dto.AgreementResponse;
import com.bharatpe.lending.loanV3.revamp.dto.*;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class AgreementStageDataService implements IStageDataService<AgreementStateDTO> {

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Override
    public LendingStateDTO<AgreementStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<AgreementStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        lendingStateDTO.setLendingViewStates(LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<AgreementStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {

        // get ApplicationId from frontEnd (mandatory)
        LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
        }

        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
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
                .ediModelModified(!ObjectUtils.isEmpty(lendingApplicationDetails) && Optional.ofNullable(lendingApplicationDetails.getEdiModelModified()).orElse(false))
                .repayment(AgreementResponse.Repayment.builder()
                        .principal(lendingApplication.getLoanAmount())
                        .interest(lendingApplication.getRepayment() - lendingApplication.getLoanAmount())
                        .total(lendingApplication.getRepayment())
                        .build())
                .accountDetails(loanUtil.getAccountDetails(lendingApplication.getMerchantId()))
                .enachBank(loanUtil.isEnachBank(lendingApplication.getMerchantId())).build();
        return new LendingStateDTO<>(agreementResponseV3 , LendingViewStates.AGREEMENT_PAGE, LendingViewStates.AGREEMENT_PAGE);
    }
}
