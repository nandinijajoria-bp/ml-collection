package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.*;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLEKYCRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLEkycStatusCheckRequestDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.*;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class TLEKYCService {
    @Autowired
    CommonService commonService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;


    @Value("${tl.eKyc.retry.count:1}")
    Integer tlEKycRetryCount;

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
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.EKYC.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            NBFCRequestDTO eKycRequest = getPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(eKycRequest)) {
                log.info("error in eKyc payload for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.EKYC_FAILED);
                return false;
            }

            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(eKycRequest, LenderAssociationStages.EKYC);

            log.info("eKyc response from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());

            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                TLEKYCResponseDto eKycResponse = objectMapper.readValue( objectMapper.writeValueAsString(nbfcResponseDto.getData()), TLEKYCResponseDto.class);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setNbfcKycAsyncId(eKycResponse.getUrl());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycMode(LenderAssociationStages.EKYC.name());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setDealId(eKycResponse.getKid());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_INITIATED.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
            Integer currentEKycRetryCount = ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getKycRetryCount()) ? 0 : lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getKycRetryCount();
            if(currentEKycRetryCount < tlEKycRetryCount) {
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

    public boolean processKycCallback(NBFCResponseDTO nbfcResponseDTO, Boolean isEkyc, Boolean isEKYCStatusCheck) {
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
            if(!Arrays.asList(LenderAssociationStatus.KYC_IN_PROGRESS.name(), LenderAssociationStatus.EKYC_INITIATED.name(), LenderAssociationStatus.EKYC_IN_PROGRESS.name(), LenderAssociationStatus.EKYC_RETRY.name()).contains(lendingApplicationLenderDetails.getKycStatus())) {
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

                if(isEKYCStatusCheck){
                    TLEKYCStatusCheckResponseDto tlckycStatusInfoResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), TLEKYCStatusCheckResponseDto.class);
                    TLEKYCStatusCheckResponseDto.Action action = getDigilockerAction(tlckycStatusInfoResponseDto);
                    if(ObjectUtils.isEmpty(action)){
                        log.error("Trillionloans actions are empty for application Id: {} so modifying lender or trying again", lendingApplication.getId());
                    }else {
                        CKycResponseDto cKycResponseDto = populateEkycDetails(action);
                        return updateKYCDataAndPushToNextStage(lenderAssociationDetailsRequest, lendingApplication.getId(), cKycResponseDto);
                    }
                } else if(isEkyc){

                    TLEkycCallbackResponseDto eKycCallbackResponse = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), TLEkycCallbackResponseDto.class);

                    if(!ObjectUtils.isEmpty(eKycCallbackResponse) && Arrays.asList("approval_pending", "approved", "success").contains(eKycCallbackResponse.getPayload().getKycRequest().getStatus())){

                        NBFCRequestDTO cKycStatusInfoRequest = getPayloadForEKycStatusInfo(lendingApplicationLenderDetails);

                        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(cKycStatusInfoRequest, LenderAssociationStages.EKYC_STATUS);

                        TLEKYCStatusCheckResponseDto tlckycStatusInfoResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), TLEKYCStatusCheckResponseDto.class);
                        TLEKYCStatusCheckResponseDto.Action action = getDigilockerAction(tlckycStatusInfoResponseDto);
                        if(ObjectUtils.isEmpty(action)){
                            log.error("Trillionloans actions are empty for application Id: {} so modifying lender or trying again", lendingApplication.getId());
                        } else {
                            CKycResponseDto cKycResponseDto = populateEkycDetails(action);
                            return updateKYCDataAndPushToNextStage(lenderAssociationDetailsRequest, lendingApplication.getId(), cKycResponseDto);
                        }
                    } else {
                        log.info("lender Trillions some of the fields are missing to set for kyc details for application Id: {} so modifying lender or trying again", lendingApplication.getId());
                    }
                } else {
                    TLCKycCallbackResponseDto cKycCallbackResponse = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), TLCKycCallbackResponseDto.class);

                    log.info("eKyc callback Response of Trillions for {} {}", nbfcResponseDTO.getApplicationId(), cKycCallbackResponse);
                    if(!ObjectUtils.isEmpty(cKycCallbackResponse) && "VERIFIED".equalsIgnoreCase(cKycCallbackResponse.getKycStatus())) {
                        NBFCRequestDTO cKycStatusInfoRequest = getPayloadForCKycStatusInfo(lendingApplicationLenderDetails);

                        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(cKycStatusInfoRequest, LenderAssociationStages.CKYC_INFO);

                        TLCKYCStatusInfoResponseDto tlckycStatusInfoResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), TLCKYCStatusInfoResponseDto.class);

                        CKycResponseDto cKycResponseDto = populateCkycDetails(tlckycStatusInfoResponseDto);
                        return updateKYCDataAndPushToNextStage(lenderAssociationDetailsRequest, lendingApplication.getId(), cKycResponseDto);
                    }
                }
            }
            Integer currentEKycRetryCount = ObjectUtils.isEmpty(lendingApplicationLenderDetails.getKycRetryCount()) ? 0 : lendingApplicationLenderDetails.getKycRetryCount();
            if(currentEKycRetryCount < tlEKycRetryCount) {
                log.info("marking kycStatus EKYC_RETRY, EKYC_PENDING for application callback, statuscheck resulted in failure for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(isEKYCStatusCheck || isEkyc ? LenderAssociationStatus.EKYC_RETRY.name() :  LenderAssociationStatus.EKYC_PENDING.name());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycRetryCount(isEKYCStatusCheck || isEkyc ? currentEKycRetryCount + 1 :  currentEKycRetryCount);
                commonService.manageApplicationState(lenderAssociationDetailsRequest);
                return true;
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.EKYC_FAILED);
        } catch (Exception e) {
            log.error("exception while processing eKyc callback of Trillion for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return true;
    }

    public TLEKYCStatusCheckResponseDto.Action getDigilockerAction(TLEKYCStatusCheckResponseDto response) {
        if (response == null || response.getActions() == null) {
            return null;
        }
        return response.getActions().stream()
                .filter(action -> action != null && "digilocker".equalsIgnoreCase(action.getType()) && Arrays.asList("approval_pending", "approved", "success").contains(action.getStatus()))
                .findFirst()
                .orElse(null);
    }


    private Boolean updateKYCDataAndPushToNextStage(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest, Long applicationId, CKycResponseDto cKycResponseDto){

        kycUtils.savePoaDetailsForLenderKyc(applicationId, cKycResponseDto);
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_COMPLETED.name());
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.EKYC_COMPLETED.name());
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycRetryCount(0);
        commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);

        log.info("Trillion EKyc is completed for applicationId {}", applicationId);
        return true;

    }

    public boolean eKycStatusCheck(LendingApplication lendingApplication) {
        try {
            if (ObjectUtils.isEmpty(lendingApplication) ) {
                log.info("request data is not correct {}", lendingApplication);
                return false;
            }
            LendingApplicationLenderDetails existingLendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), Lender.TRILLIONLOANS.name());
            if (ObjectUtils.isEmpty(existingLendingApplicationLenderDetails)
                    || !Arrays.asList(LenderAssociationStatus.EKYC_INITIATED.name(),LenderAssociationStatus.EKYC_IN_PROGRESS.name()).contains(existingLendingApplicationLenderDetails.getKycStatus())) {
                log.info("Kyc Status is not correct in lender details for eKyc status check for application {}", lendingApplication.getId());
                return false;
            }
            Integer currentEKycRetryCount = ObjectUtils.isEmpty(existingLendingApplicationLenderDetails.getKycRetryCount()) ? 0 : existingLendingApplicationLenderDetails.getKycRetryCount();
            if(currentEKycRetryCount >= tlEKycRetryCount) {
                log.info("skipping status check for last retry of eKyc of {} for {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            NBFCRequestDTO payload = NBFCRequestDTO.builder()
                    .lender(lendingApplication.getLender())
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .payload(
                            TLEkycStatusCheckRequestDto.builder()
                                    .digiId(existingLendingApplicationLenderDetails.getDealId())
                                    .build())
                    .build();
            NBFCResponseDTO nbfcResponseDTO = lenderAPIGateway.invokeStage(payload, LenderAssociationStages.EKYC_STATUS);

            if(!ObjectUtils.isEmpty(nbfcResponseDTO) && nbfcResponseDTO.getSuccess()) {
                processKycCallback(nbfcResponseDTO, Boolean.FALSE, Boolean.TRUE);
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

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();

        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(TLEKYCRequestDto.builder()
                            .clientId(lendingApplicationLenderDetails.getCccId())
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating EKYC payload of TrillionLoans for application {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }


    private NBFCRequestDTO getPayloadForCKycStatusInfo(LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplicationLenderDetails.getApplicationId())
                    .lender(lendingApplicationLenderDetails.getLender())
                    .productName("LENDING")
                    .payload(TLEKYCRequestDto.builder()
                            .clientId(lendingApplicationLenderDetails.getCccId())
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating EKYC payload of TrillionLoans for application {} {} {}", lendingApplicationLenderDetails.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private NBFCRequestDTO getPayloadForEKycStatusInfo(LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplicationLenderDetails.getApplicationId())
                    .lender(lendingApplicationLenderDetails.getLender())
                    .productName("LENDING")
                    .identifier(new LinkedHashMap<String, Object>(){{
                        put("statusCheck", "Lending");
                    }})
                    .payload(TLEkycStatusCheckRequestDto.builder()
                            .digiId(lendingApplicationLenderDetails.getDealId())
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating EKYC payload of TrillionLoans for application {} {} {}", lendingApplicationLenderDetails.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
    private static CKycResponseDto populateEkycDetails(TLEKYCStatusCheckResponseDto.Action action) {

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

    private static CKycResponseDto populateCkycDetails(TLCKYCStatusInfoResponseDto tlckycStatusInfoResponseDto) {

        CKycResponseDto cKycDto = new CKycResponseDto();
        Optional<TLCKYCStatusInfoResponseDto.AddressInfo> permanentAddress =
                tlckycStatusInfoResponseDto.getAddressInfo().stream()
                        .filter(address -> "PERMANENT".equals(address.getAddressType()))
                        .findFirst();

        Optional<TLCKYCStatusInfoResponseDto.DocumentInfo> aadhaarDocumentInfo =
                tlckycStatusInfoResponseDto.getDocumentInfo().stream()
                        .filter(document -> "AADHAAR".equals(document.getDocumentType()))
                        .findFirst();

        cKycDto.setAddress(permanentAddress.map(address ->
                address.getAddressLine1().concat(address.getAddressLine2())
        ).orElse(null));

        cKycDto.setDob(DateTimeUtil.formatDate(tlckycStatusInfoResponseDto.getPersonalInfo().getDob(), "dd-MM-yyyy","dd/MM/yyyy"));
        cKycDto.setCity(permanentAddress.get().getCity());
        cKycDto.setName(tlckycStatusInfoResponseDto.getPersonalInfo().getName());
        cKycDto.setPincode(permanentAddress.get().getPincode());
        cKycDto.setState(permanentAddress.get().getState());
        cKycDto.setGender(tlckycStatusInfoResponseDto.getPersonalInfo().getGender());
        cKycDto.setAadharNumber(aadhaarDocumentInfo.get().getAadharId());
        cKycDto.setCareOf(tlckycStatusInfoResponseDto.getPersonalInfo().getFatherName());

        return cKycDto;

    }



}
