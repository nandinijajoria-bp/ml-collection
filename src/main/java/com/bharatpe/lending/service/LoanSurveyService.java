package com.bharatpe.lending.service;

import java.util.ArrayList;
import java.util.List;

import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.LoanSurveyDao;
import com.bharatpe.lending.common.entity.LoanSurvey;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dto.LoanSurveyHeaderDto;
import com.bharatpe.lending.dto.LoanSurveyQuestionAnswerDto;
import com.bharatpe.lending.dto.LoanSurveyRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoanSurveyService {

    private final Logger logger = LoggerFactory.getLogger(LoanSurveyService.class);

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LoanSurveyDao loanSurveyDao;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    public LoanSurveyHeaderDto getSurveyMerchantHeader(Merchant merchant) {
        if(merchant == null) return null;
        logger.info("Fetching Loan Survey Data for merchant:{}", merchant.getId());
        String amount;
        String interestRate;
        LendingApplication loanApplication = lendingApplicationDao.findByMerchantIdAndStatus(merchant.getId(), "draft");
        EligibleLoan eligibleLoan = eligibleLoanDao.findMaxLoan(merchant.getId());
        if (loanApplication == null && eligibleLoan == null) {
            return LoanSurveyHeaderDto.builder()
                    .success(false)
                    .message("Loan application/Offer not found")
                    .build();
        }
        if (loanApplication != null) {
            amount = String.valueOf(loanApplication.getLoanAmount());
            interestRate = String.valueOf(loanApplication.getInterestRate());
        } else {
            LendingCategories lendingCategories = lendingCategoryDao.getByCategory(eligibleLoan.getCategory());
            amount = String.valueOf(eligibleLoan.getAmount());
            interestRate = String.valueOf(lendingCategories.getInterestRate());
        }
        return LoanSurveyHeaderDto
            .builder()
            .success(Boolean.TRUE)
            .name(merchant.getMerchantName())
            .amount(amount)
            .interestRate(interestRate)
            .build();
    }

    public LoanSurveyRequestDto submitSurvey(Merchant merchant, LoanSurveyRequestDto dto) {
        LoanSurveyRequestDto.LoanSurveyRequestDtoBuilder
            builder = LoanSurveyRequestDto.builder();

        LendingApplication lendingApplication = lendingApplicationDao.findByMerchantIdAndStatus(merchant.getId(), "draft");
        builder.success(false);

        List<LoanSurvey> loanSurveys = new ArrayList<>();
        for(LoanSurveyQuestionAnswerDto data:dto.getSurveyData()) {
            LoanSurvey loanSurvey = new LoanSurvey();
            loanSurvey.setApplicationId(lendingApplication != null ? lendingApplication.getId() : null);
            loanSurvey.setMerchantId(merchant.getId());
            if(data != null) {
                loanSurvey.setSelect(data.getSelect());
                loanSurvey.setQuestion(data.getQuestion());
                loanSurvey.setAnswer(data.getAnswer());
                loanSurvey.setPayload(data.getMetaData());
            }
            loanSurveys.add(loanSurvey);
        }

        loanSurveyDao.saveAll(loanSurveys);

        return builder.success(Boolean.TRUE)
            .deeplink("bharatpe://dynamic?key=loan")
            .message("Survey submit successfully")
            .build();
    }


}
