package com.bharatpe.lending.loanV3.services.associations.piramal;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.dto.piramal.PiramalGetLoanResponseDto;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.PiramalGetLoanDetails;
import com.bharatpe.lending.loanV3.services.gateway.PiramalApiGateway;
import com.bharatpe.lending.loanV3.services.gateway.piramal.ILenderGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class PiramalForeclosureFetchService implements ILenderAssociationService<Double>{

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    PiramalApiGateway piramalApiGateway;

    @Autowired
    PiramalGetLoanDetails piramalGetLoanDetails;

    public Double invoke(Long applicationId, Map<String, Object> args) {
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
        if (!lendingApplication.isPresent()) {
            log.info("no lending application record found for {}", applicationId);
            return 0d;
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails =
                lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, Status.ACTIVE.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("no lender assc record found for {}", applicationId);
            return 0d;
        }
        PiramalGetLoanResponseDto piramalGetLoanResponseDto = piramalGetLoanDetails.getLoanDetails(applicationId);
        if (ObjectUtils.isEmpty(piramalGetLoanResponseDto)) {
            log.info("error while processing foreclosure amount for {}", applicationId);
            return 0d;
        }
        Double feeBalanceOfAllfee = 0d;
        for (PiramalGetLoanResponseDto.Fee fee : piramalGetLoanResponseDto.getFeeList()){
            feeBalanceOfAllfee+=fee.getFeeBalance();
        }
        Double amt = piramalGetLoanResponseDto.getTotalOutstandingPrincipal()+piramalGetLoanResponseDto.getAccruedInterest()
                        +feeBalanceOfAllfee-piramalGetLoanResponseDto.getAdvancePaymentAmount();
        log.info("Amount fetched for application id {} is{}",applicationId,amt);
        return amt;
    }

}
