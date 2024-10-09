package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ModifiedOfferStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Optional;

@Service
@Slf4j
public class ModifiedOfferStageService implements IStageDataService<ModifiedOfferStateDTO> {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    @Lazy
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    @Lazy
    LoanDetailsV3Service loanDetailsV3Service;

    @Override
    public LendingStateDTO<ModifiedOfferStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {

        try{
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(scopeDataArgs.getApplicationId());
            if (!lendingApplication.isPresent()){
                log.error("Application not found  with ID:{}", scopeDataArgs.getApplicationId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
            }
            LendingApplication lendingApplication1 = lendingApplication.get();
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(lendingApplication1.getId(), lendingApplication1.getLender());
            if(ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("lending application lender details not found for applicationId: {}", lendingApplication1.getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(), LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
            }

            // calculate processing fee
            Double interestAmt = (lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() * (lendingApplication1.getInterestRate() * lendingApplication1.getTenureInMonths()) / 100);
            Long payableDays = lendingApplication1.getPayableDays();
            Double ediAmount = Math.ceil((lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() + interestAmt) / payableDays);
            double initialDisbursalAmountWithoutProcessingFee = lendingApplication1.getDisbursalAmount() + lendingApplication1.getProcessingFee();
            double  processingFeeRate = lendingApplication1.getProcessingFee()/initialDisbursalAmountWithoutProcessingFee;
            double processingFee = Math.ceil(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() * processingFeeRate);
            Double apr = lendingApplicationServiceV2.getApr(lendingApplication1.getMerchantId(), lendingApplication1.getId(), lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt(),
                           LenderOffDays.valueOf(lendingApplication1.getLender()).getEdiModel().getNoOfEdiDaysInAWeek(), lendingApplication1.getLender());
           ModifiedOfferStateDTO modifiedOfferStateDTO = ModifiedOfferStateDTO.builder().
                   loanOffer(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt())
                   .interestRate(lendingApplication1.getInterestRate())
                   .ediCount(payableDays.intValue())
                   .tenure(lendingApplication1.getTenure())
                   .apr(Double.valueOf(String.format("%.2f", apr)))
                   .ediAmount(ediAmount.intValue())
                   .arrangerFee((int)processingFee)
                   .applicationId(lendingApplication1.getId())
                   .build();
           loanDetailsV3Service.saveApplicationViewState(null, lendingApplication1.getId(), LendingViewStates.MODIFIED_OFFER);
           return new LendingStateDTO<>(modifiedOfferStateDTO, LendingViewStates.AGREEMENT_PAGE, LendingViewStates.MODIFIED_OFFER);
       } catch (Exception ex){
           log.error("Exception occurred while rendering data for merchant:{}", scopeDataArgs.getMerchant().getId());
           return null;
       }
    }

    @Override
    public LendingStateDTO<ModifiedOfferStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        return fetchScopedData(scopeDataArgs);
    }
}