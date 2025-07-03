package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dto.LenderForeclosureDetailsDTO;
import com.bharatpe.lending.loanV3.config.TrillionLoansConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.LoanDetailsResponseDTO;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.gateway.NbfcLenderGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;


import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class TLForeclosureFetchService implements ILenderAssociationService<LenderForeclosureDetailsDTO> {

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

    @Autowired
    TrillionLoansConfig trillionLoansConfig;

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
        Double amount = 0d;
        LendingLedger lendingLedger = lendingLedgerDao.findByMerchantIdOrderByIdDesc(lendingApplication.get().getMerchantId());
        NBFCRequestDTO nbfcRequest = trillionRepaymentService.getForeclosureReceiptRequest(applicationId, lendingLedger);
        LoanDetailsResponseDTO loanDetailsResponseDTO = null;
        try {
            NbfcResponseDto nbfcResponseDto = nbfcLenderGateway.invoke(objectMapper.writeValueAsString(nbfcRequest), NbfcResponseDto.class,nbfcBaseUrl+nbfcURI, trillionLoansConfig.getForeclosureDetailsTimeoutThreshold());
            log.info("Successfully hit the api for foreclosure {}",nbfcResponseDto);

            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                loanDetailsResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()),LoanDetailsResponseDTO.class);
            }
        } catch (Exception e) {
            log.error("exception occurred while posting foreclosure amt to nbfc svc for {}",nbfcRequest, e);
        }

        if (ObjectUtils.isEmpty(loanDetailsResponseDTO)) {
            log.info("error while processing foreclosure amount for {}", applicationId);
            return LenderForeclosureDetailsDTO.buildEmptyResponse();
        }
        amount = loanDetailsResponseDTO.getNetForeclosureAmount();

        log.info("Amount fetched for application id {} is {}",applicationId, amount);
        return LenderForeclosureDetailsDTO.builder()
                .foreclosureAmount(amount)
                .build();
    }
}
