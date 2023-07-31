package com.bharatpe.lending.loanV3.revamp.services;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingDisbursalStageDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.ApplicationStage;
import com.bharatpe.lending.common.enums.RejectionReason;
import com.bharatpe.lending.common.enums.RejectionStage;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.exception.BureauCallMaskedApiException;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.RejectionStateDto;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.IEdiModelAssignment;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant.BP_CLUB_MEMBERSHIP_KEY_PREFIX;

@Service
@Slf4j
public class LoanDashboardService {


    @Value("${loan.details.refresh.window:15}")
    int loanDetailsRefreshWindow;

    @Autowired
    private LoanUtil loanUtil;

    @Autowired
    private EasyLoanUtil easyLoanUtil;

    @Autowired
    private LendingMerchantDetailsDao lendingMerchantDetailsDao;

    @Autowired
    private LendingCache lendingCache;

    @Autowired
    private APIGatewayService apiGatewayService;

    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    @Autowired
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    private LendingResubmitTaskDao lendingResubmitTaskDao;

    @Autowired
    private LendingResubmitReasonCountDao lendingResubmitReasonCountDao;

    @Autowired
    private LendingDisbursalStageDao lendingDisbursalStageDao;

    @Autowired
    private LendingGstDao lendingGstDao;

    @Autowired
    private LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
     private KycHandler kycHandler;

    @Autowired
    private LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    private ExperianDao experianDao;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MerchantSummaryHandler merchantSummaryHandler;

    @Autowired
    private EligibleLoanDao eligibleLoanDao;

    @Autowired
    private LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Value("${eligibility.refresh.window:1}")
    int eligibilityRefreshWindow;

    @Autowired
    private DateTimeUtil dateTimeUtil;

    @Value("${club.eligible.loan.cache:true}")
    Boolean clubEligibleLoanCache;

    @Value("${abfl.rollout.percent:10}")
    Integer rolloutAbflPercent;

    @Value("${edi.assignment.model:false}")
    Boolean assignEdiModelFromModelAssignmentEngine;

    @Value("${screen.redesign.rollout.percent:0}")
    Integer screenRedesignRolloutPercent;

    @Value("${screen.redesign.one.percent.rollout.date:}")
    String screenRedesignOnePercentRolloutDate;

    @Value("${screen.redesign.five.percent.rollout.date:}")
    String screenRedesignFivePercentRolloutDate;

    @Value("${screen.redesign.ten.percent.rollout.date:}")
    String screenRedesignTenPercentRolloutDate;

    @Value("${screen.redesign.twenty.percent.rollout.date:}")
    String screenRedesignTwentyPercentRolloutDate;

    @Value("${screen.redesign.fifty.percent.rollout.date:}")
    String screenRedesignFiftyPercentRolloutDate;

    @Value("${screen.redesign.hundred.percent.rollout.date:}")
    String screenRedesignHundredPercentRolloutDate;

    @Autowired
    IEdiModelAssignment iEdiModelAssignment;


    /*
    This method gives the api version to frontend,so that FE can decide which flow to trigger for loan application corresponding to merchant
    currently we are deciding this feature on the basis of internal/external merchant only
     */
    public LoanDashboardApiVersion getLoanDashboardApiVersion(BasicDetailsDto merchantDetails) {
        log.info("Getting loan dashboard api version details for merchantId:{}", merchantDetails.getId());
        LoanDashboardApiVersion loanDashboardApiVersion = new LoanDashboardApiVersion();
        try{
            // hardcoding value for some testing
            if(merchantDetails.getId()==9987300){
                loanDashboardApiVersion.setApiVersion("v1");
            }
            else if (loanUtil.isInternalMerchant(merchantDetails.getId())){
                loanDashboardApiVersion.setApiVersion("v2");
            }
            else if(percentScaleUp(merchantDetails.getId(), screenRedesignRolloutPercent)){
                LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(null, merchantDetails.getId());
                if(!ObjectUtils.isEmpty(lendingApplication)){
                    Date thresholdDate = getThresholdDate(merchantDetails.getId());
                    if(lendingApplication.getCreatedAt().after(thresholdDate))loanDashboardApiVersion.setApiVersion("v2");
                    else loanDashboardApiVersion.setApiVersion("v1");
                }
                else loanDashboardApiVersion.setApiVersion("v2");
            }
            else
                loanDashboardApiVersion.setApiVersion("v1");
            log.info("Returning loan dashboard api version detail for merchantId:{}, details:{}", merchantDetails.getId(), loanDashboardApiVersion);
            return loanDashboardApiVersion;
        }
        catch(Exception e){
            log.error("Exception in fetching version for merchant:{}, {}, {}", merchantDetails.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            loanDashboardApiVersion.setApiVersion("v1");
            return loanDashboardApiVersion;
        }
    }


    public Object getLoanDashboardDetails(BasicDetailsDto merchantDetails) {
        // in previous  version we  are using cache first build the data.

        LoanDetailsResponse loanDetailsResponse = new LoanDetailsResponse();
        loanDetailsResponse.setMerchantId(merchantDetails.getId());
        //set dummy merchant
        loanDetailsResponse.setDummyMerchant(easyLoanUtil.isDummyMerchant(merchantDetails.getId()));
        Experian experian = experianDao.getByMerchantId(merchantDetails.getId());
        if (experian != null) {
            loanDetailsResponse.setPancard(experian.getPancardNumber());
            loanDetailsResponse.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
            loanDetailsResponse.setHasExperian(true);
        }
        else {
            log.error("No experian record for merchantId:{},returning empty records", merchantDetails.getId());
            return loanDetailsResponse;
        }
        // set bank details and benificiaryName and account details (in single api get all the information)
        setBankAccountDetails(merchantDetails.getId(),loanDetailsResponse);
        // set business details
        populateBusinessDetails(merchantDetails.getId(),loanDetailsResponse);
        //set bpClubMember
//        setBpClubMember(merchantDetails.getId(),loanDetailsResponse);
        // to check if user have repeat loan
        loanDetailsResponse.setRepeatLoan(loanUtil.isRepeatLoan(merchantDetails.getId()));
        //if user has inactive loan, return
        LendingPaymentSchedule lendingPaymentSchedule1 = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantDetails.getId(), "INACTIVE");
        if (!ObjectUtils.isEmpty(lendingPaymentSchedule1)) {
            loanDetailsResponse.setIneligible(RejectionReason.LOW_TRANSACTION.getReason());
            loanDetailsResponse.setKycStatus(KycStatus.APPROVED);
            return loanDetailsResponse;
        }

        // check if user have active loan
        if (loanUtil.hasActiveLoan(merchantDetails)) {
            log.info("active loan merchant:{}", merchantDetails.getId());
            LendingApplication topupApplication = lendingApplicationDao.findOpenTopUpApplication(merchantDetails.getId(), "TOPUP");
            if(Objects.nonNull(topupApplication)){
                LoanApplicationDetails topUpApplicationDetails = setApplicationDetails(topupApplication,merchantDetails);
                loanDetailsResponse.setTopupLoanApplication(topUpApplicationDetails);
            }
            loanDetailsResponse.setActiveLoan(true);
            return loanDetailsResponse;
        }
       // loanDetailsResponse.setEligibleForCallback(checkEligibilityForCallback(merchant.getId()));
        Optional<LendingPaymentSchedule> lendingPaymentSchedule = lendingPaymentScheduleDao.findLatestClosedLoan(merchantDetails.getId());
        LendingApplication openApplication;
        if (lendingPaymentSchedule.isPresent()) {
            openApplication = lendingApplicationDao.findTopByMerchantIdAndLoanDisbursalStatusNullAndPaymentScheduleStatusClosedOrderByIdDesc(merchantDetails.getId(), lendingPaymentSchedule.get().getCreatedAt());
        } else {
            openApplication = lendingApplicationDao.findTopByMerchantIdAndLoanDisbursalStatusNullOrderByIdDesc(merchantDetails.getId());
        }
        if (openApplication != null) {
            //kyc checks can be removed from here...
            LoanApplicationDetails loanApplicationDetails = setApplicationDetails(openApplication, merchantDetails);
            loanDetailsResponse.setLoanApplication(loanApplicationDetails);
        }
        checkEligibility(loanDetailsResponse,new LoanDetailsRequest(),experian, merchantDetails);
        log.info("returning response from database");
        return loanDetailsResponse;
    }


    private void setBankAccountDetails(Long merchantId,LoanDetailsResponse loanDetailsResponse) {
           BankAccountDetails bankAccountDetails=loanUtil.getAccountDetails(merchantId);
           if(Objects.nonNull(bankAccountDetails)){
               loanDetailsResponse.setBankLinked(true);
               loanDetailsResponse.setAccountDetails(bankAccountDetails);
               loanDetailsResponse.setMerchantName(bankAccountDetails.getBeneficiaryName());
           }
    }

    private void populateBusinessDetails(Long merchantId, LoanDetailsResponse loanDetailsResponse) {
        LendingMerchantDetails lendingMerchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
        if (Objects.nonNull(lendingMerchantDetails)) {
            loanDetailsResponse.setBusinessName(lendingMerchantDetails.getBusinessName());
            loanDetailsResponse.setBusinessCategory(lendingMerchantDetails.getBusinessCategory());
            loanDetailsResponse.setBusinessSubCategory(lendingMerchantDetails.getBusinessSubCategory());
        }
    }

    private void setBpClubMember(Long merchantId,LoanDetailsResponse loanDetailsResponse){
        String bpMembershipKey = BP_CLUB_MEMBERSHIP_KEY_PREFIX + merchantId;
        Object bpCLubResponse = lendingCache.get(bpMembershipKey);
        if (ObjectUtils.isEmpty(bpCLubResponse)) {
            Boolean isBpClubMember = apiGatewayService.eligibleForProcessingFee(merchantId);
            loanDetailsResponse.setBpClubMember(isBpClubMember);
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(bpMembershipKey);
            addCacheDto.setValue(isBpClubMember);
            addCacheDto.setTtl(7 * 24);
            lendingCache.add(addCacheDto);
        } else {
            loanDetailsResponse.setBpClubMember((Boolean) bpCLubResponse);
        }
    }

    private LoanApplicationDetails setApplicationDetails(LendingApplication openApplication,BasicDetailsDto merchantDetails) {
        try {
            LoanApplicationDetails applicationDetails = new LoanApplicationDetails();
            applicationDetails.setApplicationId(openApplication.getId());
            applicationDetails.setExternalLoanId(openApplication.getExternalLoanId());
            applicationDetails.setLoanAmount(openApplication.getLoanAmount());
            applicationDetails.setApplicationStatus(openApplication.getStatus().toLowerCase());
            applicationDetails.setLender(openApplication.getLender());
            LendingApplicationDetails lendingApplicationDetails =
                    lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(openApplication.getId());
            if (!ObjectUtils.isEmpty(lendingApplicationDetails)) {
                log.info("lender assc for {} {}", lendingApplicationDetails.getLenderAssc(), lendingApplicationDetails.getApplicationId());
                applicationDetails.setLenderAssc(Optional.ofNullable(lendingApplicationDetails.getLenderAssc()).orElse(false));
            }
            if ("approved".equalsIgnoreCase(openApplication.getStatus()) || "pending_verification".equalsIgnoreCase(openApplication.getStatus())) {
                LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationIdAndMerchantId(openApplication.getId(), openApplication.getMerchantId());
                if(Objects.nonNull(lendingResubmitTask)){
                    if (lendingResubmitTask.getResubmit() != null && lendingResubmitTask.getResubmit() && (lendingResubmitTask.getResubmitDone() == null || !lendingResubmitTask.getResubmitDone())) {
                        applicationDetails.setApplicationStatus("RESUBMIT");
                        applicationDetails.setResubmitReason(lendingResubmitTask.getResubmitReason());
                        String resubmitDoneReasons = getResubmitDoneReasons(openApplication.getId(), openApplication.getMerchantId());
                        applicationDetails.setCompletedResubmitReason(resubmitDoneReasons);
                    }
                    else if (lendingResubmitTask.getDowngrade() != null && lendingResubmitTask.getDowngrade() && (lendingResubmitTask.getDowngradeDone() == null || !lendingResubmitTask.getDowngradeDone())) {
                        applicationDetails.setApplicationStatus("DOWNGRADE");
                    }
                    else if(lendingResubmitTask.getResign() != null && lendingResubmitTask.getResign() && (lendingResubmitTask.getResignDone() == null || !lendingResubmitTask.getResignDone())) {
                        applicationDetails.setApplicationStatus("RESIGN");
                    }
                }
            addApplicationStages(openApplication,applicationDetails);
            }

            RejectionStateDto rejectionStateDto = getRejectionReason(openApplication, merchantDetails);
            applicationDetails.setRejectReason(rejectionStateDto.getRejectionReason());
            applicationDetails.setRejectionMessage(rejectionStateDto.getRejectionMessage());
            applicationDetails.setAddressDetails(getShopAddress(openApplication));
            applicationDetails.setProfessionalDetails(getProfessionalDetails(openApplication));
            applicationDetails.setAdditionalDetails(new AdditionalDetails(openApplication.getEmail(), openApplication.getAlternateMobile()));
            applicationDetails.setCurrentAddress(getCurrentAddress(openApplication));
            applicationDetails.setShopPhotoRequired(isShopPhotoRequired(openApplication));
            if (applicationDetails.getEnachDeeplink() == null && (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus()) || ApplicationStatus.APPROVED.name().equalsIgnoreCase(openApplication.getStatus()))) {
                int tat = loanUtil.getApplicationTAT(openApplication.getId());
                applicationDetails.setTransferDays(tat < 1 ? "Soon" : tat + "-" + (tat + 2) + " Days");
            }
            Long reapplyTime = getReapplyTime(openApplication);
            if (Objects.nonNull(reapplyTime)) {
                reapplyTime = reapplyTime > 0 ? reapplyTime : 0;
                applicationDetails.setReapplyTime(reapplyTime);
                applicationDetails.setReapplyTimeEpoch(LoanUtil.addDays(new Date(), reapplyTime).getTime());
            }
            applicationDetails.setReapply(shouldReapply(openApplication, reapplyTime));
            return applicationDetails;
        } catch (Exception e) {
            log.error("Exception in setApplicationDetails for merchant:{}", openApplication.getMerchantId(), e);
        }
        return null;
    }

    private String getResubmitDoneReasons(Long applicationId, Long merchantId){
        String reason = "";
        List<LendingResubmitReasonCount> lendingResubmitReasonCountList = lendingResubmitReasonCountDao.findByApplicationIdAndMerchantId(applicationId, merchantId);
        if(ObjectUtils.isEmpty(lendingResubmitReasonCountList))return null;
        Integer maxCount = -1;
        for(LendingResubmitReasonCount lendingResubmitReasonCount : lendingResubmitReasonCountList){
            if(lendingResubmitReasonCount.getResubmitCount() > maxCount)maxCount = lendingResubmitReasonCount.getResubmitCount();
        }
        for(LendingResubmitReasonCount lendingResubmitReasonCount : lendingResubmitReasonCountList){
            if(lendingResubmitReasonCount.getResubmitCount() != maxCount)continue;
            if(!ObjectUtils.isEmpty(lendingResubmitReasonCount.getResubmitReason()) && Objects.nonNull(lendingResubmitReasonCount.getResubmitDone())
                    && lendingResubmitReasonCount.getResubmitDone()){
                if("".equals(reason))reason = reason + lendingResubmitReasonCount.getResubmitReason();
                else reason = reason + "," + lendingResubmitReasonCount.getResubmitReason();
            }
        }
        if("".equals(reason))return null;
        return reason;
    }

    private RejectionStateDto getRejectionReason(LendingApplication openApplication, BasicDetailsDto merchant) {
        RejectionStateDto rejectionStateDto = new RejectionStateDto();
        String rejectionReason = null;
        String rejectionMessage = null;
        if (!ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getStatus()))
            return rejectionStateDto;
        if (KycStatus.REJECTED.name().equalsIgnoreCase(openApplication.getCkycStatus())) {
            rejectionReason = openApplication.getCkycRejectionReason();
        } else if (KycStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualKyc())) {
            rejectionMessage = easyLoanUtil.getRejectionMessage(openApplication.getManualKycReason(), RejectionStage.KYC);
            rejectionReason = Objects.nonNull(openApplication.getManualKycReason()) ? openApplication.getManualKycReason() : null;
        }
        else if (KycStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualCibil())) {
            rejectionMessage = easyLoanUtil.getRejectionMessage(openApplication.getManualCibilReason(), RejectionStage.CIBIL);
            rejectionReason = Objects.nonNull(openApplication.getManualCibilReason()) ? openApplication.getManualCibilReason() : null;
        }
        else if (KycStatus.REJECTED.name().equalsIgnoreCase(openApplication.getPhysicalVerificationStatus())) {
            rejectionMessage = easyLoanUtil.getRejectionMessage(openApplication.getPhysicalReason(), RejectionStage.QC);
            rejectionReason = Objects.nonNull(openApplication.getPhysicalReason()) ? openApplication.getPhysicalReason() : null;
        } else if (KycStatus.REJECTED.name().equalsIgnoreCase(openApplication.getPhysicalReason())) {
            rejectionMessage = easyLoanUtil.getRejectionMessage(openApplication.getPhysicalReason(), RejectionStage.QC);
            rejectionReason = openApplication.getPhysicalReason();
        }
        rejectionMessage = Objects.nonNull(rejectionMessage) ? rejectionMessage : "Please re-apply with correct shop details";
        rejectionStateDto.setRejectionReason(rejectionReason);
        rejectionStateDto.setRejectionMessage(rejectionMessage);
        return rejectionStateDto;
    }

    private AddressDetails getShopAddress(LendingApplication lendingApplication) {
        return AddressDetails.builder()
                .pincode(String.valueOf(lendingApplication.getPincode()))
                .city(lendingApplication.getCity())
                .state(lendingApplication.getState())
                .address1(lendingApplication.getShopNumber())
                .address2(lendingApplication.getStreetAddress())
                .landmark(lendingApplication.getLandmark()).build();
    }

    private ProfessionalDetails getProfessionalDetails(LendingApplication openApplication) {
        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(openApplication.getId());
        if (lendingGstDetail == null) return null;

        return ProfessionalDetails.builder()
                .profession(lendingGstDetail.getEntityType())
                .gstNumber(lendingGstDetail.getGstNumber())
                .experience(lendingGstDetail.getExperience())
                .salary(String.valueOf(lendingGstDetail.getSalary()))
                .companyName(lendingGstDetail.getCompanyName())
                .addressType(lendingGstDetail.getAddressType())
                .shopType(lendingGstDetail.getShopType())
                .build();
    }

    private String getCurrentAddress(LendingApplication lendingApplication) {
        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplication.getId());
        return lendingGstDetail != null && !StringUtils.isEmpty(lendingGstDetail.getCurrentAddress()) && "Different".equalsIgnoreCase(lendingGstDetail.getAddressType()) ? lendingGstDetail.getCurrentAddress() : null;
    }

    private boolean isShopPhotoRequired(LendingApplication openApplication) {
        if (ApplicationStatus.DRAFT.name().equalsIgnoreCase(openApplication.getStatus())) {
            List<LendingShopDocuments> lendingShopDocumentsList = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(openApplication.getMerchantId(), openApplication.getId());
            return lendingShopDocumentsList.size() < 2;
        }
        return false;
    }

    private Long getReapplyTime(LendingApplication lendingApplication) {
        Long reapplyTime = null;
        if ("rejected".equalsIgnoreCase(lendingApplication.getStatus())) {
            Integer reapplyDayDiff = null;
            if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getManualCibilReason(), RejectionStage.CIBIL, lendingApplication.getMerchantId());
            } else if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualKyc())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getManualKycReason(), RejectionStage.KYC, lendingApplication.getMerchantId());
            } else if ("REJECTED".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getPhysicalReason(), RejectionStage.QC, lendingApplication.getMerchantId());
            } else {
                reapplyDayDiff = 0;
            }
            if (Objects.nonNull(reapplyDayDiff)) {
                reapplyTime = reapplyDayDiff - LoanUtil.getDateDiffInDays(lendingApplication.getUpdatedAt(), new Date());
                reapplyTime = reapplyTime > 0 ? reapplyTime : 0;
            }
        }
        return reapplyTime;
    }

    private String shouldReapply(LendingApplication openApplication, Long reapplyTime) {

        if (ObjectUtils.isEmpty(reapplyTime)) {
            return null;
        }

        if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getStatus())) {
            if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualCibil())) {
                return Reapply.OFFER.name();
            } else if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualKyc())) {
                return Reapply.OFFER.name();
            } else if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getCkycStatus())) {
                KycStatusDTO kycStatusDTO = kycHandler.getKycStatus(openApplication.getMerchantId());
                if (KycStatus.REJECTED.equals(kycStatusDTO.getKycStatus()) && KycDocType.PAN_NO.equals(kycStatusDTO.getKycDocType())) {
                    return Reapply.PAN.name();
                } else if ("PANCARD MISMATCH".equalsIgnoreCase(openApplication.getCkycRejectionReason())) {
                    return Reapply.PAN.name();
                } else {
                    return Reapply.OFFER.name();
                }
            } else {
                return Reapply.OFFER.name();
            }
        }
        return null;
    }
        private void checkEligibility(LoanDetailsResponse loanDetailsResponse, LoanDetailsRequest request,
                Experian experian, BasicDetailsDto merchant)  {
            MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchant.getId());
            if (ObjectUtils.isEmpty(merchantResponseDTO)) {
                throw new MerchantSummaryExceptionHandler(merchant.getId().toString());
            }
            loanDetailsResponse.setPancard(experian.getPancardNumber());
            loanDetailsResponse.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
            loanDetailsResponse.setHasExperian(true);
            // TODO: 10/11/22 todo final hardcoded this bit
//        loanDetailsResponse.setEdiDaysModel(EdiModel.assignEdiModel().getNoOfEdiDaysInAWeek());
            loanDetailsResponse.setEdiDaysModel(6);
            if (loanUtil.isInternalMerchant(merchant.getId()) || easyLoanUtil.percentScaleUp(merchant.getId(), rolloutAbflPercent)) {
                loanDetailsResponse.setEdiDaysModel(7);
            }

            if (loanUtil.isInternalMerchant(merchant.getId()) || assignEdiModelFromModelAssignmentEngine) {
                loanDetailsResponse.setEdiDaysModel(iEdiModelAssignment.assignModel(merchant.getId()).getNoOfEdiDaysInAWeek());
            }
            EligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdAndLoanTypeNotTopup(merchant.getId());
            Date dateWindow = dateTimeUtil.getDatePlusDays(dateTimeUtil.getCurrentDate(), -24 * eligibilityRefreshWindow);
            Boolean isClubV2 = apiGatewayService.checkClubV2(merchant.getId());
            log.info("merchant is: {} clubV2 member: {}",merchant.getId(), isClubV2);
            loanDetailsResponse.setClubV2Member(isClubV2);
            Eligibility eligibility = null;
//        log.info("date window: {}, getCreatedAt after date Window: {} for merchant: {}", dateWindow, eligibleLoan.getCreatedAt().after(dateWindow), merchant.getId());
//        log.info("check object eligible loan: {} for merchant: {}", !ObjectUtils.isEmpty(eligibleLoan), merchant.getId());
            log.info("eligibility check begins !!! {}", merchant.getId());
            if (!ObjectUtils.isEmpty(eligibleLoan) && eligibleLoan.getCreatedAt().after(dateWindow) && !(isClubV2 && clubEligibleLoanCache)) {
                log.info("Eligible offers exist for merchant:{}", merchant.getId());
                eligibility = createEligibility(merchant.getId(),eligibleLoan);
                if (eligibility != null) {
                    log.info("eligibility is not null for merchant: {}", merchant.getId());
                    loanDetailsResponse.setEligibility(eligibility);
                    return;
                } else {
                    log.info("eligibility is null for merchant: {}", merchant.getId());
                }
            } else {
                log.info("after the date window for merchant: {}", merchant.getId());
            }
            MutableBoolean isDerog = new MutableBoolean(false);
            GlobalLimitResponse globalLimitResponse=new GlobalLimitResponse();
            try {
                globalLimitResponse = apiGatewayService.getGlobalLimit(merchant.getId());
            }
            catch (BureauCallMaskedApiException e) {
                log.error("Exception occurred for merchantId:{},execption:{}", merchant.getId(),e);
            }
            Double eligibleAmount = 0D;
            if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
                log.info("Global limit for merchant:{} is {}", merchant.getId(), globalLimitResponse.getData().getGlobalLimit());
                eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                isDerog.setValue(globalLimitResponse.getData().isDerog());
            }
            if (eligibleAmount > 0D) {
                log.info("Eligibility found for merchant:{}", merchant.getId());
                recomputeEligibleLoan(globalLimitResponse, null, merchant.getId());
                eligibility = createEligibility(merchant.getId(),eligibleLoan);
            }
            if (eligibility != null) {
                loanDetailsResponse.setEligibility(eligibility);
                return;
            }
            log.info("Eligibility not found for merchant:{}", merchant.getId());
            loanDetailsResponse.setIneligible(getIneligibleReason(merchant.getId(), isDerog, experian.getPincode(), globalLimitResponse));
            loanDetailsResponse.setChangeBankAccount(!loanUtil.isEnachBank(merchant.getId()));
        }


    private void cacheLoanDetailsData(LoanDetailsResponse loanDetailsResponse) {
        try {
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(LoanDetailsConstant.LENDING_LOAN_DETAILS_KEY_PREFIX+loanDetailsResponse.getMerchantId());
            addCacheDto.setValue(objectMapper.writeValueAsString(loanDetailsResponse));
            addCacheDto.setTtl(loanDetailsRefreshWindow);
            lendingCache.add(addCacheDto, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("exception occured while caching loan details for {} !!", LoanDetailsConstant.LENDING_LOAN_DETAILS_KEY_PREFIX+loanDetailsResponse.getMerchantId());
        }
    }

    public Eligibility createEligibility(Long merchantId,EligibleLoan eligibleLoan) {
        try {
            log.info("Creating eligibility for merchant:{}", merchantId);
            return Eligibility.builder()
                    .loanAmount(eligibleLoan.getAmount())
                    .arrangerFee(eligibleLoan.getProcessingFee())
                    .interestRate(eligibleLoan.getRateOfInterest())
                    .repaymentAmount(eligibleLoan.getRepayment())
                    .ediCount(eligibleLoan.getEdiCount())
                    .ediAmount(eligibleLoan.getEdi())
                    .tenure(eligibleLoan.getTenure())
                    .category(eligibleLoan.getCategory())
                    .loanType(eligibleLoan.getLoanType())
                    .clubV2Amount(eligibleLoan.getClubV2Amount())
                    .uniqueKey(eligibleLoan.getId())
                    .build();
        } catch (Exception e) {
            log.error("Exception in createEligibility for merchant:{}", merchantId, e);
        }
        return null;
    }

    private void addApplicationStages(LendingApplication openApplication,LoanApplicationDetails loanApplicationDetails){
        // here we are adding application stages as well
        List<LoanApplicationStage> loanApplicationStageList = new ArrayList<>();
        LoanApplicationStage loanApplicationStage=new LoanApplicationStage();
        loanApplicationStage.setStage(LoanDetailsConstant.APPLICATION_SUBMITTED);
        loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_PENDING);
        if(Objects.nonNull(openApplication.getNachLender()) && NachStatus.APPROVED.name().equalsIgnoreCase(openApplication.getNachStatus())){
            loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_SUCCESS);
        }
        loanApplicationStageList.add(loanApplicationStage);
        loanApplicationStage=new LoanApplicationStage();
        loanApplicationStage.setStage(LoanDetailsConstant.REVIEW);
        loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_PENDING);
        // in case of auto disbursal lmsStage will be null, so checking if application status is approved or not.
        if(ApplicationStatus.APPROVED.name().equalsIgnoreCase(openApplication.getStatus()) &&
                (Objects.isNull(openApplication.getLmsStage()) || "PENDING_DISBURSAL".equalsIgnoreCase(openApplication.getLmsStage()))
        ){
            loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_SUCCESS);
        }
        loanApplicationStageList.add(loanApplicationStage);
//        loanApplicationStage=new LoanApplicationStage();
//        loanApplicationStage.setStage(LoanDetailsConstant.SEND_TO_NBFC);
//        loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_PENDING);
//        if(Objects.nonNull(openApplication.getSendToNbfc()) || Objects.nonNull(openApplication.getNbfcSendDate())){
//            loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_SUCCESS);
//        }
//        loanApplicationStageList.add(loanApplicationStage);
        loanApplicationStage=new LoanApplicationStage();
        loanApplicationStage.setStage(LoanDetailsConstant.DISBURSAL);
        loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_PENDING);
        if(Objects.nonNull(openApplication.getDisburseTimestamp()) && "DISBURSED".equalsIgnoreCase(openApplication.getLoanDisbursalStatus())){
            loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_SUCCESS);
        }
        loanApplicationStageList.add(loanApplicationStage);
        loanApplicationDetails.setLoanApplicationStageList(loanApplicationStageList);
    }

    public String getIneligibleReason(Long merchantId, MutableBoolean isDerog, Integer pincode, GlobalLimitResponse globalLimitResponse) {
        log.info("Checking ineligible reason for merchant:{}", merchantId);
        try {
            if (Objects.isNull(globalLimitResponse) || Objects.isNull(globalLimitResponse.getData())) {
                log.info("Global limit response is null for merchantId: {} , {}", merchantId, globalLimitResponse);
            }
            if (Objects.nonNull(globalLimitResponse) && Objects.nonNull(globalLimitResponse.getData()) && Objects.nonNull(globalLimitResponse.getData().getRejectionType())) {
                return globalLimitResponse.getData().getRejectionType();
            }
            if (loanUtil.isOGL(pincode)) {
                log.info("OGL merchant:{}", merchantId);
                return IneligibleType.OGL.name();
            }
            if (isDerog.booleanValue()) {
                log.info("Derog merchant:{}", merchantId);
                return IneligibleType.DEROG.name();
            }
        } catch (Exception e) {
            log.error("Exception in getIneligibleReason for merchant:{}", merchantId, e);
        }
        log.info("Ineligible merchant:{}", merchantId);
        return RejectionReason.LOW_TRANSACTION.getReason();
    }

    public void recomputeEligibleLoan(GlobalLimitResponse globalLimitResponse, Double customAmount, Long merchantId) {
        if (Objects.isNull(globalLimitResponse) || Objects.isNull(globalLimitResponse.getData())) {
            log.info("Global Limit not found");
            return;
        }
        Double finalLimit = globalLimitResponse.getData().getGlobalLimit();
        String loanType = globalLimitResponse.getData().getLoanType();
        Double version = globalLimitResponse.getData().getVersion();
        try {
//            eligibleLoanDao.deleteByMerchantId(merchantId);
            List<GlobalLimitResponse.OfferDetail> offerDetails = globalLimitResponse.getData().getOfferDetails();
            offerDetails.sort(Comparator.comparingInt(GlobalLimitResponse.OfferDetail::getTenure));
            for (GlobalLimitResponse.OfferDetail offerDetail : offerDetails) {
                log.info("Tenure: {}, finalLimit: {}, loanAmount: {}, customAmount: {}", offerDetail.getTenure(), finalLimit, offerDetail.getLoanAmount(), customAmount);
                if (Objects.nonNull(customAmount) && customAmount < finalLimit && customAmount <= offerDetail.getLoanAmount()) {
                    loanUtil.calculateLoanBreakup(offerDetail, merchantId, loanType, customAmount, null, version);
                }
                if (finalLimit <= offerDetail.getMaxLoanAmount() && finalLimit <= (offerDetail.getLoanAmount())) {
                    loanUtil.calculateLoanBreakup(offerDetail, merchantId, loanType, finalLimit, null, version);
                }
            }
//            eligibleLoanDao.deleteGreaterOffersByMerchantId(merchantId, finalLimit);
        } catch (Exception e) {
            log.error("Exception while recomputing eligible loan for merchant:{}", merchantId, e);
        }
    }

    private boolean percentScaleUp(Long merchantId, Integer percent) {
        log.info("checking percent scale up for merchant: {} with percent: {}", merchantId, percent);
        switch (percent) {
            case 1 :
                if (isOnePercentRollOutMerchant(merchantId)){
                    return true;
                }
                break;
            case 5 :
                if(isFivePercentRollOutMerchant(merchantId)){
                    return true;
                }
                break;
            case 10 :
                if (isTenPercentRollOutMerchant(merchantId)) {
                    return true;
                }
                break;
            case 20 :
                if (isTwentyPercentRollOutMerchant(merchantId)) {
                    return true;
                }
                break;
            case 50 :
                if (isFiftyPercentRollOutMerchant(merchantId)) {
                    return true;
                }
                break;
            case 100 :
                return true;
        }

        return false;
    }

    private Date getThresholdDate(Long merchantId) throws ParseException {
        String DATE_FORMAT = "yyyy-MM-dd hh:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        Date date = null;
        if(screenRedesignRolloutPercent == 1 && !ObjectUtils.isEmpty(screenRedesignOnePercentRolloutDate)){
            date = sdf.parse(screenRedesignOnePercentRolloutDate);
        }
        else if(screenRedesignRolloutPercent == 5){
            if(isOnePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignOnePercentRolloutDate)) date = sdf.parse(screenRedesignOnePercentRolloutDate);
            else date = sdf.parse(screenRedesignFivePercentRolloutDate);
        }
        else if(screenRedesignRolloutPercent == 10){
            if(isOnePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignOnePercentRolloutDate)) date = sdf.parse(screenRedesignOnePercentRolloutDate);
            else if(isFivePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignFivePercentRolloutDate)) date = sdf.parse(screenRedesignFivePercentRolloutDate);
            else date = sdf.parse(screenRedesignTenPercentRolloutDate);
        }
        else if(screenRedesignRolloutPercent == 20){
            if(isOnePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignOnePercentRolloutDate)) date = sdf.parse(screenRedesignOnePercentRolloutDate);
            else if(isFivePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignFivePercentRolloutDate)) date = sdf.parse(screenRedesignFivePercentRolloutDate);
            else if(isTenPercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignTenPercentRolloutDate)) date = sdf.parse(screenRedesignTenPercentRolloutDate);
            else date = sdf.parse(screenRedesignTwentyPercentRolloutDate);
        }
        else if(screenRedesignRolloutPercent == 50){
            if(isOnePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignOnePercentRolloutDate)) date = sdf.parse(screenRedesignOnePercentRolloutDate);
            else if(isFivePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignFivePercentRolloutDate)) date = sdf.parse(screenRedesignFivePercentRolloutDate);
            else if(isTenPercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignTenPercentRolloutDate)) date = sdf.parse(screenRedesignTenPercentRolloutDate);
            else if(isTwentyPercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignTwentyPercentRolloutDate)) date = sdf.parse(screenRedesignTwentyPercentRolloutDate);
            else date = sdf.parse(screenRedesignFiftyPercentRolloutDate);
        }
        else{
            if(isOnePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignOnePercentRolloutDate)) date = sdf.parse(screenRedesignOnePercentRolloutDate);
            else if(isFivePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignFivePercentRolloutDate)) date = sdf.parse(screenRedesignFivePercentRolloutDate);
            else if(isTenPercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignTenPercentRolloutDate)) date = sdf.parse(screenRedesignTenPercentRolloutDate);
            else if(isTwentyPercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignTwentyPercentRolloutDate)) date = sdf.parse(screenRedesignTwentyPercentRolloutDate);
            else if(isFiftyPercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignFiftyPercentRolloutDate)) date = sdf.parse(screenRedesignFiftyPercentRolloutDate);
            else date = sdf.parse(screenRedesignHundredPercentRolloutDate);
        }
        log.info("threshold date for {} : {}", merchantId, date);
        return date;
    }

    private boolean isOnePercentRollOutMerchant(Long merchantId){
        if(merchantId % 100 == 11)return true;
        return false;
    }

    private boolean isFivePercentRollOutMerchant(Long merchantId){
        if(merchantId % 10 == 1 && (merchantId % 100 - 1)/10 % 2 == 0)return true;
        return false;
    }

    private boolean isTenPercentRollOutMerchant(Long merchantId){
        if(merchantId % 10 == 1)return true;
        return false;
    }

    private boolean isTwentyPercentRollOutMerchant(Long merchantId){
        if(String.valueOf(merchantId).endsWith("1") || String.valueOf(merchantId).endsWith("2"))return true;
        return false;
    }

    private boolean isFiftyPercentRollOutMerchant(Long merchantId){
        if (String.valueOf(merchantId).endsWith("1") || String.valueOf(merchantId).endsWith("2") || String.valueOf(merchantId).endsWith("3") ||
                String.valueOf(merchantId).endsWith("4") || String.valueOf(merchantId).endsWith("5")
        ) {
            return true;
        }
        return false;
    }
}
