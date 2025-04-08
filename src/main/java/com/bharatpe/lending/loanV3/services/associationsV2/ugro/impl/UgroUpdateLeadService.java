package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroCreateLeadRequest;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroUpdateLeadRequest;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroUpdateLeadResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.ugro.validations.UgroPayloadValidation;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.InvokeCreateLeadAndDocUploadWrapperService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class UgroUpdateLeadService {
    @Autowired
    KycUtils kycUtils;

    @Autowired
    UgroPayloadValidation payloadValidation;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UgroConfig ugroConfig;

    @Autowired
    ConverterUtils converterUtils;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    UgroLeadService ugroLeadService;

    @Transactional
    public boolean invokeUpdateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("UGRO: application id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            if (InvokeCreateLeadAndDocUploadWrapperService.kycDataNeeded(LenderAssociationStages.UPDATE_LEAD.name()) && ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            if (payloadValidation.isInvalidCreateLeadCKycData(lenderAssociationDetailsDto.getCKycResponseDto())) {
                log.error("UGRO: invalid response from downstream api for update lead : {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);
                return false;
            }

            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.UPDATE_LEAD.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.UPDATE_LEAD_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            NBFCRequestDTO<?> updateLeadPayload = getUpdateLeadPayload(lenderAssociationDetailsDto);
            if (ObjectUtils.isEmpty(updateLeadPayload)) {
                log.info("UGRO: error in update lead payload for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);
                return false;
            }

            NBFCResponseDTO<?> initialResponse = lenderAPIGateway.invokeStage(updateLeadPayload, LenderAssociationStages.UPDATE_LEAD);
            if (!ObjectUtils.isEmpty(initialResponse) && initialResponse.getSuccess() && !ObjectUtils.isEmpty(initialResponse.getData())) {
                log.info("UGRO: updateLead request success for {}", lenderAssociationDetailsDto.getApplicationId());
               UgroUpdateLeadResponse updateLeadResponse = objectMapper.convertValue(initialResponse.getData(), UgroUpdateLeadResponse.class);

                if (!ObjectUtils.isEmpty(updateLeadResponse) && !ObjectUtils.isEmpty(updateLeadResponse.getLeadId())
                        && lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId().equalsIgnoreCase(updateLeadResponse.getLeadId())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.UPDATE_LEAD_COMPLETED.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.info("UGRO: exception occurred while processing update lead for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);
        return false;
    }

    private NBFCRequestDTO<?> getUpdateLeadPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            CKycResponseDto cKycResponseDto = lenderAssociationDetailsRequest.getCKycResponseDto();
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || ObjectUtils.isEmpty(lendingApplication) || ObjectUtils.isEmpty(cKycResponseDto) || ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
                throw new RuntimeException("UGRO: LA/LALD/CKyc/LRVS not found for application " + lendingApplication.getId());
            }

            CKycResponseDto gstResponseDto = kycUtils.getGstData(lenderAssociationDetailsRequest.getMerchantId());

            UgroCreateLeadRequest.ProfileData profileData = ugroLeadService.getProfileData(lendingApplication, lendingApplicationLenderDetails, cKycResponseDto, gstResponseDto, lendingRiskVariablesSnapshot);
            profileData.setMobile(null); // We are setting these values as null because update lead API doesn't allow to edit them
            profileData.setPanNumber(null);

            UgroUpdateLeadRequest ugroUpdateLeadRequest = UgroUpdateLeadRequest.builder()
                    .id(lendingApplicationLenderDetails.getLeadId())
                    .product(ugroConfig.getProduct())
                    .profileData(profileData)
                    .acquisitionPlatformData(ugroLeadService.getAcquisitionPlatformData(lendingRiskVariablesSnapshot))
                    .udyamRegistrationFields(ugroLeadService.getUdyamRegistrationFields(lendingApplication, cKycResponseDto, gstResponseDto))
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(ugroUpdateLeadRequest).build();
        } catch (Exception e) {
            log.info("UGRO: Exception in creating payload of update lead for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
