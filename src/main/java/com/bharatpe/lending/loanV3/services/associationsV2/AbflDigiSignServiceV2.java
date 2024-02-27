package com.bharatpe.lending.loanV3.services.associationsV2;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Service
public class AbflDigiSignServiceV2 {
    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LenderGatewayFactory lenderGatewayFactory;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    MerchantService merchantService;

    public AbflDigiSignResponseDTO invokeDigiSign(Long applicationId) {
        try {
            Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
            if (!lendingApplicationOptional.isPresent()) {
                log.info("DIGI sign: no application found for id {}", applicationId);
                return null;
            }
            LendingApplication lendingApplication = lendingApplicationOptional.get();
            AbflDigiSignRequestDTO digiSignRequest = createPayload(lendingApplication);
            INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(digiSignRequest.getLender());
            AbflDigiSignResponseDTO digiSignResponseDTO = apiGatewayV3.invokeDigiSign(digiSignRequest);
            if (ObjectUtils.isEmpty(digiSignResponseDTO) || !digiSignResponseDTO.getSuccess() ||
                    ObjectUtils.isEmpty(digiSignResponseDTO.getData()) || !StatusCheckResponse.SUCCESS.name().equalsIgnoreCase(digiSignResponseDTO.getData().getResponseStatus())
            ) {
                log.error("DIGI sign: Unable to initiate digiSign request at lender for : {}", applicationId);
                return digiSignResponseDTO;
            }
            log.info("DIGI sign: successfully placed the digi sign request at lender for {}", applicationId);
            return digiSignResponseDTO;
        } catch (Exception ex) {
            log.error("DIGI sign: exception occurred while processing digiSign request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return null;
    }

    private AbflDigiSignRequestDTO createPayload(LendingApplication lendingApplication) {
        LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
        if(ObjectUtils.isEmpty(lendingKfs)) {
            log.error("DIGI sign: No documents found for applicationId {} for digiSign API", lendingApplication.getId());
            throw new RuntimeException("DIGI sign: Kfs and sanction letter not found for applicationId");
        }
        MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(lendingApplication.getMerchantId());
        if(ObjectUtils.isEmpty(merchantDetailsDto)) {
            log.error("DIGI sign: Error in fetching merchant details for merchantId: {}", lendingApplication.getMerchantId());
            throw new RuntimeException("DIGI sign: error in fetching merchant details for ABFL DigiSign API");
        }
        return AbflDigiSignRequestDTO.builder()
                .applicationId(lendingApplication.getId())
                .lender("ABFL")
                .productName("LENDING")
                .payload(AbflDigiSignRequestDTO.Payload.builder()
                        .accountId(lendingApplication.getExternalLoanId())
                        .key_fact_statement(lendingKfs.getKfsDocUrl())
                        .loan_agreement(lendingKfs.getSanctionLoanAgreementDocUrl())
                        .sanction_letter(lendingKfs.getSanctionLoanAgreementDocUrl())
                        .merged_pdf_flag(Boolean.FALSE)
                        .mobile_number(merchantDetailsDto.getMerchantDetail().getMobile())
                        .build())
                .build();
    }
}