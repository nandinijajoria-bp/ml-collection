package com.bharatpe.lending.ai.services.impl;

import com.bharatpe.lending.ai.dto.LoanApplicationDetail;
import com.bharatpe.lending.ai.dto.LoanDetailResponse;
import com.bharatpe.lending.ai.dto.StageDetail;
import com.bharatpe.lending.ai.enums.LoanApplicationStatus;
import com.bharatpe.lending.ai.services.AILedgerService;
import com.bharatpe.lending.ai.services.ILoanStageDetailBuilder;
import com.bharatpe.lending.ai.services.ILonaApplicationService;
import com.bharatpe.lending.ai.services.LoanStageDataBuilderFactory;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.query.dao.LendingApplicationDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import com.bharatpe.lending.exception.CustomLendingException;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanApplicationServiceImpl implements ILonaApplicationService {

    private final LendingApplicationDaoSlave lendingApplicationDaoSlave;
    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;
    private final LoanStageDataBuilderFactory loanStageDataBuilderFactory;
    private final AILedgerService aiLedgerService;

    @Override
    public LoanDetailResponse getLoanApplicationDetails(Long merchantId) {
        LendingApplicationSlave lendingApplication = lendingApplicationDaoSlave.findLastNonDeletedApplicationByMerchantId(merchantId);
        LoanDetailResponse response = new LoanDetailResponse();
        if(lendingApplication==null){
            // check for eligibility and build response
            return null;
        }
        LoanApplicationDetail loanApplicationDetail = new LoanApplicationDetail();
        loanApplicationDetail.setApplicationId(lendingApplication.getId());
        loanApplicationDetail.setStatus(getStatus(lendingApplication.getStatus()));
        loanApplicationDetail.setAmount(lendingApplication.getLoanAmount());
        loanApplicationDetail.setTenureInMonths(lendingApplication.getTenureInMonths());
        loanApplicationDetail.setMonthlyInterestRate(lendingApplication.getInterestRate());
        loanApplicationDetail.setTotalPayableAmount(lendingApplication.getRepayment());
        loanApplicationDetail.setEdiAmount(loanApplicationDetail.getEdiAmount());
        loanApplicationDetail.setCreatedAt(lendingApplication.getCreatedAt());
        loanApplicationDetail.setLender(lendingApplication.getLender());
        loanApplicationDetail.setDisbursalAmount(lendingApplication.getDisbursalAmount());
        loanApplicationDetail.setInterestRate(lendingApplication.getInterestRate());

        LendingApplicationDetails lad = lendingApplicationDetailsDao.findByApplicationId(lendingApplication.getId());
        if(lad==null){
            throw new CustomLendingException(HttpStatus.UNPROCESSABLE_ENTITY, "lad not found");
        }
        LendingViewStates stage = LendingViewStates.valueOf(lad.getApplicationViewState());
        loanApplicationDetail.setStage(stage);
        response.setCurrentLoan(loanApplicationDetail);
        response.setLendingApplication(lendingApplication);
        ILoanStageDetailBuilder stageDetailBuilder = loanStageDataBuilderFactory.getStageBuilder(stage, lendingApplication);
        StageDetail stageDetail = stageDetailBuilder.buildStageResponse(lendingApplication, lad);
        response.setStageDetail(stageDetail);
        return response;
    }

    private LoanApplicationStatus getStatus(String status) {
        if(status.equalsIgnoreCase("draft") || status.equalsIgnoreCase("pending_verification")){
            return LoanApplicationStatus.IN_PROGRESS;
        } else if(status.equalsIgnoreCase("approved")){
            return LoanApplicationStatus.APPROVED;
        } else if(status.equalsIgnoreCase("rejected")){
            return LoanApplicationStatus.REJECTED;
        } else if(status.equalsIgnoreCase("disbursed")){
            return LoanApplicationStatus.DISBURSED;
        } else if(status.equalsIgnoreCase("closed")){
            return LoanApplicationStatus.CLOSED;
        }
        throw new CustomLendingException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid status: " + status);
    }
}
