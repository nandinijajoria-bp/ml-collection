package com.bharatpe.lending.ai.services;

import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
public class LoanStageDataBuilderFactory {
    private final ILoanStageDetailBuilder lenderEvaluationStageDetailBuilder;
    private final ILoanStageDetailBuilder kycStageDetailBuilder;
    private final ILoanStageDetailBuilder voidStageDetailBuilder;

    private Map<LendingViewStates, ILoanStageDetailBuilder> stageDetailBuilderMap;

    public LoanStageDataBuilderFactory(
            @Qualifier("lenderEvaluationStageDetailBuilder") ILoanStageDetailBuilder lenderEvaluationStageDetailBuilder,
            @Qualifier("kycStageDetailBuilder") ILoanStageDetailBuilder kycStageDetailBuilder,
            @Qualifier("voidStageDetailBuilder") ILoanStageDetailBuilder voidStageDetailBuilder) {
        this.lenderEvaluationStageDetailBuilder = lenderEvaluationStageDetailBuilder;
        this.kycStageDetailBuilder = kycStageDetailBuilder;
        this.voidStageDetailBuilder = voidStageDetailBuilder;
    }

    @PostConstruct
    public void init(){
        stageDetailBuilderMap = new HashMap<>();
        stageDetailBuilderMap.put(LendingViewStates.LENDER_EVALUATION_PAGE, lenderEvaluationStageDetailBuilder);
        stageDetailBuilderMap.put(LendingViewStates.KYC_PAGE, kycStageDetailBuilder);
    }

    public ILoanStageDetailBuilder getStageBuilder(LendingViewStates state){
        return stageDetailBuilderMap.getOrDefault(state, voidStageDetailBuilder);
    }

}
