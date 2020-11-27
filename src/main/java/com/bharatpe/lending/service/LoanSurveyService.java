package com.bharatpe.lending.service;

import java.util.ArrayList;
import java.util.List;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.common.dao.LoanSurveyDao;
import com.bharatpe.lending.common.entity.LoanSurvey;
import com.bharatpe.lending.dao.LendingApplicationDao;
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

    public LoanSurveyHeaderDto getSurveyMerchantHeader(Merchant merchant) {
        if(merchant == null) return null;
        StringBuilder amount = new StringBuilder();
        StringBuilder interestRate = new StringBuilder();
        LendingApplication loanApplication = lendingApplicationDao.findByMerchantIdAndStatus(merchant.getId(), "draft");
        if (loanApplication == null) {
            return LoanSurveyHeaderDto.builder()
                    .success(false)
                    .message("Loan application not found")
                    .build();
        }
        logger.info("Loan application for merchant {} with application id {}", merchant, loanApplication.getId());
        return LoanSurveyHeaderDto
            .builder()
            .success(Boolean.TRUE)
            .name(merchant.getMerchantName())
            .amount(amount.append(loanApplication.getLoanAmount()).toString())
            .interestRate(interestRate.append(loanApplication.getInterestRate()).toString())
            .build();
    }

    public LoanSurveyRequestDto submitSurvey(Merchant merchant, LoanSurveyRequestDto dto) {
        LoanSurveyRequestDto.LoanSurveyRequestDtoBuilder
            builder = LoanSurveyRequestDto.builder();

        LendingApplication lendingApplication = lendingApplicationDao.findByMerchantIdAndStatus(merchant.getId(), "draft");
        builder.success(false);
        if (lendingApplication == null) {
            return builder.message("Application Id is missing!")
                .build();
        }
        logger.info("loan survey for merchant {} with lending application id {}", merchant, lendingApplication.getId());

        List<LoanSurvey> loanSurveys = new ArrayList<>();

        for(LoanSurveyQuestionAnswerDto data:dto.getSurveyData()) {
            LoanSurvey loanSurvey = new LoanSurvey();
            loanSurvey.setApplicationId(lendingApplication.getId());
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
