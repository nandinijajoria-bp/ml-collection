package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.*;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.piramal.ILenderGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class EKycService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderGateway lenderGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Lazy
    @Autowired
    KycUtils kycUtils;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Lazy
    @Autowired
    PiramalDocumentUploadService piramalDocumentUploadService;

    @Lazy
    @Autowired
    NbfcUtils nbfcUtils;

    @Value("${eKyc.redirection.url:https://easy-loans-v2.bharatpe.in/_kyc_callback.html}")
    String eKycRedirectionUrl;

    @Value("${piramal.ekyc.retry.count:2}")
    Integer piramalEKycRetryCount;

    @Transactional
    public boolean invokeEKyc(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            if(ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getPanData(lenderAssociationDetailsDto.getLendingApplication().getMerchantId()));
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.PiramalAssociationStages.EKYC.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            NbfcRequestDto eKycRequest = getPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(eKycRequest)) {
                log.info("error in eKyc payload for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.EKYC_FAILED);
                return false;
            }
            NbfcResponseDto nbfcResponseDto = lenderGateway.invokeStage(eKycRequest, LenderAssociationStages.PiramalAssociationStages.EKYC);
            log.info("eKyc response from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                PEKycResponseDTO eKycResponse = objectMapper.readValue( objectMapper.writeValueAsString(nbfcResponseDto.getData()), PEKycResponseDTO.class);
                if(!ObjectUtils.isEmpty(eKycResponse) && "INITIATED".equalsIgnoreCase(eKycResponse.getKycStatus())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setNbfcKycAsyncId(eKycResponse.getDigilockerUrl());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycMode(LenderAssociationStages.EKYC.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_INITIATED.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
            Integer currentEKycRetryCount = ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getKycRetryCount()) ? 0 : lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getKycRetryCount();
            if(currentEKycRetryCount < piramalEKycRetryCount) {
                log.info("marking kycStatus EKYC_RETRY for application as eKyc initiation resulted in failure for  {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_RETRY.name());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycRetryCount(currentEKycRetryCount + 1);
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return false;
            }
        } catch (Exception e) {
            log.info("exception occurred while processing eKyc {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.EKYC_FAILED);
        return false;
    }

    private NbfcRequestDto getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        try {
            NbfcRequestDto nbfcRequestDto = NbfcRequestDto.builder()
                    .applicationId(lenderAssociationDetailsRequest.getLendingApplication().getId())
                    .lender(lenderAssociationDetailsRequest.getLendingApplication().getLender())
                    .productName("LENDING")
                    .payload(PEKycRequestDTO.builder()
                            .source("BRTPE")
                            .inputIdType("DIGILOCKER_AADHAAR_XML")
                            .phone(lenderAssociationDetailsRequest.getCKycResponseDto().getMobile())
                            .kycUrl(eKycRedirectionUrl)
                            .leadId(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId())
                            .applicantReferenceId(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getCccId())
                            .kycType("OKYC_DIGILOCKER")
                            .productId("BRTPE")
                            .build())
                    .build();
            return nbfcRequestDto;
        } catch (Exception e) {
            log.info("Exception in creating piramal eKyc payload for applicationId {} {}", lenderAssociationDetailsRequest.getLendingApplication().getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public boolean processEKycCallback(NBFCResponseDTO nbfcResponseDTO) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId())).orElse(null);
            if(ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
                return false;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
            if(ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
                log.info("No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            if(!Arrays.asList(LenderAssociationStatus.EKYC_IN_PROGRESS.name(), LenderAssociationStatus.EKYC_INITIATED.name()).contains(lendingApplicationLenderDetails.getKycStatus())) {
                log.info("eKyc status of {} application is not correct for applicationId {} for callback ", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .merchantId(lendingApplication.getMerchantId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .manageState(true)
                    .modifyLender(true)
                    .build();
            if (nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {
                PEKycCallbackResponseDTO eKycCallbackResponse = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), PEKycCallbackResponseDTO.class);
                log.info("eKyc callback Response of Piramal for {} {}", nbfcResponseDTO.getApplicationId(), eKycCallbackResponse);
                if(!ObjectUtils.isEmpty(eKycCallbackResponse) && "SOURCE_VERIFIED".equalsIgnoreCase(eKycCallbackResponse.getKycStatus())) {
                    CKycResponseDto cKycResponseDto = new CKycResponseDto();
                    cKycResponseDto.setAddress(eKycCallbackResponse.getAadharDetail().getCompleteAddress());
                    cKycResponseDto.setDob(eKycCallbackResponse.getAadharDetail().getDob());
                    cKycResponseDto.setCity(eKycCallbackResponse.getAadharDetail().getAddress().getCity());
                    cKycResponseDto.setName(eKycCallbackResponse.getAadharDetail().getName());
                    cKycResponseDto.setPincode(eKycCallbackResponse.getAadharDetail().getAddress().getPinCode());
                    cKycResponseDto.setState(eKycCallbackResponse.getAadharDetail().getAddress().getState());
                    cKycResponseDto.setGender(eKycCallbackResponse.getAadharDetail().getGender());
                    cKycResponseDto.setAadharNumber(eKycCallbackResponse.getAadharDetail().getMaskedAadhaarNumber());
                    cKycResponseDto.setSelfieBase64(eKycCallbackResponse.getSelfieDetail().getImageBase64());
                    cKycResponseDto.setCareOf(eKycCallbackResponse.getAadharDetail().getAddress().getCareOf());
                    kycUtils.savePoaDetailsForLenderKyc(lendingApplication.getId(), cKycResponseDto);
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_COMPLETED.name());
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.EKYC_COMPLETED.name());
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycRetryCount(0);
                    commonService.manageApplicationState(lenderAssociationDetailsRequest);
                    log.info("invoking selfie upload to piramal after EKyc success for applicationId {}", lendingApplication.getId());
                    if(piramalDocumentUploadService.invokeDocUpload(lenderAssociationDetailsRequest, LenderAssociationStages.PiramalAssociationStages.SELFIE_UPLOAD.name())) {
                        String currStage =  lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getStage();
                        LenderAssociationStages nextStage =
                                LenderAssociationStageFactory.getNextStage(Lender.valueOf(lenderAssociationDetailsRequest.getLendingApplication().getLender()),
                                        LenderAssociationStages.valueOf(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getStage()));
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setStage(nextStage.name());
                        commonService.manageApplicationState(lenderAssociationDetailsRequest);
                        nbfcUtils.pushApplicationToNextStage(lenderAssociationDetailsRequest.getApplicationId(),
                                lenderAssociationDetailsRequest.getLendingApplication().getLender(),
                                currStage,
                                Boolean.TRUE
                        );
                    }
                    return true;
                }
            }
            Integer currentEKycRetryCount = ObjectUtils.isEmpty(lendingApplicationLenderDetails.getKycRetryCount()) ? 0 : lendingApplicationLenderDetails.getKycRetryCount();
            if(currentEKycRetryCount < piramalEKycRetryCount) {
                log.info("marking kycStatus EKYC_RETRY for application as eKyc callback resulted in failure for  {}", lendingApplication.getId());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_RETRY.name());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycRetryCount(currentEKycRetryCount + 1);
                commonService.manageApplicationState(lenderAssociationDetailsRequest);
                return true;
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.EKYC_FAILED);
        } catch (Exception e) {
            log.error("exception while processing eKyc callback of Piramal for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return true;
    }

    public boolean eKycStatusCheck(LendingApplication lendingApplication) {
        try {
            if (ObjectUtils.isEmpty(lendingApplication) ) {
                log.info("request data is not correct {}", lendingApplication);
                return false;
            }
            LendingApplicationLenderDetails existingLendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), Lender.PIRAMAL.name());
            if (ObjectUtils.isEmpty(existingLendingApplicationLenderDetails)
                    || !Arrays.asList(LenderAssociationStatus.EKYC_IN_PROGRESS.name(), LenderAssociationStatus.EKYC_INITIATED.name()).contains(existingLendingApplicationLenderDetails.getKycStatus())) {
                log.info("Kyc Status is not correct in lender details for eKyc status check for application {}", lendingApplication.getId());
                return false;
            }
            Integer currentEKycRetryCount = ObjectUtils.isEmpty(existingLendingApplicationLenderDetails.getKycRetryCount()) ? 0 : existingLendingApplicationLenderDetails.getKycRetryCount();
            if(currentEKycRetryCount >= piramalEKycRetryCount) {
                log.info("skipping status check for last retry of eKyc of {} for {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            NbfcRequestDto payload = NbfcRequestDto.builder()
                    .lender(lendingApplication.getLender())
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .payload(
                            PEKycStatusCheckRequestDTO.builder()
                                    .leadId(existingLendingApplicationLenderDetails.getLeadId())
                                    .applicantReferenceId(existingLendingApplicationLenderDetails.getCccId())
                                    .kycType("OKYC_DIGILOCKER")
                                    .productId("BRTPE")
                                    .build())
                    .build();
            NbfcResponseDto nbfcResponseDTO = lenderGateway.invokeStage(payload, LenderAssociationStages.PiramalAssociationStages.EKYC_STATUS);
            if(!ObjectUtils.isEmpty(nbfcResponseDTO) && nbfcResponseDTO.getSuccess()) {
               NBFCResponseDTO callbackRequest = NBFCResponseDTO.builder()
                       .success(nbfcResponseDTO.getSuccess())
                       .applicationId(nbfcResponseDTO.getApplicationId())
                       .lender(nbfcResponseDTO.getLender())
                       .productName(nbfcResponseDTO.getProductName())
                       .data(nbfcResponseDTO.getData())
                       .build();
               processEKycCallback(callbackRequest);
               return true;
            }
            existingLendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.EKYC_RETRY.name());
            existingLendingApplicationLenderDetails.setKycRetryCount(currentEKycRetryCount + 1);
            lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);
        } catch (Exception ex) {
            log.error("exception occurred while processing eKyc status check request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return false;
    }
}
