package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV2.dto.AgreementResponse;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
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

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    @Lazy
    LendingApplicationServiceV2 lendingApplicationServiceV2;

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
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(lendingApplication.getId(), lendingApplication.getLender());

        AgreementStateDTO agreementResponseV3 = AgreementStateDTO.builder()
                .applicationId(lendingApplication.getId())
                .lender(lendingApplication.getLender())
                .loanAmount(lendingApplication.getLoanAmount())
                .interestRate(lendingApplication.getInterestRate())
                .annualRoi(getAnnualRoi(lendingApplicationLenderDetails, lendingApplication))
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
        if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))agreementResponseV3.setTopup(true);

        return new LendingStateDTO<>(agreementResponseV3 , LendingViewStates.AGREEMENT_PAGE, LendingViewStates.AGREEMENT_PAGE);
    }

    public Double getAnnualRoi(LendingApplicationLenderDetails lendingApplicationLenderDetails, LendingApplication lendingApplication){

        // for old lenders
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
            lendingApplicationLenderDetails.setApplicationId(lendingApplication.getId());
            lendingApplicationLenderDetails.setLender(lendingApplication.getLender());
            lendingApplicationLenderDetails.setStatus(Status.ACTIVE.name());
            lendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.OLD_MODEL.name());
            lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.OLD_MODEL.name());
            lendingApplicationLenderDetails.setStage(LenderAssociationStages.COMPLETED.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        }

        if(lendingApplicationLenderDetails.getAnnualRoi() == null){

            DecimalFormat df = new DecimalFormat("#.##");
            df.setRoundingMode(RoundingMode.DOWN);

            Double annualRoi = Double.valueOf(df.format(
                    lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getLoanAmount(),
                            LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().getNoOfEdiDaysInAWeek(), lendingApplication.getLender())));

            lendingApplicationLenderDetails.setAnnualRoi(annualRoi);
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);

            log.info("Calculated AnnualRoi {}", annualRoi);

            return annualRoi;

        }

        return lendingApplicationLenderDetails.getAnnualRoi();
    }
}
