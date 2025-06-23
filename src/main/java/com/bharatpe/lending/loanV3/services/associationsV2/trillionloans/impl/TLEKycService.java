package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.config.TrillionLoansConfig;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLEKycRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLEkycStatusCheckRequestDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.*;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;

@Slf4j
@Service
public class TLEKycService {

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CommonService commonService;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    @Lazy
    TrillionLoansConfig trillionLoansConfig;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;


    public boolean invokeEKyc(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            int currentEKycRetryCount = ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getKycRetryCount()) ? 0 : lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getKycRetryCount();
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.EKYC.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            NBFCRequestDTO<?> eKycRequest = getEKycPayload(lenderAssociationDetailsDto);
            if (ObjectUtils.isEmpty(eKycRequest)) {
                log.info("error in eKyc payload for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.EKYC_FAILED);
                return false;
            }
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(eKycRequest, LenderAssociationStages.EKYC);
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                TLEKycResponseDto eKycResponse = objectMapper.convertValue(nbfcResponseDto.getData(), TLEKycResponseDto.class);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setNbfcKycAsyncId(eKycResponse.getUrl());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycMode(LenderAssociationStages.EKYC.name());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setDealId(eKycResponse.getKid());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_INITIATED.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
            if (currentEKycRetryCount < trillionLoansConfig.getEKycRetryCount()) {
                log.info("marking kycStatus EKYC_RETRY for application as eKyc initiation resulted in failure for  {}", lenderAssociationDetailsDto.getApplicationId());
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

    private NBFCRequestDTO<?> getEKycPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(TLEKycRequestDto.builder()
                            .clientId(lendingApplicationLenderDetails.getCccId())
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating EKyc payload of TrillionLoans for application {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public Boolean processEKycCallback(NBFCResponseDTO nbfcResponseDTO) {
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
            if (!Arrays.asList(LenderAssociationStatus.EKYC_IN_PROGRESS.name(), LenderAssociationStatus.EKYC_INITIATED.name()).contains(lendingApplicationLenderDetails.getKycStatus())) {
                log.info("eKyc status of {} application is not correct for applicationId {} for callback ", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }

            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId()).merchantId(lendingApplication.getMerchantId())
                    .lendingApplication(lendingApplication).lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .manageState(true).modifyLender(enableLenderChange)
                    .build();

            if (nbfcResponseDTO.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDTO.getData())) {
                TLEKycCallbackResponseDto eKycCallbackResponse = objectMapper.convertValue(nbfcResponseDTO.getData(), TLEKycCallbackResponseDto.class);
                if (!ObjectUtils.isEmpty(eKycCallbackResponse) && Arrays.asList("approval_pending", "approved", "success").contains(eKycCallbackResponse.getPayload().getKycRequest().getStatus())) {
                    NBFCRequestDTO<?> eKycStatusRequestDto = getEKycStatusCheckPayload(lendingApplication, lendingApplicationLenderDetails, false);
                    NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(eKycStatusRequestDto, LenderAssociationStages.EKYC_STATUS);
                    log.info("NBFC EKYC_STATUS response for applicationId {}: {}", lendingApplication.getId(), nbfcResponseDto);
                    if (nbfcResponseDto != null && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                        TLEKycStatusCheckResponseDto ekycStatusCheckResponseDto = objectMapper.convertValue(nbfcResponseDto.getData(), TLEKycStatusCheckResponseDto.class);
                        if (processEKycStatusResponse(ekycStatusCheckResponseDto, lendingApplication, lenderAssociationDetailsRequest))
                            return true;
                    }
                }
            }
            int currentEKycRetryCount = ObjectUtils.isEmpty(lendingApplicationLenderDetails.getKycRetryCount()) ? 0 : lendingApplicationLenderDetails.getKycRetryCount();
            if (currentEKycRetryCount < trillionLoansConfig.getEKycRetryCount()) {
                log.info("marking kycStatus EKYC_RETRY for application as eKyc callback resulted in failure for  {}", lendingApplication.getId());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_RETRY.name());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycRetryCount(currentEKycRetryCount + 1);
                commonService.manageApplicationState(lenderAssociationDetailsRequest);
                return true;
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.EKYC_FAILED);
        } catch (Exception e) {
            log.error("exception while processing EKYC callback of Trillionloans for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    public boolean eKycStatusCheck(LendingApplication lendingApplication) {
        try {
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("request data is not correct {}", lendingApplication);
                return false;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), Lender.TRILLIONLOANS.name());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)
                    || !Arrays.asList(LenderAssociationStatus.EKYC_INITIATED.name(), LenderAssociationStatus.EKYC_IN_PROGRESS.name()).contains(lendingApplicationLenderDetails.getKycStatus())) {
                log.info("Kyc Status is not correct in lender details for eKyc status check for application {}", lendingApplication.getId());
                return false;
            }
            int currentEKycRetryCount = ObjectUtils.isEmpty(lendingApplicationLenderDetails.getKycRetryCount()) ? 0 : lendingApplicationLenderDetails.getKycRetryCount();
            if (currentEKycRetryCount >= trillionLoansConfig.getEKycRetryCount()) {
                log.info("skipping status check for last retry of eKyc of {} for {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            NBFCRequestDTO<?> payload = getEKycStatusCheckPayload(lendingApplication, lendingApplicationLenderDetails, true);
            NBFCResponseDTO<?> nbfcResponseDTO = lenderAPIGateway.invokeStage(payload, LenderAssociationStages.EKYC_STATUS);
            if (!ObjectUtils.isEmpty(nbfcResponseDTO) && nbfcResponseDTO.getSuccess()) {
                TLEKycStatusCheckResponseDto eKycStatusCheckResponseDto = objectMapper.convertValue(nbfcResponseDTO.getData(), TLEKycStatusCheckResponseDto.class);
                LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                        .applicationId(lendingApplication.getId()).merchantId(lendingApplication.getMerchantId())
                        .lendingApplication(lendingApplication).lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                        .manageState(true).modifyLender(enableLenderChange).build();
                if (processEKycStatusResponse(eKycStatusCheckResponseDto, lendingApplication, lenderAssociationDetailsRequest))
                    return true;
            }
            lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.EKYC_RETRY.name());
            lendingApplicationLenderDetails.setKycRetryCount(currentEKycRetryCount + 1);
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        } catch (Exception ex) {
            log.error("exception occurred while processing eKyc status check request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return false;
    }

    private boolean processEKycStatusResponse(TLEKycStatusCheckResponseDto ekycStatusCheckResponseDto, LendingApplication lendingApplication, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        TLEKycStatusCheckResponseDto.Action action = getDigilockerAction(ekycStatusCheckResponseDto);
        if (!ObjectUtils.isEmpty(action)) {
            CKycResponseDto cKycResponseDto = populateEKycDetails(action);
            kycUtils.savePoaDetailsForLenderKyc(lendingApplication.getId(), cKycResponseDto);
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_IN_PROGRESS.name());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.EKYC_COMPLETED.name());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycRetryCount(0);
            commonService.manageApplicationState(lenderAssociationDetailsRequest);
            return true;
        }
        return false;
    }

    private TLEKycStatusCheckResponseDto.Action getDigilockerAction(TLEKycStatusCheckResponseDto response) {
        if (ObjectUtils.isEmpty(response) || ObjectUtils.isEmpty(response.getActions())) {
            return null;
        }
        return response.getActions().stream()
                .filter(action -> action != null && "digilocker".equalsIgnoreCase(action.getType()) && Arrays.asList("approval_pending", "approved", "success").contains(action.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private CKycResponseDto populateEKycDetails(TLEKycStatusCheckResponseDto.Action action) {
        CKycResponseDto cKycDto = new CKycResponseDto();
        cKycDto.setAddress(action.getDetails().getAadhaar().getPermanentAddress());
        cKycDto.setDob(action.getDetails().getAadhaar().getDob());
        cKycDto.setCity(action.getDetails().getAadhaar().getPermanentAddressDetails().getDistrictOrCity());
        cKycDto.setName(action.getDetails().getAadhaar().getName());
        cKycDto.setPincode(action.getDetails().getAadhaar().getPermanentAddressDetails().getPincode());
        cKycDto.setState(action.getDetails().getAadhaar().getPermanentAddressDetails().getState());
        cKycDto.setGender(action.getDetails().getAadhaar().getGender());
        cKycDto.setAadharNumber(action.getDetails().getAadhaar().getIdNumber());
        return cKycDto;
    }

    private NBFCRequestDTO<?> getEKycStatusCheckPayload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, boolean isEKycStatusCheck) {
        return NBFCRequestDTO.builder()
                .lender(lendingApplication.getLender())
                .applicationId(lendingApplication.getId())
                .productName("LENDING")
                .payload(TLEkycStatusCheckRequestDto.builder()
                        .digiId(lendingApplicationLenderDetails.getDealId())
                        .leadId(lendingApplicationLenderDetails.getLeadId()).build())
                .identifier(new LinkedHashMap<String, Object>() {{
                    put("eKycStatusCheck", String.valueOf(isEKycStatusCheck));
                }})
                .build();
    }

}
