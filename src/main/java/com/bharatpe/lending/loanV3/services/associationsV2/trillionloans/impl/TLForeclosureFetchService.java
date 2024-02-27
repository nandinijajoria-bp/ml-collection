package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.dto.piramal.PiramalGetLoanResponseDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.LoanDetailsResponseDTO;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.gateway.NbfcLenderGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;


import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class TLForeclosureFetchService implements ILenderAssociationService<Double> {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    TrillionRepaymentService trillionRepaymentService;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Value("${nbfc.baseurl.v3.api:https://api-nbfc-uat.bharatpe.in/}")
    String nbfcBaseUrl;

    @Value("${nbfc.baseurl.v3.foreclosure:api/v3/lender/foreclosure-details}")
    String nbfcURI;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NbfcLenderGateway nbfcLenderGateway;

    @Override
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
        Double amount = 0d;
        LendingLedger lendingLedger = lendingLedgerDao.findByMerchantIdOrderByIdDesc(lendingApplication.get().getMerchantId());
        NBFCRequestDTO nbfcRequest = trillionRepaymentService.getForeclosureReceiptRequest(applicationId, lendingLedger);
        LoanDetailsResponseDTO loanDetailsResponseDTO = null;
        try {
            NbfcResponseDto nbfcResponseDto = nbfcLenderGateway.invoke(objectMapper.writeValueAsString(nbfcRequest), NbfcResponseDto.class,nbfcBaseUrl+nbfcURI);
            log.info("Successfully hit the api for foreclosure {}",nbfcResponseDto);

            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                loanDetailsResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()),LoanDetailsResponseDTO.class);
            }
        } catch (Exception e) {
            log.error("exception occurred while posting foreclosure amt to nbfc svc for {}",nbfcRequest, e);
        }

        if (ObjectUtils.isEmpty(loanDetailsResponseDTO)) {
            log.info("error while processing foreclosure amount for {}", applicationId);
            return 0d;
        }
        amount = loanDetailsResponseDTO.getNetForeclosureAmount();

        log.info("Amount fetched for application id {} is {}",applicationId, amount);
        return amount;
    }
}
