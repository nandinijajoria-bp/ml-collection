package com.bharatpe.lending.loanV3.consumer;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.util.ConfigResolver;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssignment;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.loanV3.utils.RiskEngineUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

@Component
@Slf4j
public class BreRequestKafka {

    @Autowired
    ConfigResolver configResolver;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LenderGatewayFactory lenderGatewayFactory;

    @Autowired
    ILenderAssignment iLenderAssignment;

    @Autowired
    LenderAssociationStageFactory lenderAssociationStageFactory;

    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    @Lazy
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    ConverterUtils converterUtils;

    @KafkaListener(topics="${abfl.bre.topic:invoke_bre}", concurrency = "5")
    public void breRequestListener(String request) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = null;
        Optional<LendingApplication> lendingApplication = Optional.empty();
        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            log.info("Received bre request:{}", request);
            Map<String,String> breRequestString = configResolver.getConfig(request, new TypeReference<Map<String, String>>() {
            });
            lendingApplication = lendingApplicationDao.findById(Long.valueOf(breRequestString.get("application_id")));
            if (!lendingApplication.isPresent()) {
                log.info("no application found for id {}", breRequestString.get("application_id"));
            }
            BreApiRequestDto breRequest = createPayload(Long.valueOf(breRequestString.get("application_id")));
            lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(breRequest.getApplicationId(),Status.ACTIVE.name(), Lender.ABFL.name());
            if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails) &&
                    (!breRequest.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender()) ||
                            !LenderAssociationStages.BRE.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage()))) {
                log.info("lender or stage mismatch while initiating bre association for application {}", breRequest.getApplicationId());
                return;
            }
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
                lendingApplicationLenderDetails.setApplicationId(breRequest.getApplicationId());
                lendingApplicationLenderDetails.setLender(breRequest.getLender());
                lendingApplicationLenderDetails.setStage(LenderAssociationStages.BRE.name());
                lendingApplicationLenderDetails.setStatus(Status.ACTIVE.name());
                lendingApplicationLenderDetails.setAccountId(lendingApplication.get().getExternalLoanId());
                lendingApplicationLenderDetails.setKycRetryCount(0);
                DecimalFormat df = new DecimalFormat("#.##");
                df.setRoundingMode(RoundingMode.DOWN);
                lendingApplicationLenderDetails.setAnnualRoi(Double.valueOf(df.format(
                        lendingApplicationServiceV2.getApr(lendingApplication.get().getMerchantId(), lendingApplication.get().getId(), lendingApplication.get().getLoanAmount(),
                                LenderOffDays.valueOf(lendingApplication.get().getLender()).getEdiModel().getNoOfEdiDaysInAWeek(), lendingApplication.get().getLender()))));
            }
            lendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.BRE_PENDING.name());
            lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
            if (lendingApplicationLenderDetails.getAnnualRoi() > 50) {
                log.info("pre rejecting, changing lender as roi > 50, for {}", request);
                nbfcUtils.modifyLender(lendingApplication.get(), lendingApplicationLenderDetails, LenderAssociationStatus.BRE_HARD_FAILED);
                return;
            }
            INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(breRequest.getLender());
            BreApiResponseDto breApiResponseDto = apiGatewayV3.invokeBre(breRequest);
            if (ObjectUtils.isEmpty(breApiResponseDto) ||
                    !breApiResponseDto.getSuccess() ||
                    ObjectUtils.isEmpty(breApiResponseDto.getData()) ||
                    !StatusCheckResponse.SUCCESS.name().equalsIgnoreCase(breApiResponseDto.getData().getResponseStatus())
            ) {
                if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType())) {
                    log.info("marking breStatus BRE_FAILED and rejecting application for topup application as bre callback resulted in failure for  {}", lendingApplication.get().getId());
                    lendingApplication.get().setStatus("rejected");
                    lendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.BRE_FAILED.name());
                    lendingApplicationLenderDetails.setStatus(Status.INACTIVE.name());
                    lendingApplicationDao.save(lendingApplication.get());
                    lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                } else {
                    log.info("request resulted in bre failure, modifying lender for {}", request);
                    nbfcUtils.modifyLender(lendingApplication.get(), lendingApplicationLenderDetails, LenderAssociationStatus.BRE_FAILED);
                }
            }
            else {
                lendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.BRE_IN_PROGRESS.name());
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                log.info("successfully placed the bre request at lender for {}", request);
                }
        } catch (Exception ex) {
            log.error("exception occurred while processing bre request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
//            throw new RuntimeException("unable to ack bre event" + request);
            nbfcUtils.modifyLender(lendingApplication.get(), lendingApplicationLenderDetails, LenderAssociationStatus.BRE_REQUEST_CLIENT_FAILURE);
        }
    }


    // for callback kafka event from nbfc service
    @KafkaListener(topics = "${abfl.bre.callback.topic:bureau-callback}", concurrency = "5")
    public void breCallbackListener(String request) {
        Optional<LendingApplication> lendingApplication = Optional.empty();
        LendingApplicationLenderDetails existingLendingApplicationLenderDetails = null;
        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            log.info("Received bre callback request:{}", request);
            BreCallbackResponseDto breCallbackResponseDto = configResolver.getConfig(request, BreCallbackResponseDto.class);
            lendingApplication = lendingApplicationDao.findById(Long.valueOf(breCallbackResponseDto.getApplicationId()));
            if (!lendingApplication.isPresent()) {
                log.info("no application found for id {}", breCallbackResponseDto.getData());
                return;
            }
            existingLendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.get().getId(),Status.ACTIVE.name(), Lender.ABFL.name());
            if (ObjectUtils.isEmpty(existingLendingApplicationLenderDetails) ||
                    !breCallbackResponseDto.getLender().equalsIgnoreCase(existingLendingApplicationLenderDetails.getLender()) ||
                    // TODO: 10/11/22  todo final change this to account id
                    !ObjectUtils.isEmpty(existingLendingApplicationLenderDetails.getBreCompletionTimestamp())) {
                log.info("lender/stage mismatch while callback ack / callback already received for bre in application {}", breCallbackResponseDto.getApplicationId());
                return;
            }
            if (Boolean.FALSE.equals(breCallbackResponseDto.getSuccess())) {
                if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType())) {
                    if(breCallbackResponseDto.getData().getIsRetryable()) {
                        log.info("marking breStatus as BRE_RETRY as bre Callback resulted in bre retry true for: {}", lendingApplication.get().getId());
                        existingLendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.BRE_RETRY.name());
                        lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);
                        return;
                    }
                    log.info("marking breStatus BRE_FAILED and deleting application for topup application as bre callback resulted in failure for  {}", lendingApplication.get().getId());
                    lendingApplication.get().setStatus("rejected");
                    existingLendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.BRE_FAILED.name());
                    existingLendingApplicationLenderDetails.setStatus(Status.INACTIVE.name());
                    lendingApplicationDao.save(lendingApplication.get());
                    lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);
                    return;
                }
                log.info("modifying lender as bre callback resulted in failure for  {}", lendingApplication.get().getId());
                nbfcUtils.modifyLender(lendingApplication.get(),existingLendingApplicationLenderDetails, LenderAssociationStatus.BRE_FAILED);
                return;
            }
            BreCallbackResponseDto.Data data = breCallbackResponseDto.getData().getData();
            existingLendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.BRE_COMPLETED.name());
            LenderAssociationStages nextStage = LenderAssociationStageFactory.getNextStage(Lender.valueOf(breCallbackResponseDto.getLender()),LenderAssociationStages.BRE);
            existingLendingApplicationLenderDetails.setStage(nextStage.name());
            existingLendingApplicationLenderDetails.setBreCompletionTimestamp(new Date());
            existingLendingApplicationLenderDetails.setNbfcBreAsyncId(data.getAsyncId());
//            existingLendingApplicationLenderDetails.setNbfcId(data.getAbflApplicationId());
            existingLendingApplicationLenderDetails.setCccId(data.getCccId());
            existingLendingApplicationLenderDetails.setNbfcApprovedLoanOfferAmt(data.getLoanAmount());
            existingLendingApplicationLenderDetails.setRoi(Double.valueOf(data.getRoi()));
            existingLendingApplicationLenderDetails.setTenure(Integer.valueOf(data.getTenure()));
            lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);
//            lendingApplication.get().setNbfcId(data.getAbflApplicationId());
            lendingApplicationDao.save(lendingApplication.get());
            nbfcUtils.pushApplicationToNextStage(lendingApplication.get().getId(),
                    lendingApplication.get().getLender(), LenderAssociationStages.BRE.name(),
                    LenderAssociationStageFactory.autoInvokeNextStage(Lender.valueOf(lendingApplication.get().getLender()), LenderAssociationStages.BRE));
            log.info("bre completed for ", data.getAccountId());
        } catch (Exception ex) {
            log.error("exception occurred while processing bre callback request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
//            throw new RuntimeException("unable to ack bre callback event" + request);
            nbfcUtils.modifyLender(lendingApplication.get(),existingLendingApplicationLenderDetails, LenderAssociationStatus.BRE_CALLBACK_CLIENT_FAILURE);
        }
    }

    public BreApiRequestDto createPayload(Long applicationId) {
        try {
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
            if (!lendingApplication.isPresent()) {
                log.error("application not found !! {}", applicationId);
            }
            CKycResponseDto cKycResponseDto = kycUtils.getKycData(lendingApplication.get().getMerchantId());
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.get().getId());
            String name = Optional.ofNullable(cKycResponseDto.getName()).orElse("").trim();
            String firstName = !ObjectUtils.isEmpty(cKycResponseDto.getName()) ?
                    (name.indexOf(" ") == -1 ? name :
                            name.substring(0,name.indexOf(" ")).trim())
                    : cKycResponseDto.getFirstName().trim();

            String middleName = !ObjectUtils.isEmpty(cKycResponseDto.getName()) ?
                    getMiddleName(cKycResponseDto.getName()) : cKycResponseDto.getMiddleName().trim();

            String lastName = !ObjectUtils.isEmpty(cKycResponseDto.getName()) ?
                    (name.lastIndexOf(" ") == -1 ? name :
                            name.substring(name.lastIndexOf(" ") + 1).trim())
                    : cKycResponseDto.getLastName().trim();
            String productCode = LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType()) ? "TopupLoan" : "BharatPe";
            BreApiRequestDto breRequestKafkaDto = BreApiRequestDto.builder()
                    .applicationId(applicationId)
                    .lender(lendingApplication.get().getLender())
                    .productName("LENDING")
                    .payload(
                            BreApiRequestDto.Payload.builder()
                                    .accountId(lendingApplication.get().getExternalLoanId())
                                    .customerReport(
                                            BreApiRequestDto.CustomerReport.builder()
                                                    .kycInfo(
                                                            BreApiRequestDto.KycInfo.builder()
                                                                    .addressLine1(converterUtils.parseData(cKycResponseDto.getAddress()))
                                                                    .addressLine2("")
                                                                    .addressLine3("")
                                                                    .city(cKycResponseDto.getCity())
                                                                    .dob(DateTimeUtil.formatDate(cKycResponseDto.getDob(),"dd/MM/yyyy","yyyy-MM-dd"))
                                                                    .firstName(converterUtils.parseNameData(firstName))
                                                                    .gender(cKycResponseDto.getGender())
                                                                    .lastName(converterUtils.parseNameData(lastName))
                                                                    .mobile(ObjectUtils.isEmpty(cKycResponseDto.getMobile()) ? "" : cKycResponseDto.getMobile().substring(2))
                                                                    .middleName(converterUtils.parseNameData(middleName))
                                                                    .panNumber(cKycResponseDto.getPanNumber())
                                                                    .pincode(Integer.valueOf(cKycResponseDto.getPincode()))
                                                                    .state(cKycResponseDto.getState())
                                                                    .build()
                                                    )
                                                    .loanApplicationRequest(BreApiRequestDto.LoanApplicationRequest.builder()
                                                            .requestedLoanAmount(lendingApplication.get().getLoanAmount())
                                                            .roi(String.valueOf(lendingApplication.get().getInterestRate()))
                                                            .tenure(lendingApplication.get().getTenureInMonths().toString())
                                                            .build())
                                                    .build()
                                    )
                                    .productCode(productCode)
                                    .source("BharatPe")
                                    .loanSegment(RiskEngineUtil.loanRiskMapping(lendingRiskVariablesSnapshot.getRiskSegment().name()))
                                    .riskGroup(lendingRiskVariablesSnapshot.getRiskGroup())
                                    .pincodeColor(lendingRiskVariablesSnapshot.getPincodeColor().name())
                                    .build())
                    .build();
            log.info("breRequest payload {}", breRequestKafkaDto);
            return breRequestKafkaDto;
        } catch (Exception e) {
            log.error("exception occurred while initiating bre workflow for  {}", applicationId);
        }
        return null;
    }

    private String getMiddleName(String name) {
        int firstOccurence = name.indexOf(" ");
        int lastOccurence = name.lastIndexOf(" ");
        if (firstOccurence == lastOccurence) {
            return "";
        }
        return name.substring(firstOccurence + 1, lastOccurence).trim();
    }
}
