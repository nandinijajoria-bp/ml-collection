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
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class AbflDigiSignService {
    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LenderGatewayFactory lenderGatewayFactory;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    MerchantService merchantService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DocUploadUtils docUploadUtils;

    public AbflDigiSignResponseDTO invokeDigiSign(Long applicationId, LendingApplication lendingApplication) {
        try {
            log.info("DIGI sign: initiating for abfl lender for applicationId: {}", applicationId);
            AbflDigiSignRequestDTO digiSignRequest = createPayload(lendingApplication);
            INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(digiSignRequest.getLender());
            AbflDigiSignResponseDTO digiSignResponseDTO = apiGatewayV3.invokeDigiSign(digiSignRequest);
            if (ObjectUtils.isEmpty(digiSignResponseDTO)
                    || !digiSignResponseDTO.getSuccess()
                    || ObjectUtils.isEmpty(digiSignResponseDTO.getData())
                    || !StatusCheckResponse.SUCCESS.name().equalsIgnoreCase(digiSignResponseDTO.getData().getResponseStatus())
                    || (!ObjectUtils.isEmpty(digiSignResponseDTO.getData())
                    && !ObjectUtils.isEmpty(digiSignResponseDTO.getData().getData())
                    && !ObjectUtils.isEmpty(digiSignResponseDTO.getData().getData().getSuccess_message())
                    && !ObjectUtils.isEmpty(digiSignResponseDTO.getData().getData().getSuccess_message().getStatus()))
            ) {
                log.error("DIGI sign: Unable to initiate digiSign request at lender for : {}", applicationId);
                LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
                if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                    lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.DIGI_SIGN_DOC_UPLOAD_INIT_FAILED.name());
                    lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                }else {
                    log.info("No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                }
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
                        .mobile_number(merchantDetailsDto.getMerchantDetail().getMobile().substring(2))
                        .build())
                .build();
    }

    public Boolean processDigitalSignCallback(NBFCResponseDTO nbfcResponseDTO) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId())).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
                return false;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
                log.info("No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            if (Objects.nonNull(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                AbflDigiSignStatusResponseDTO digitalSignCallbackResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), AbflDigiSignStatusResponseDTO.class);
                log.info("DIGI sign: callback Response for id {} {}", nbfcResponseDTO.getApplicationId(), digitalSignCallbackResponseDto);
                if (!ObjectUtils.isEmpty(digitalSignCallbackResponseDto) && !ObjectUtils.isEmpty(digitalSignCallbackResponseDto.getData()) && !ObjectUtils.isEmpty(digitalSignCallbackResponseDto.getData().getShortUrl())) {
                    lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.DIGI_SIGN_DOC_UPLOAD_COMPLETED.name());
                    lendingApplicationLenderDetails.setESignedSanc(Boolean.TRUE);
                    lendingApplicationLenderDetails.setESignedKfs(Boolean.TRUE);
                    docUploadUtils.saveESignedDocs(lendingApplication.getId(), digitalSignCallbackResponseDto.getData().getShortUrl(), digitalSignCallbackResponseDto.getData().getShortUrl());
                    return true;
                }
            }
            lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.DIGI_SIGN_DOC_UPLOAD_HARD_FAILED.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        } catch (Exception e) {
            log.error("exception while processing KYC callback of Muthoot for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

}