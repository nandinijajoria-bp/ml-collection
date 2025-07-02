package com.bharatpe.lending.loanV3.services.associations.piramal;

import com.bharatpe.lending.dto.LenderForeclosureDetailsDTO;
import com.bharatpe.lending.loanV3.dto.piramal.PiramalGetLoanResponseDto;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.PiramalGetLoanDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Map;

@Component
@Slf4j
public class PiramalForeclosureFetchService implements ILenderAssociationService<LenderForeclosureDetailsDTO> {

    @Autowired
    PiramalGetLoanDetails piramalGetLoanDetails;

    public LenderForeclosureDetailsDTO invoke(Long applicationId, Map<String, Object> args) {
        PiramalGetLoanResponseDto piramalGetLoanResponseDto = piramalGetLoanDetails.getLoanDetails(applicationId);
        if (ObjectUtils.isEmpty(piramalGetLoanResponseDto)) {
            log.info("error while processing foreclosure amount for {}", applicationId);
            return LenderForeclosureDetailsDTO.buildEmptyResponse();
        }
        Double feeBalanceOfAllfee = 0d;
        if(!ObjectUtils.isEmpty(piramalGetLoanResponseDto.getFeeList())) {
            for (PiramalGetLoanResponseDto.Fee fee : piramalGetLoanResponseDto.getFeeList()) {
                feeBalanceOfAllfee += fee.getFeeBalance();
            }
        }
        Double amt = piramalGetLoanResponseDto.getTotalOutstandingPrincipal() + piramalGetLoanResponseDto.getAccruedInterest()
                + feeBalanceOfAllfee - piramalGetLoanResponseDto.getAdvancePaymentAmount();
        log.info("Amount fetched for application id {} is {}", applicationId, amt);
        return LenderForeclosureDetailsDTO.builder()
                .foreclosureAmount(amt)
                .principalOutstanding(piramalGetLoanResponseDto.getTotalOutstandingPrincipal().doubleValue())
                .build();

    }
}
