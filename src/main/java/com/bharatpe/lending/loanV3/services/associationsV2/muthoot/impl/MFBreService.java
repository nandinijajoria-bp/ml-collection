package com.bharatpe.lending.loanV3.services.associationsV2.muthoot.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingOfferModificationSnapshotDao;
import com.bharatpe.lending.entity.LendingOfferModificationSnapshot;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFAcceptOfferDTO;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFAcceptOfferRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFBreRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFAcceptOfferResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFBreCallbackResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFBreResponseDTO;
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
import java.util.*;


@Slf4j
@Service
public class MFBreService {

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

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LendingOfferModificationSnapshotDao lendingOfferModificationSnapshotDao;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Value("${muthoot.percent.experimentation.value:0}")
    Integer muthootPercentExperimentationValue;

    @Value("${muthoot.bre.max.roi.difference : 0.2}")
    Double muthootBreMaxRoiDifference;

    private static final String TOPUP = "Topup";

    @Transactional
    public boolean invokeBre(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.RISK_DECISION.name());
            if (Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails())
                    || Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
                return false;
            }
            String breExperimentationFlag = easyLoanUtil.percentScaleUp(lenderAssociationDetailsRequestDto.getLendingApplication().getMerchantId(), muthootPercentExperimentationValue) ? "N" : "Y";
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setNbfcBreAsyncId(breExperimentationFlag);
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
            NBFCRequestDTO breRequestPayload = getBreRequestPayload(lenderAssociationDetailsRequestDto);
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(breRequestPayload, LenderAssociationStages.BRE);
            log.info("BRE response of Muthoot from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());

            if (Objects.nonNull(nbfcResponseDto.getData()) && nbfcResponseDto.getSuccess()) {
                MFBreResponseDTO breResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), MFBreResponseDTO.class);
                if ("GNO-S-000".equalsIgnoreCase(breResponseDTO.getStatusCode())) {
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                    return true;
                }
            }

        } catch (Exception e) {
            log.error("error while invoking BRE of Muthoot for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
        return false;
    }

    private NBFCRequestDTO getBreRequestPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsDto.getLendingApplicationLenderDetails();
        try {
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            if (ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
                throw new RuntimeException("Lending risk variable snapshot not found for application");
            }
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.MUTHOOT.name())
                    .payload(MFBreRequestDTO.builder()
                            .customerID(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                            .riskVariables(MFBreRequestDTO.RiskVariables.builder()
                                    .tpv(getValueOrDefault(lendingRiskVariablesSnapshot.getMonthlyTpv(), 0D))
                                    .loanAmount(lendingApplication.getLoanAmount())
                                    .tenure(lendingApplication.getTenureInMonths())
                                    .riskGroup(lendingRiskVariablesSnapshot.getRiskGroup())
                                    .riskSegment(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()) ? TOPUP : lendingRiskVariablesSnapshot.getRiskSegment().name())
                                    .netFreeIncome(getValueOrDefault(lendingRiskVariablesSnapshot.getMonthlyNfi(), 0D))
                                    .pincodeColour(lendingRiskVariablesSnapshot.getPincodeColor().name())
                                    .pincodeBand(lendingApplication.getPincode())
                                    .experimentationFlag(lendingApplicationLenderDetails.getNbfcBreAsyncId())
                                    .proposedInterest(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getAnnualRoi())
                                    .proposedProcessingFee(lendingApplication.getProcessingFee())
                                    .build())
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while KYC request payload for createLead for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    @Async("lenderPoolTaskExecutor")
    public boolean invokeAcceptOffer(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto, MFBreCallbackResponseDTO callbackResponse) {
        try {
            if (Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails())
                    || Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.ACCEPT_OFFER_FAILED);
                return false;
            }
            MFAcceptOfferDTO acceptedOffer = getEligibleOffer(lenderAssociationDetailsRequestDto, callbackResponse);
            if(ObjectUtils.isEmpty(acceptedOffer)) {
                log.info("no eligible offer found from lender's BRE response with loanAmount {} and tenure {} for applicationId {}",
                        lenderAssociationDetailsRequestDto.getLendingApplication().getLoanAmount(), lenderAssociationDetailsRequestDto.getLendingApplication().getTenureInMonths(), lenderAssociationDetailsRequestDto.getLendingApplication().getId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_FAILED.name());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.NO_ELIGIBLE_OFFER.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.ACCEPT_OFFER_FAILED);
                return false;
            }
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
            NBFCRequestDTO acceptOfferPayload = getAcceptOfferPayload(lenderAssociationDetailsRequestDto, acceptedOffer);
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(acceptOfferPayload, LenderAssociationStages.ACCEPT_OFFER);
            log.info("Accept Offer response of Muthoot from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto.getData()) && nbfcResponseDto.getSuccess()) {
                MFAcceptOfferResponseDTO acceptOfferResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), MFAcceptOfferResponseDTO.class);
                if ("AOP-S-000".equalsIgnoreCase(acceptOfferResponseDTO.getStatusCode()) && "ok".equalsIgnoreCase(acceptOfferResponseDTO.getData().getMessage())) {
                    updateOfferDetails(lenderAssociationDetailsRequestDto, acceptedOffer);
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_SUCCESS.name());
                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequestDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("error while invoking Accept Offer of Muthoot for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.ACCEPT_OFFER_FAILED);
        return false;
    }

    private NBFCRequestDTO getAcceptOfferPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, MFAcceptOfferDTO acceptedOffer) {
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsDto.getLendingApplicationLenderDetails();
        try {
            Double irr = lendingApplicationLenderDetails.getAnnualRoi();
            Double processingFee = lendingApplication.getProcessingFee();
            if("N".equalsIgnoreCase(lendingApplicationLenderDetails.getNbfcBreAsyncId())) {
                if(Math.abs(acceptedOffer.getSlab().getInterest() - lendingApplicationLenderDetails.getAnnualRoi()) > muthootBreMaxRoiDifference) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.ROI_MISMATCHED.name());
                    log.info("max difference threshold {} in lender's IRR {} and BP IRR {} breached for applicationId {}", muthootBreMaxRoiDifference, acceptedOffer.getSlab().getInterest(), lendingApplicationLenderDetails.getAnnualRoi(), lendingApplication.getId());
                    throw new RuntimeException("Max difference threshold in lender's IRR and BP's IRR breached for " + lendingApplication.getId());
                }
                irr = acceptedOffer.getSlab().getInterest();
                processingFee = acceptedOffer.getSlab().getProcessingFee();
            }
            MFAcceptOfferRequestDTO.RequestData requestData = MFAcceptOfferRequestDTO.RequestData.builder()
                    .offerID(acceptedOffer.getOfferId())
                    .amount(lendingApplication.getLoanAmount())
                    .tenure(lendingApplication.getTenureInMonths())
                    .tenureType("MONTH")
                    .processingFee(processingFee)
                    .interest(irr)
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.MUTHOOT.name())
                    .payload(MFAcceptOfferRequestDTO.builder()
                            .customerID(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                            .offerDetails(requestData)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while KYC request payload for createLead for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public Boolean processMFBreCallback(NBFCResponseDTO nbfcResponseDTO) {
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
            if (!LenderAssociationStages.BRE.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())
                    || !LenderAssociationStatus.RISK_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getBreStatus())) {
                log.info("Application not in correct state for BRE callback for applicationId {}", lendingApplication.getId());
                return false;
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .modifyLender(enableLenderChange)
                    .manageState(true)
                    .build();
            if (Objects.nonNull(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                MFBreCallbackResponseDTO breCallbackResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), MFBreCallbackResponseDTO.class);
                log.info("BRE callback Response of Muthoot for {} {}", nbfcResponseDTO.getApplicationId(), breCallbackResponseDTO);
                if (!ObjectUtils.isEmpty(breCallbackResponseDTO)) {
                    if ("COMPLETED".equalsIgnoreCase(breCallbackResponseDTO.getData().getStatus()) || "SUCCESS".equalsIgnoreCase(breCallbackResponseDTO.getData().getStatus())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
                        commonService.manageApplicationState(lenderAssociationDetailsRequest);
                        invokeAcceptOffer(lenderAssociationDetailsRequest, breCallbackResponseDTO);
                        return true;
                    } else if ("REJECTED".equalsIgnoreCase(breCallbackResponseDTO.getData().getStatus()) || "FAILED".equalsIgnoreCase(breCallbackResponseDTO.getData().getStatus())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.RISK_FAILED);
                        return false;
                    }
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.RISK_FAILED);
        } catch (Exception e) {
            log.error("exception while processing BRE callback of Muthoot for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private MFAcceptOfferDTO getEligibleOffer(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest, MFBreCallbackResponseDTO breCallbackResponseDTO){
        Double bpLoanAmount = lenderAssociationDetailsRequest.getLendingApplication().getLoanAmount();
        Integer bpTenure = lenderAssociationDetailsRequest.getLendingApplication().getTenureInMonths();
        log.info("BP Loan Amount: {}, BP Tenure: {} {}", bpLoanAmount, bpTenure, breCallbackResponseDTO);
        if(!ObjectUtils.isEmpty(breCallbackResponseDTO.getData().getOffers())) {
            MFAcceptOfferDTO acceptedOffer = null;
            if("N".equalsIgnoreCase(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getNbfcBreAsyncId())) {
                for (MFBreCallbackResponseDTO.LoanOffer offer : breCallbackResponseDTO.getData().getOffers()) {
                    if (!ObjectUtils.isEmpty(offer.getSlabs())) {
                        Optional<MFBreCallbackResponseDTO.Slab> matchingOffer = offer.getSlabs().stream()
                                .filter(slab -> slab.getMinAmount() <= bpLoanAmount && bpLoanAmount <= slab.getMaxAmount())
                                .filter(slab -> Objects.equals(slab.getTenure(), bpTenure))
                                .findFirst();

                        if (matchingOffer.isPresent()) {
                            log.info("Matching Slab Found: {}", matchingOffer.get());
                            acceptedOffer = MFAcceptOfferDTO.builder()
                                    .OfferId(offer.getOfferID())
                                    .slab(matchingOffer.get())
                                    .build();
                            break;
                        }
                    }
                }
                return acceptedOffer;
            }
            acceptedOffer = MFAcceptOfferDTO.builder()
                    .OfferId(breCallbackResponseDTO.getData().getOffers().get(0).getOfferID())
                    .slab(null)
                    .build();
           return acceptedOffer;
        }
        return null;
    }

    private <T> T getValueOrDefault(T value, T defaultValue) {
        if (!ObjectUtils.isEmpty(value)) {
            return value;
        }
        return defaultValue;
    }

    private void updateOfferDetails(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto,  MFAcceptOfferDTO acceptedOffer) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequestDto.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails();
        try {
            if ("N".equalsIgnoreCase(lendingApplicationLenderDetails.getNbfcBreAsyncId())) {
                log.info("Updating offer details after acceptOffer as breExperiment flag {} for applicationId {}", lendingApplicationLenderDetails.getNbfcBreAsyncId(), lendingApplication.getId());
                LendingOfferModificationSnapshot lendingOfferModificationSnapshot = getLendingOfferModificationSnapshot(lendingApplication);
                log.info("saving previous offer details in lendingOfferModificationSnapshot {} for applicationId {}", lendingOfferModificationSnapshot, lendingApplication.getId());
                lendingOfferModificationSnapshotDao.save(lendingOfferModificationSnapshot);
                Double processingFee = acceptedOffer.getSlab().getProcessingFee();
                lendingApplication.setProcessingFee(processingFee);
                lendingApplication.setDisbursalAmount(lendingApplication.getLoanAmount() - processingFee);
                lendingApplicationLenderDetails.setAnnualRoi(acceptedOffer.getSlab().getInterest());
                lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
                lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
            }
        } catch (Exception e) {
            log.error("Exception in updating offer details for applicationId {} {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
            throw new RuntimeException("Exception in updating offer details for applicationId " + lendingApplication.getId());
        }
    }

    private LendingOfferModificationSnapshot getLendingOfferModificationSnapshot(LendingApplication lendingApplication) {
        LendingOfferModificationSnapshot lendingOfferModificationSnapshot = new LendingOfferModificationSnapshot();
        lendingOfferModificationSnapshot.setApplicationId(lendingApplication.getId());
        lendingOfferModificationSnapshot.setPayableDays(lendingApplication.getPayableDays());
        lendingOfferModificationSnapshot.setDisbursalAmount(lendingApplication.getDisbursalAmount());
        lendingOfferModificationSnapshot.setLoanAmount(lendingApplication.getLoanAmount());
        lendingOfferModificationSnapshot.setEdiAmount(lendingApplication.getEdi());
        lendingOfferModificationSnapshot.setProceeingFee(lendingApplication.getProcessingFee());
        lendingOfferModificationSnapshot.setRepaymentAmount(lendingApplication.getRepayment());
        return lendingOfferModificationSnapshot;
    }

}
