package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.LenderForeclosureDetailsDTO;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.ForeClosureAmountResponse;
import com.bharatpe.lending.loanV3.dto.ForeclosureAmountRequest;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AbflForeclosureFetchService implements ILenderAssociationService<LenderForeclosureDetailsDTO> {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LenderGatewayFactory lenderGatewayFactory;

    @Override
    public LenderForeclosureDetailsDTO invoke(Long applicationId, Map<String, Object> args) {
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
        if (!lendingApplication.isPresent()) {
            log.info("no lending application record found for {}", applicationId);
            return LenderForeclosureDetailsDTO.buildEmptyResponse();
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails =
                lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, Status.ACTIVE.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("no lender assc record found for {}", applicationId);
            return LenderForeclosureDetailsDTO.buildEmptyResponse();
        }
        ForeclosureAmountRequest foreclosureAmountRequest = ForeclosureAmountRequest.builder()
                .applicationId(applicationId)
                .lender(lendingApplicationLenderDetails.getLender())
                .productName("LENDING")
                .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType()))
                .payload(ForeclosureAmountRequest.Payload.builder()
                        .accountId(lendingApplication.get().getExternalLoanId())
                        .loanNo(lendingApplicationLenderDetails.getLan())
                        .dealNo(lendingApplicationLenderDetails.getDealNo())
                        .build())
                .build();
        INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(lendingApplicationLenderDetails.getLender());
        ForeClosureAmountResponse foreClosureAmountResponse = apiGatewayV3.fetchDueForeclosureAmount(foreclosureAmountRequest);
        if (ObjectUtils.isEmpty(foreClosureAmountResponse) || !foreClosureAmountResponse.getSuccess() ||
            ObjectUtils.isEmpty(foreClosureAmountResponse.getData()) || ObjectUtils.isEmpty(foreClosureAmountResponse.getData().getData())) {
            log.info("error while processing foreclosure amount for {}", applicationId);
            return LenderForeclosureDetailsDTO.buildEmptyResponse();
        }
        Double amt = foreClosureAmountResponse.getData().getData().getNetReceivablePayable();

        return  LenderForeclosureDetailsDTO.builder()
                .foreclosureAmount(ObjectUtils.isEmpty(amt) ? 0d : amt)
                .principalOutstanding(foreClosureAmountResponse.getData().getData().getBalancePrincipal())
                .build();
    }
}
