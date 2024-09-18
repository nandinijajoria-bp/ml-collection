package com.bharatpe.lending.loanV3.services.associationsV2.payu.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.payu.PayUAcceptOfferRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.payu.PayUBreRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.payu.PayULoanPreviewRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.*;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class PayUBreService {

    @Autowired
    CommonService commonService;

    @Autowired

    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Transactional
    public Boolean invokeBre(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.RISK_DECISION.name());
            if (Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails())
                    || Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
                return false;
            }
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
            NBFCRequestDTO breRequest = getPayload(lenderAssociationDetailsRequestDto);
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(breRequest, LenderAssociationStages.BRE);
            log.info("Bre response of PayU from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());

        } catch (Exception e) {
            log.error("error while invoking Bre of payU for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
        }
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        try {
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            if (ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
                throw new RuntimeException("Lending risk variable snapshot not found for application");
            }
            Double sixtyDaysTpv = ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getSummaryTpv()) ? 0D : lendingRiskVariablesSnapshot.getSummaryTpv() * 60;

            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(lendingApplication.getLender())
                    .payload(PayUBreRequestDTO.builder()
                            .applicationId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                            .tpv(sixtyDaysTpv)
                            .merchantCategory(lendingRiskVariablesSnapshot.getRiskGroup())
                            .merchantSegment(getRiskSegment(lendingRiskVariablesSnapshot.getRiskSegment().name()))
                            .pincodeColour(lendingRiskVariablesSnapshot.getPincodeColor().name())
                            .transactingDaysLast3Months(true)
                            .uniqueCustomerCountLast3Months(true)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while BRE request payload for createLead of PayU for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getRiskSegment(String riskSegment) {

        switch (riskSegment) {
            case "REGULAR_NTC":
            case "REGULAR_ETC":
                return "REGULAR";
            case "REPEAT":
                return "REPEAT";
            case "TOPUP":
                return "TOPUP";
            case "NTB_ETB_1":
            case "NTB_ETB_2":
            case "NTB_PURE":
                return "NTB";
            default:
                return "REGULAR";
        }
    }


    public Boolean processBreCallback(NBFCResponseDTO nbfcResponseDTO) {
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
            if(!LenderAssociationStatus.RISK_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getBreStatus())) {
                log.info("Bre status of {} application is not in progress for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .modifyLender(enableLenderChange)
                    .manageState(true)
                    .build();
            if (nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {
                PayUBreCallbackResponseDTO breCallbackResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), PayUBreCallbackResponseDTO.class);
                log.info("Bre callback Response of PayU for {} {}", nbfcResponseDTO.getApplicationId(), breCallbackResponseDTO);
                if(!ObjectUtils.isEmpty(breCallbackResponseDTO) && ("Approved".equalsIgnoreCase(breCallbackResponseDTO.getData().getEventDetails().getMessage()))) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
                        commonService.manageApplicationState(lenderAssociationDetailsRequest);
                        invokeAcceptOffer(lenderAssociationDetailsRequest, breCallbackResponseDTO);
                        return true;

                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.RISK_FAILED);
        } catch (Exception e) {
            log.error("exception while processing bre callback of PayU for {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    @Async("lenderPoolTaskExecutor")
    public boolean invokeAcceptOffer(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto, PayUBreCallbackResponseDTO callbackResponse) {
        try {
            if (Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails())
                    || Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.ACCEPT_OFFER_FAILED);
                return false;
            }
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
            NBFCRequestDTO acceptOfferPayload = getAcceptOfferPayload(lenderAssociationDetailsRequestDto);
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(acceptOfferPayload, LenderAssociationStages.ACCEPT_OFFER);
            log.info("Accept Offer response of PayU from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());

            if (Objects.nonNull(nbfcResponseDto.getData()) && nbfcResponseDto.getSuccess()) {

                PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), PayUCommonResponseDTO.class);

                PayUAcceptOfferResponseDTO acceptOfferResponseDTO =  objectMapper.convertValue(commonResponseDTO.getApiResponse(), PayUAcceptOfferResponseDTO.class);

                if ("SUCCESS".equalsIgnoreCase(commonResponseDTO.getApiStatus())) {

                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setOfferId(String.valueOf(acceptOfferResponseDTO.getOfferDetailList().get(0).getOfferId()));
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_SUCCESS.name());

                    if(Boolean.FALSE.equals(invokeLoanPreview(lenderAssociationDetailsRequestDto))){
                        return false;
                    }

                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequestDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("error while invoking Accept Offer of PayU for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.ACCEPT_OFFER_FAILED);
        return false;
    }

    private NBFCRequestDTO getAcceptOfferPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        try {
            PayUAcceptOfferRequestDTO requestData =  PayUAcceptOfferRequestDTO.builder()
                    .applicationId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                    .amount(lendingApplication.getLoanAmount())
                    .tenure(lendingApplication.getTenureInMonths())
                    .tenureMetric("Months")
                    .roi(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getAnnualRoi())
                    .roiType("REDUCING")
                    .charges(getCharges(lendingApplication))
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.PAYU.name())
                    .payload(requestData)
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while getting acceptOfferPayload request payload for createLead for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private List<PayUAcceptOfferRequestDTO.Charge> getCharges(LendingApplication lendingApplication){

        List<PayUAcceptOfferRequestDTO.Charge> chargesList = new ArrayList<>();
        PayUAcceptOfferRequestDTO.Charge charges = PayUAcceptOfferRequestDTO.Charge.builder()
                .type("PROCESSING_FEES")
                .value(Integer.valueOf( (int) Math.round(lendingApplication.getProcessingFee())))
                .valueType("FLAT")
                .isGstIncluded(true)
                .build();
        chargesList.add(charges);
        return chargesList;
    }

    private Boolean invokeLoanPreview(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {

        try {
            LendingApplication lendingApplication = lenderAssociationDetailsRequestDto.getLendingApplication();
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails();

            NBFCRequestDTO loanPreviewRequestDto = getLoanPreviewPayload(lendingApplication, lendingApplicationLenderDetails);
            if (Objects.isNull(loanPreviewRequestDto)) {
                log.info("error in loan preview payload of PayU for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.ACCEPT_OFFER_FAILED);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(loanPreviewRequestDto, LenderAssociationStages.LOAN_PREVIEW);
            log.info("loan preview response of PayU from nbfc: {} with applicationId: {}", nbfcResponseDto, lendingApplication.getId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("loan preview request of payU success for {}", lendingApplication.getId());

                PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), PayUCommonResponseDTO.class);

                PayULoanPreviewResponseDTO payULoanPreviewResponseDTO =  objectMapper.convertValue(commonResponseDTO.getApiResponse(), PayULoanPreviewResponseDTO.class);

                if ("SUCCESS".equalsIgnoreCase(commonResponseDTO.getApiStatus())) {

                    if(payULoanPreviewResponseDTO.getPeriods().size() < 3 || !Objects.equals(payULoanPreviewResponseDTO.getLoanTermsData().getPrincipal(), lendingApplication.getLoanAmount()) || !Objects.equals(payULoanPreviewResponseDTO.getPeriods().get(1).getTotalOriginalDueForPeriod(), payULoanPreviewResponseDTO.getPeriods().get(2).getTotalOriginalDueForPeriod()) || !Objects.equals(payULoanPreviewResponseDTO.getPeriods().get(1).getTotalOriginalDueForPeriod(), lendingApplication.getEdi()) || !Objects.equals(payULoanPreviewResponseDTO.getLoanTermsData().getNumberOfRepayments(), (double)lendingApplication.getPayableDays())){
                        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_FAILED.name());
                        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.ACCEPT_OFFER_FAILED);
                        return false;
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("error while pushing update address of PayU for  {} {} {}", lenderAssociationDetailsRequestDto.getLendingApplication().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.ACCEPT_OFFER_FAILED);
        return false;
    }

    private NBFCRequestDTO getLoanPreviewPayload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails){

        return NBFCRequestDTO.builder()
                .applicationId(lendingApplication.getId())
                .productName("LENDING")
                .lender(lendingApplication.getLender())
                .payload(PayULoanPreviewRequestDTO.builder()
                        .applicationId(lendingApplicationLenderDetails.getLeadId())
                        .amount(lendingApplication.getLoanAmount().intValue())
                        .tenure(lendingApplication.getPayableDays())
                        .roi(lendingApplicationLenderDetails.getAnnualRoi().toString())
                        .pf(lendingApplication.getProcessingFee())
                        .pfType("FIXED_AMOUNT")
                        .build())
                .build();
    }
}
