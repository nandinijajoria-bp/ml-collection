package com.bharatpe.lending.loanV3.revamp.services;


import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.response.EnachHistory;
import com.bharatpe.lending.loanV3.revamp.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class EnachService {



    @Autowired
    private EnachHandler enachHandler;
    public List<EnachHistory> getNachHisory(BasicDetailsDto merchant,Long applicationId){
        List<EnachHistory> enachHistoryList=new ArrayList<>();
        try {
            List<BharatPeEnachResponseDTO> enachResponseDTOList = enachHandler.getEnachListByMerchantIdAndApplicationIdV2(merchant.getId(),applicationId);
            for (BharatPeEnachResponseDTO enachResponse : enachResponseDTOList) {
                EnachHistory enachHistory = new EnachHistory();
                enachHistory.setNachId(StringUtils.isEmpty(enachResponse.getMandateId())? "N/A":enachResponse.getMandateId());
                enachHistory.setStatus(Objects.nonNull(enachResponse.getSuccess()) && enachResponse.getSuccess() ? LoanDetailsConstant.STATUS_SUCCESS : LoanDetailsConstant.STATUS_FAILED);
                enachHistory.setNachDate(DateUtils.formatDate_DD_MMM_YY_HH_mm_a(enachResponse.getCreatedAt())+LoanDetailsConstant.PIPE_DELIMITER+DateUtils.formatDate_HH_mm_a(enachResponse.getCreatedAt()));
                enachHistory.setMessage(enachResponse.getMessage());
                enachHistoryList.add(enachHistory);
            }
            return enachHistoryList;
        }
        catch (Exception e){
            log.error("Exception in fetching enach history for merchantId:{},exception:{}",merchant.getId(),e);
            return enachHistoryList;
        }
    }
}
