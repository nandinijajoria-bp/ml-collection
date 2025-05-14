package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLKycRequestDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLKycCallbackResponseDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class TLKycService {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CommonService commonService;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Autowired
    private ILenderAPIGateway lenderAPIGateway;

    @Autowired
    private KycUtils kycUtils;

    private final List<String> kycCallbackValidStatus = Arrays.asList(LenderAssociationStatus.KYC_IN_PROGRESS.name(), LenderAssociationStatus.AADHAR_UPLOAD_IN_PROGRESS.name());

    public Boolean processKycCallback(NBFCResponseDTO nbfcResponseDTO) {
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
            if(!kycCallbackValidStatus.contains(lendingApplicationLenderDetails.getKycStatus())) {
                log.info("Kyc status {} not correct for kyc callback for applicationId {}", lendingApplicationLenderDetails.getKycStatus(), lendingApplication.getId());
                return false;
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .manageState(true)
                    .modifyLender(enableLenderChange)
                    .build();
            if (Objects.nonNull(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                TLKycCallbackResponseDto kycCallbackResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), TLKycCallbackResponseDto.class);
                log.info("KYC callback Response of TrillionLoans for {} {}", nbfcResponseDTO.getApplicationId(), kycCallbackResponseDto);
                if (!ObjectUtils.isEmpty(kycCallbackResponseDto)) {
                    if (kycCallbackResponseDto.getKycStatus().equalsIgnoreCase("VERIFIED")) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_COMPLETED.name());
                        commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                        return true;
                    }
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.KYC_FAILED);
        } catch (Exception e) {
            log.error("exception while processing KYC callback of Trillionloans for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    public Boolean invokeKyc(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        try {
            boolean isEligibleForLenderKyc = kycUtils.isELigibleForLenderKyc(Lender.TRILLIONLOANS.name(), lenderAssociationDetailsRequest.getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsRequest.getLendingApplication().getLoanType()));
            LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
            if (ObjectUtils.isEmpty(lenderAssociationDetailsRequest.getApplicationId()) || ObjectUtils.isEmpty(lendingApplication)) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsRequest.getMerchantId());
                return false;
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.KYC.name());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequest);

            NBFCRequestDTO<?> kycRequestPayload = NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.TRILLIONLOANS.name())
                    .payload(TLKycRequestDto.builder()
                            .leadId(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId())
                            .build())
                    .identifier(new LinkedHashMap<String, Object>(){{ put("isEligibleForLenderKyc", isEligibleForLenderKyc); }})
                    .build();

            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(kycRequestPayload, LenderAssociationStages.KYC);
            log.info("KYC response of Trillionloans from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequest.getApplicationId());
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(isEligibleForLenderKyc ? LenderAssociationStatus.EKYC_PENDING.name() : LenderAssociationStatus.KYC_IN_PROGRESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequest);
                return true;
            }
        } catch (Exception e) {
            log.error("exception occurred while KYC of Trillionloans for {} {} {}", lenderAssociationDetailsRequest.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.KYC_FAILED);
        return false;
    }

    public Boolean kycStatusCheck(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails())) {
                log.info("LendingApplication / LendingApplicationLenderDetails is empty for request {} {} ", lenderAssociationDetailsDto.getApplicationId(), lenderAssociationDetailsDto);
                return false;
            }
            NBFCRequestDTO<?> cKycInfoDto = NBFCRequestDTO.builder()
                    .applicationId(lenderAssociationDetailsDto.getLendingApplication().getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.TRILLIONLOANS.name())
                    .payload(TLKycRequestDto.builder()
                            .leadId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                            .build())
                    .build();
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(cKycInfoDto, LenderAssociationStages.KYC_STATUS_CHECK);
            return processKycCallback(nbfcResponseDto);
        } catch (Exception ex) {
            log.error("exception occurred while processing kyc status check request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_PENDING.name());
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycRetryCount(ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getKycRetryCount()) ? 0 : lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getKycRetryCount());
        commonService.manageApplicationState(lenderAssociationDetailsDto);
        return false;
    }
}
