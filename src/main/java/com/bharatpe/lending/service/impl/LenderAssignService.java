package com.bharatpe.lending.service.impl;

import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.BankStatementSessionDetailsDao;
import com.bharatpe.lending.common.dao.Gst3bSessionDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.entity.BankStatementSessionDetails;
import com.bharatpe.lending.common.entity.Gst3bSessionDetails;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.service.ILenderAssignService;
import com.bharatpe.lending.common.service.merchant.dto.*;
import com.bharatpe.lending.common.service.merchant.service.*;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.entity.*;
import com.bharatpe.lending.enums.EnachMode;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.loanV2.handlers.*;
import com.bharatpe.lending.loanV3.dto.ExtractedRulesAndLendersDTO;
import com.bharatpe.lending.loanV3.utils.OfferUtils;
import com.bharatpe.lending.service.*;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Slf4j
@Service
public class LenderAssignService implements ILenderAssignService {

    @Autowired
    LenderEligiblePincodesDao lenderEligiblePincodesDao;

    @Autowired
    LenderAssignmentRulesDao lenderAssignmentRulesDao;

    @Autowired
    LenderDisbursalLimitsDao lenderDisbursalLimitsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Value("${whitelisted.topup.lenders}")
    String topupLenders;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Value("${abfl.rollout.percent:10}")
    Integer rolloutAbflPercent;

    @Value("${age.check.lenders}")
    String ageCheckLenders;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    BureauHandler bureauHandler;

    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;

    @Value("${gst_offer_lender}")
    String gstOfferLender;

    @Value("${lender.assign.rollout}")
    Integer lenderAssignmentNewFlowRollOutPercent;

    @Value("${lender.eligible.pincode.check:PIRAMAL}")
    List<String> lenderEligiblePincodeCheckList;

    @Autowired
    DateTimeUtil dateTimeUtil;

    @Autowired
    AssignmentRuleUtils assignmentRuleUtils;

    @Value("${thresholdVinatage:151}")
    Integer thresholdVintage;

    @Value("${default.assign.abfl:false}")
    boolean defaultAssignAbfl;

    @Value("${default.assign.lender:PIRAMAL}")
    String defaultAssignLender;

    @Value("${run.internal.rules:false}")
    boolean runInternalRules;

    @Autowired
    BankStatementSessionDetailsDao bankStatementSessionDetailsDao;

    @Autowired
    Gst3bSessionDetailsDao gst3bSessionDetailsDao;

    @Override
    public LendingEnum.LENDER assignLender(EdiModel ediModel) {
        return null;
    }

    public String lenderAssignmentHandler(LendingApplication application, EdiModel ediModel) {
        refreshDisbursalLimitsForLender();
        // Ensure rule util here: todo
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(application.getMerchantId());
        Double bureauScore = 0D;
        String riskSegment = "";
        String riskGroupLike = null;
        String pincodeColor = null;
        Boolean isGstOffer = null;
        Long vintage = 0L;
        if(Objects.nonNull(lendingRiskVariables)){
            bureauScore = Objects.nonNull(lendingRiskVariables.getBureauScore()) ? lendingRiskVariables.getBureauScore() : 0D;
            riskSegment = Objects.nonNull(lendingRiskVariables.getRiskSegment()) ? "%" + lendingRiskVariables.getRiskSegment() + "%" : "";
            riskGroupLike = Objects.nonNull(lendingRiskVariables.getRiskGroup()) ? "%" + lendingRiskVariables.getRiskGroup() + "%" : "";
            pincodeColor = Objects.nonNull(lendingRiskVariables.getPincodeColor()) ? "%"+lendingRiskVariables.getPincodeColor().name() + "%" : "";
            isGstOffer = Objects.nonNull(lendingRiskVariables.getGstAffectedOffer())? lendingRiskVariables.getGstAffectedOffer() : Boolean.FALSE;
            vintage = Objects.nonNull(lendingRiskVariables.getVintage())? lendingRiskVariables.getVintage() : 0L;
        }
        String tenure = "%" + application.getTenureInMonths() + "%";
        try {
            log.info("Lender assignment parameters -> bureau:{}, loanType:{}, tenure:{}, loanAmount:{}, riskGroup:{}, pincodeColor:{}", bureauScore, riskSegment, application.getTenure(),
                    application.getLoanAmount(), riskGroupLike, pincodeColor);
            List<String> lenders = new ArrayList<>();
            String decidedLender = null;
            List<LenderAssignmentRules> ruleList=lenderAssignmentRulesDao.fetchEligibleRules(application.getLoanAmount(), bureauScore, riskSegment, tenure, riskGroupLike, pincodeColor);
            if (loanUtil.isInternalMerchant(application.getMerchantId()) || runInternalRules) {
                ruleList=lenderAssignmentRulesDao.fetchEligibleRulesForInternal(application.getLoanAmount(), bureauScore, riskSegment, tenure, riskGroupLike, pincodeColor);
            }
            log.info("Fetched Rules:{}", ruleList);
            if(ObjectUtils.isEmpty(ruleList)){
                return null;
            }
            lenders = getLenderList(ruleList, ediModel, application.getLender(), application.getMerchantId(),vintage);
            try {
                if (!CollectionUtils.isEmpty(lenders)) {
                    ListIterator<String> iterator = lenders.listIterator();
                    while (iterator.hasNext()) {
                        String lender = iterator.next().toUpperCase();
                        if (lenderEligiblePincodeCheckList.contains(lender)) {
                            LenderEligiblePincodes lenderEligiblePincodes = lenderEligiblePincodesDao.findByLenderAndPincodeAndStatus(
                                    lender, lendingRiskVariables.getPincode(), LenderEligiblePincodes.LenderEligiblePincodesStatus.ACTIVE
                            );
                            if (ObjectUtils.isEmpty(lenderEligiblePincodes)) {
                                log.info("removing lender : {} from eligible as pincode : {} not serviceable", lender, lendingRiskVariables.getPincode());
                                iterator.remove();
                            }
                        }
                        if (!baseChecksPassedForLenders(application,lender)){
                            log.info("only adhaar mode available for nach by bank, skipping {} for {}", lender, application.getId());
                            iterator.remove();
                        }
                    }
                }
            } catch (Exception exception) {
                log.error("exception while custom pincode check for lender for application id : {}", application.getId(), exception);
            }
            decidedLender = getLender(application, lenders, ediModel, isGstOffer, riskSegment.substring(1, riskSegment.length()-1));
            log.info("lender to be assigned: {} {}", decidedLender, application.getId());
            return decidedLender;
        } catch(Exception ex){
            log.error("Exception occurred while assigning lender : {}, {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            return null;
        }
    }

    private boolean baseChecksPassedForLenders(LendingApplication lendingApplication, String lender) {
        if (Lender.PIRAMAL.name().equalsIgnoreCase(lender)) {
            String enachMode = loanUtil.getEnachBankMode(lendingApplication.getMerchantId());
            if ("ADHAAR".equalsIgnoreCase(enachMode) && lendingApplication.getTenureInMonths() >= 12) {
                return false;
            }
        }
        return true;
    }

    public LendingApplication assignLender(LendingApplication application, EdiModel ediModel, BasicDetailsDto merchantDetails) {

        // for topup flow
        if("TOPUP".equals(application.getLoanType())){
            log.info("Assigning lender for topup application:{}", application.getId());
            assignTopupLender(application);
            return application;
        }

        if (ObjectUtils.isEmpty(application)) {
            throw new RuntimeException("Application not found for merchant:" + application.getMerchantId());
        }
        String decidedLender = null;
        decidedLender = checkForcefulAssignedLenderForMerchant(application);
        if(!ObjectUtils.isEmpty(decidedLender)) {
            saveLenderChangeAudit(application, decidedLender);
            String oldLender = application.getLender();
            application.setLender(decidedLender);
            updateOfferDetailsInApplication(application,LenderOffDays.valueOf(application.getLender()).getEdiModel(), oldLender);
            lendingApplicationDao.save(application);
            return application;
        }
        if (loanUtil.isInternalMerchant(application.getMerchantId()) && ObjectUtils.isEmpty(application.getLender())
                && !ObjectUtils.isEmpty(application.getExternalLoanId())) {
            log.info("internal merchant lender assignment, skipping all rules {}", application.getMerchantId());
            decidedLender =  defaultAssignAbfl ? Lender.ABFL.name() : defaultAssignLender;
        } else {
            BankStatementSessionDetails aaSession = bankStatementSessionDetailsDao.findFirstByMerchantIdAndTypeAndStatusOrderByIdDesc(application.getMerchantId(), "ACCOUNT_AGGREGATOR", BankStatementSessionStatus.SUCCESS);
            if(!ObjectUtils.isEmpty(aaSession)) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(aaSession.getCreatedAt());
                calendar.add(Calendar.MONTH, 1);
                if (new Date().compareTo(calendar.getTime()) < 0) {
                    decidedLender = aaSession.getLender().name();
                    saveLenderChangeAudit(application, decidedLender);
                    String oldLender = application.getLender();
                    application.setLender(decidedLender);
                    updateOfferDetailsInApplication(application,LenderOffDays.valueOf(application.getLender()).getEdiModel(), oldLender);
                    lendingApplicationDao.save(application);
                    return application;
                }
            }
            decidedLender = lenderAssignmentHandler(application, ediModel);
            if (Lender.ABFL.name().equals(decidedLender)) {
                decidedLender = updateLenderForGstAndBS(application, ediModel, decidedLender);
            }
        }
        if(!ObjectUtils.isEmpty(decidedLender)){
            saveLenderChangeAudit(application, decidedLender);
        }else{
            EdiModel modifiedEdiModel = ediModel.getNoOfEdiDaysInAWeek() == 6 ? EdiModel.SEVEN_DAY_MODEL:EdiModel.SIX_DAY_MODEL;
            log.info("EDI MODEL CHANGED TO -> {}", modifiedEdiModel);
            // ModifyEdiModel
            decidedLender = lenderAssignmentHandler(application, modifiedEdiModel);
            if(Lender.ABFL.name().equals(decidedLender)) {
                decidedLender = updateLenderForGstAndBS(application, ediModel, decidedLender);
            }
            if(ObjectUtils.isEmpty(decidedLender)){
                decidedLender = assignFallackLender(application, ediModel);
            } else{
                modifyEdiModel(application, modifiedEdiModel);
            }
            saveLenderChangeAudit(application, decidedLender);
        }

        // exclude disrbusal failed cases to not assign on abfl
        if (Lender.ABFL.name().equals(decidedLender) && loanUtil.abflExcludedMerchants().contains(application.getMerchantId())) {
            decidedLender = assignFallackLender(application, LenderOffDays.valueOf(decidedLender).getEdiModel());
            saveLenderChangeAudit(application, decidedLender);
        }

        // change lender if it is LDC and nachMode is adhaar
        if (Lender.LDC.name().equals(decidedLender) && EnachMode.ADHAAR.name().equalsIgnoreCase(loanUtil.getEnachBankMode(application.getMerchantId()))) {
            decidedLender = Lender.LIQUILOANS_NBFC.name();
            saveLenderChangeAudit(application, decidedLender);
        }

        if(additionalChecksFailed(application, Lender.valueOf(decidedLender), merchantDetails)){
            decidedLender = assignFallackLender(application, LenderOffDays.valueOf(decidedLender).getEdiModel());
            saveLenderChangeAudit(application, decidedLender);
        }
        String oldLender = application.getLender();
        application.setLender(decidedLender);
        updateOfferDetailsInApplication(application,LenderOffDays.valueOf(decidedLender).getEdiModel(), oldLender);
        return lendingApplicationDao.save(application);
    }

    public void saveLenderChangeAudit(LendingApplication lendingApplication, String lender){
        LendingAuditTrial auditLender = new LendingAuditTrial();
        auditLender.setApplicationId(lendingApplication.getId());
        auditLender.setMerchantId(lendingApplication.getMerchantId());
        auditLender.setType("LENDER_SET");
        auditLender.setLoanId("BPL"+lendingApplication.getId());
        auditLender.setOldStatus(lendingApplication.getLender());
        auditLender.setNewStatus(lender);
        log.info("Audit Trail: {}", auditLender);
        lendingAuditTrialDao.save(auditLender);
    }



    public String getLender(LendingApplication lendingApplication, List<String> lenders, EdiModel ediModel, Boolean isGstOffer, String riskSegment){
        boolean flag = false;
        log.info("Implementing logic for lender assignment.");
        LendingLenderQuota kycSkippableLender=null;
        LendingLenderQuota nachSkippableLender=null;
        LendingLenderQuota assignedLender = null;
        List<LendingLenderQuota> toBeAssignedLenders = new ArrayList<>();
        if(!ObjectUtils.isEmpty(lenders)){
            toBeAssignedLenders = lenderDisbursalLimitsDao.fetchEligibleLenderLimits(lenders, lendingApplication.getLoanAmount());
        }
        log.info("Final eligible lenders:{}", toBeAssignedLenders);
        if(ObjectUtils.isEmpty(toBeAssignedLenders)){
            log.info("No Eligible lenders found. Changing ediModel.");
            return null;
        }
        //new flow
        for(LendingLenderQuota lender: toBeAssignedLenders){
            log.info("Lender:{}", lender.getLender());

            if(isGstOffer){
                log.info("Offer increased due to gst for merchant:{}", lendingApplication.getMerchantId());
                if(!LenderOffDays.valueOf(gstOfferLender).getEdiModel().equals(ediModel)){
                    modifyEdiModel(lendingApplication, LenderOffDays.valueOf(gstOfferLender).getEdiModel());
                }
                return gstOfferLender;
            }

            LendingApplicationKycDetails kycDetails = lendingApplicationKycDetailsDao.findSuccessKycDetails(lendingApplication.getMerchantId(), lender.getLender());
            if("REPEAT".equalsIgnoreCase(riskSegment) && Objects.nonNull(kycDetails)){
                //skip KYC
                log.info("merchant {}  can skip KYC done on:{} for lender:{}", lendingApplication.getMerchantId(),kycDetails.getConsentDate() ,lender.getLender());
                kycSkippableLender=lender;
                flag=true;
            }
            if(loanUtil.isEligibleForNachSkip(lendingApplication, lender.getLender())){
                //skip NACH
                log.info("merchant {}  can skip NACH for lender:{}", lendingApplication.getMerchantId(), lender.getLender());
                nachSkippableLender=lender;
                flag = true;
            }
            
            if(Objects.nonNull(nachSkippableLender) && Objects.nonNull(kycSkippableLender) && nachSkippableLender.equals(kycSkippableLender)){
                log.info("merchant {} can skip both KYC and NACH for lender: {}", lendingApplication.getMerchantId(), kycSkippableLender);
                break;
            }
        }

        if(easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), lenderAssignmentNewFlowRollOutPercent)){
            //new flow
            if(ObjectUtils.isEmpty(assignedLender)){
                assignedLender = flag ? (ObjectUtils.isEmpty(nachSkippableLender)?kycSkippableLender:nachSkippableLender):toBeAssignedLenders.get(0);
                LendingApplicationDetails lendingApplicationDetails=lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
                if(ObjectUtils.isEmpty(lendingApplicationDetails)){
                    lendingApplicationDetails = new LendingApplicationDetails();
                    lendingApplicationDetails.setApplicationId(lendingApplication.getId());
                }
                lendingApplicationDetails.setIsKycSkip(Objects.nonNull(kycSkippableLender) && assignedLender.equals(kycSkippableLender));
            }
        } else{
            assignedLender=toBeAssignedLenders.get(0);
        }

        updateLenderLimits(assignedLender, lendingApplication);
        ediModelAudit(lendingApplication, LenderOffDays.valueOf(assignedLender.getLender()).getEdiModel());
        return assignedLender.getLender();


        //old flow
//        assignedLender = toBeAssignedLenders.get(0);
//        log.info("Selected Lender : {}", assignedLender);
//        EdiModel ediModel1 = LenderOffDays.valueOf(assignedLender.getLender()).getEdiModel();
//        log.info("Selected EDI Model : {}", ediModel1);
//        ediModelAudit(lendingApplication, ediModel1);
//
//        //updating lender limits
//        LendingEnum.LENDER lender = LendingEnum.LENDER.valueOf(assignedLender.getLender());
//        Double updatedAssignedAmount = assignedLender.getAssignedAmount() + lendingApplication.getLoanAmount();
//        assignedLender.setAssignedAmount(updatedAssignedAmount);
//        Double updatedBalance = ObjectUtils.isEmpty(assignedLender.getRemainingBalance()) ? null:assignedLender.getRemainingBalance() - lendingApplication.getLoanAmount();
//        assignedLender.setRemainingBalance(updatedBalance);
//        lenderDisbursalLimitsDao.save(assignedLender);
//        return lender.name();
    }

    public void updateLenderLimits(LendingLenderQuota lender, LendingApplication lendingApplication){
        Double updatedAssignedAmount = lender.getAssignedAmount() + lendingApplication.getLoanAmount();
        lender.setAssignedAmount(updatedAssignedAmount);
        Double updatedBalance = ObjectUtils.isEmpty(lender.getRemainingBalance()) ? null:lender.getRemainingBalance() - lendingApplication.getLoanAmount();
        lender.setRemainingBalance(updatedBalance);
        lenderDisbursalLimitsDao.save(lender);
    }

    public void ediModelAudit(LendingApplication lendingApplication, EdiModel ediModel){
        LendingApplicationDetails lenderAudit = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        if(ObjectUtils.isEmpty(lenderAudit)){
            lenderAudit = new LendingApplicationDetails();
            lenderAudit.setApplicationId(lendingApplication.getId());
            lenderAudit.setCreatedAt(new Date());
            lenderAudit.setStage(LenderAssociationStages.INIT.name());
            lenderAudit.setEdiModel(ediModel.name());
        }
        else if(ObjectUtils.isEmpty(lenderAudit.getEdiModel())){
            lenderAudit.setEdiModel(ediModel.name());
            lenderAudit.setEdiModelModified(Boolean.FALSE);
        }
        else if(!ediModel.name().equals(lenderAudit.getEdiModel())){
            lenderAudit.setEdiModel(ediModel.name());
            lenderAudit.setEdiModelModified(Boolean.TRUE);
        }
        lendingApplicationDetailsDao.save(lenderAudit);
    }

    public void refreshDisbursalLimitsForLender(){
        log.info("Refreshing Weekly Disbursal Limits");
        Double disbursedAmount = lenderDisbursalLimitsDao.fetchDisbursedCount();
        LendingLenderQuota weeklyTarget = lenderDisbursalLimitsDao.findByLender("WEEKLY_TARGET");
        log.info("Disbursed Amount: {}, TARGET: {}", disbursedAmount, weeklyTarget.getTotalWeeklyAmount());
        if(disbursedAmount >= weeklyTarget.getTotalWeeklyAmount()){
            List<LendingLenderQuota> quotaList = lenderDisbursalLimitsDao.findAll();
            for(LendingLenderQuota quota : quotaList){
                if("WEEKLY_TARGET".equals(quota.getLender())) {
                    continue;
                }
                quota.setRemainingBalance(quota.getTotalWeeklyAmount());
                quota.setAssignedAmount(0D);
            }
            lenderDisbursalLimitsDao.saveAll(quotaList);
        }
    }

    public List<LenderAssignmentRules> getAllActiveRules(){
        log.info("Fetching all Active Rules");
        return lenderAssignmentRulesDao.findByIsActive(Boolean.TRUE);
    }

    public List<LendingLenderQuota> getAllLenderLimits(){
        log.info("Fetching all Lender limits");
        return lenderDisbursalLimitsDao.findAll();
    }

    public LenderAssignmentRules updateRules(LenderAssignmentRules lenderAssignmentRules){
        log.info("Updating rule with ID: {}", lenderAssignmentRules.getId());
        if(ObjectUtils.isEmpty(lenderAssignmentRules.getId())){
            return lenderAssignmentRulesDao.save(lenderAssignmentRules);
        }
        Optional<LenderAssignmentRules> rule = lenderAssignmentRulesDao.findById(lenderAssignmentRules.getId());
        Long id = lenderAssignmentRules.getId();
        if(rule.isPresent()){
            LenderAssignmentRules updatedRule = new LenderAssignmentRules(lenderAssignmentRules.getLender(), lenderAssignmentRules.getLoanType(),
                    lenderAssignmentRules.getTenure(), lenderAssignmentRules.getMinBureauScore(), lenderAssignmentRules.getMaxBureauScore(), lenderAssignmentRules.getMinAmount(),
                    lenderAssignmentRules.getMaxAmount(), lenderAssignmentRules.getDefault(), lenderAssignmentRules.getActive(), lenderAssignmentRules.getPincodeColor(), lenderAssignmentRules.getRiskGroup());
            updatedRule.setId(id);
            updatedRule.setCreatedAt(rule.get().getCreatedAt());
            return lenderAssignmentRulesDao.save(updatedRule);
        }
        return null;
    }

    public LendingLenderQuota updateLenderLimits(LendingLenderQuota limit){
        log.info("Updating lender limit with ID: {}", limit.getId());
        if(ObjectUtils.isEmpty(limit.getId())){
            limit.setEdiModel(LenderOffDays.valueOf(LendingEnum.LENDER.valueOf(limit.getLender()).name()).getEdiModel().name());
            return lenderDisbursalLimitsDao.save(limit);
        }
        Optional<LendingLenderQuota> limit1 = lenderDisbursalLimitsDao.findById(limit.getId());
        Long id = limit.getId();
        if(limit1.isPresent()){
            LendingLenderQuota updatedLimit = new LendingLenderQuota(limit.getLender(), limit.getTotalWeeklyAmount(),
                    limit.getRemainingBalance(), limit.getAssignedAmount(), null);
            updatedLimit.setCreatedAt(limit1.get().getCreatedAt());
            updatedLimit.setId(id);
            updatedLimit.setEdiModel(LenderOffDays.valueOf(LendingEnum.LENDER.valueOf(limit1.get().getLender()).name()).getEdiModel().name());
            return lenderDisbursalLimitsDao.save(updatedLimit);
        }
        return null;
    }

    public String assignLenderAndEdiModel(Long applicationId, String ediModel){
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
        if(lendingApplication.isPresent()){

            log.info("EDI MODEL -> {}", EdiModel.valueOf(ediModel));
            return assignLender(lendingApplication.get(), EdiModel.valueOf(ediModel), null).getLender();
//            return null;
        }
        return "Application Not Found.";
    }

    List<String> getLenderList(List<LenderAssignmentRules> lenderAssignmentRules, EdiModel ediModel, String assignedLender, Long merchantId, Long vintage){
        log.info("Assigned Lender: {}  EdiModel: {}", assignedLender, ediModel );
        List<String> eligibleLenders = new ArrayList<>();
        List<String> ageCheckLenderList = Arrays.asList(ageCheckLenders.split(","));
        Integer age = apiGatewayService.getMerchantAge(merchantId);
        log.info("lender assignment rules: {}", lenderAssignmentRules);
        log.info("is internal merchant", loanUtil.isInternalMerchant(merchantId));
        for(LenderAssignmentRules rule:lenderAssignmentRules){
            String lender = rule.getLender();
            log.info("running skip check for lender {} for  {}", lender, merchantId);
            if(ObjectUtils.isEmpty(ediModel) || ediModel.name().equals(LenderOffDays.valueOf(lender).getEdiModel().name())){
                // in case lender is to be changed.
                if(!ObjectUtils.isEmpty(assignedLender) && rule.getLender().equals(assignedLender)){
                    log.info("lender change workflow, skip {} for {}", lender, merchantId);
                    continue;
                }
                if (ageCheckLenderList.contains(rule.getLender()) && !ObjectUtils.isEmpty(age) && age != 0
                        && (age < 21 || age > 65)) {
                    log.info("age checks failed for {}", merchantId);
                    continue;
                }
                if (Lender.PIRAMAL.name().equalsIgnoreCase(lender) && vintage < thresholdVintage && (!loanUtil.isInternalMerchant(merchantId) || runInternalRules)){
                    log.info("Can't assign PIRAMAL as vintage {} is less than threshold vintage {}",vintage,thresholdVintage);
                    continue;
                }
                log.info("adding {} to the eligible list", lender);
                eligibleLenders.add(lender);
            }
        }
        log.info("Eligible Lenders: {}", eligibleLenders);
        return eligibleLenders;
    }

    public Lender modifyLender(Long applicationId){
        Optional<LendingApplication> application = lendingApplicationDao.findById(applicationId);
        if(application.isPresent()){
            log.info("Modifying lender for application:{}", application.get().getId());
            List<LendingAuditTrial> auditLenderList = lendingAuditTrialDao.findByApplicationIdAndMerchantIdAndType(application.get().getId(),
                    application.get().getMerchantId(), "LENDER_SET");
            if(auditLenderList.size()>=2){
                log.info("Lender already changed twice for application: {}", application.get().getId());
                EdiModel ediModel = LenderOffDays.valueOf(Lender.LIQUILOANS_P2P.name()).getEdiModel();
//                EdiModel modifiedEdiModel = ediModel.getNoOfEdiDaysInAWeek() == 6 ? EdiModel.SEVEN_DAY_MODEL:EdiModel.SIX_DAY_MODEL;
                LendingApplicationDetails ediDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
                if (!ediDetails.getEdiModel().equals(ediModel.name())) {
                    modifyEdiModel(application.get(), ediModel);
                }
                String oldLender = application.get().getLender();
                application.get().setLender(Lender.LIQUILOANS_P2P.name());
                updateOfferDetailsInApplication(application.get(),ediModel, oldLender);
                lendingApplicationDao.save(application.get());
                return Lender.LIQUILOANS_P2P;
            }
            EdiModel ediModel = LenderOffDays.valueOf(application.get().getLender()).getEdiModel();
            assignLender(application.get(), ediModel, null);
            return Lender.valueOf(application.get().getLender());
        }
        log.info("Application with id:{} not found.", applicationId);
        return null;
    }

    public void modifyEdiModel(LendingApplication application, EdiModel modifiedModel){
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(application.getId());
        if(!ObjectUtils.isEmpty(lendingApplicationDetails)){
            lendingApplicationDetails.setEdiModel(modifiedModel.name());
            lendingApplicationDetails.setEdiModelModified(Boolean.TRUE);
            lendingApplicationDetails.setUpdatedAt(new Date());
            lendingApplicationDetails.setStage(LenderAssociationStages.INIT.name());
            lendingApplicationDetailsDao.save(lendingApplicationDetails);
        }

        log.info("Modifying Edi Model for application:{}", application.getId());
    }

    public void updateLenderLimitsForRejectedLoans(Long applicationId){
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
        if(lendingApplication.isPresent()){
            if(ObjectUtils.isEmpty(lendingApplication.get().getLender())){
                log.info("Lender not found in application:{}", applicationId);
                return;
            }
            LendingLenderQuota lender = lenderDisbursalLimitsDao.findByLender(lendingApplication.get().getLender());
            log.info("Updating balance for lender:{} for loan amount:{}", lendingApplication.get().getLender(), lendingApplication.get().getLoanAmount());
            lender.setAssignedAmount(lender.getAssignedAmount() - lendingApplication.get().getLoanAmount());
            lender.setRemainingBalance(lender.getRemainingBalance() + lendingApplication.get().getLoanAmount());
            lenderDisbursalLimitsDao.save(lender);
        }
    }

    public String assignFallackLender(LendingApplication lendingApplication, EdiModel ediModel){
        log.info("Assigning fallback lender");
        LendingLenderQuota fallbackLender = lenderDisbursalLimitsDao.findByEdiModelIsNull();
        if(ObjectUtils.isEmpty(fallbackLender)){
            modifyEdiModel(lendingApplication, LenderOffDays.valueOf(Lender.LIQUILOANS_P2P.name()).getEdiModel());
            return Lender.LIQUILOANS_P2P.name();
        }
        if(!LenderOffDays.valueOf(fallbackLender.getLender()).getEdiModel().equals(ediModel)){
            modifyEdiModel(lendingApplication, LenderOffDays.valueOf(fallbackLender.getLender()).getEdiModel());
        }
        fallbackLender.setAssignedAmount(fallbackLender.getAssignedAmount() + lendingApplication.getLoanAmount());
        lenderDisbursalLimitsDao.save(fallbackLender);
        return fallbackLender.getLender();
    }

    public String assignTopupLender(LendingApplication lendingApplication){
        log.info("Assigning lender for topup loan for application:{}", lendingApplication.getId());
        String lender = null;
        if("ALL".equals(topupLenders)){
//            lender = assignLender(lendingApplication, EdiModel.SIX_DAY_MODEL).getLender();
        }
        else if("NONE".equals(topupLenders)){
            lender = Lender.LDC.name();
        }
        else{
            String[] lenders = topupLenders.split(",");
            List<String> topupLenders = Arrays.asList(lenders);
            log.info("topup lenders:{}", topupLenders);
            LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(lendingApplication.getMerchantId(), "ACTIVE");
            if(topupLenders.contains(activeLoan.getNbfc())){
                lender = lenderMapper(activeLoan.getNbfc());
                lendingApplication.setLender(lender);
                lendingApplicationDao.save(lendingApplication);
                saveLenderChangeAudit(lendingApplication, lender);
                ediModelAudit(lendingApplication, LenderOffDays.valueOf(lender).getEdiModel());
            }else {
                log.error("Lender could not be assigned for application:{}", lendingApplication.getId());
                throw new RuntimeException("Lender not same/Not eligible for topup loans");
            }
        }
        return lender;
    }

    public String lenderMapper(String prevLender){
        if("LDC".equals(prevLender)) return "LDC";
        if("LIQUILOANS_P2P".equals(prevLender) || "LIQUILOANS_P2P_OF".equals(prevLender) || "LIQUILOANS_NBFC".equals(prevLender)) return "LIQUILOANS_P2P";
        return null;
    }

    public void updateOfferDetailsInApplication(LendingApplication lendingApplication, EdiModel ediModel, String oldLender) {
        try {
            Long currentPayableDays = (long) OfferUtils.getEdiDays(lendingApplication.getTenureInMonths(), ediModel);
            if (currentPayableDays == lendingApplication.getPayableDays()) {
                log.info("skipping updated offer as offer remains same for {}", lendingApplication.getId());
                return;
            }
            log.info("modifying application details post lender change for {}", lendingApplication.getId());
            Long payableDays = (long) OfferUtils.getEdiDays(lendingApplication.getTenureInMonths(), ediModel);
            Double interestAmt = (lendingApplication.getLoanAmount() * (lendingApplication.getInterestRate() * lendingApplication.getTenureInMonths()) / 100) ;
            Double edi = Math.ceil((lendingApplication.getLoanAmount() + interestAmt) / payableDays);
            Double repayment = edi * payableDays;
            lendingApplication.setRepayment(repayment);
            lendingApplication.setEdi(edi);
            lendingApplication.setPayableDays(payableDays);
            lendingApplicationDao.save(lendingApplication);

            LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
            lendingAuditTrial.setApplicationId(lendingApplication.getId());
            lendingAuditTrial.setLoanId(ObjectUtils.isEmpty(lendingApplication.getExternalLoanId())?"":lendingApplication.getExternalLoanId());
            lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
            lendingAuditTrial.setType("OFFER_MODIFIED_LENDER_CHANGE");
            lendingAuditTrial.setOldStatus("OLD_LENDER" + oldLender);
            lendingAuditTrial.setNewStatus("NEW_LENDER" + lendingApplication.getLender());
            lendingAuditTrialDao.save(lendingAuditTrial);
        } catch (Exception e) {
            log.error("exception while updating applicationDetails {} {}",lendingApplication.getId(), e.getMessage());
        }
    }

    public boolean additionalChecksFailed(LendingApplication lendingApplication, Lender lender, BasicDetailsDto merchantDetails){
        log.info("Running additional checks for lender:{}", lender);
        boolean flag = false;
        if(Lender.ABFL.equals(lender)){
            flag = ObjectUtils.isEmpty(lendingApplication.getExternalLoanId());
            if(ObjectUtils.isEmpty(merchantDetails)){
                merchantDetails=merchantService.fetchMerchantDetails(lendingApplication.getMerchantId()).getMerchantDetail();
            }
            BureauResponseDTO responseDTO = null;
            if(!ObjectUtils.isEmpty(merchantDetails)){
                responseDTO = bureauHandler.getBureauData(merchantDetails.getPanNumber(), merchantDetails.getId(), merchantDetails.getMobile(), 30L);
            }
            if(ObjectUtils.isEmpty(responseDTO) || ObjectUtils.isEmpty(responseDTO.getVariables()) || ObjectUtils.isEmpty(responseDTO.getVariables().getMaxDpd6Months())){
                flag = false;
            } else{
                flag =  responseDTO.getVariables().getMaxDpd6Months()>=30;
            }
        }
        return flag;
    }

    private String updateLenderForGstAndBS(LendingApplication application, EdiModel ediModel, String decidedLender) {
        BankStatementSessionDetails bankStatementSessionDetails = bankStatementSessionDetailsDao.findFirstByMerchantIdOrderByIdDesc(application.getMerchantId());
        Calendar calendar = Calendar.getInstance();
        if (!ObjectUtils.isEmpty(bankStatementSessionDetails) && BankStatementSessionStatus.SUCCESS.equals(bankStatementSessionDetails.getStatus())) {
            calendar.setTime(bankStatementSessionDetails.getCreatedAt());
            calendar.add(Calendar.MONTH, 1);
            if (new Date().compareTo(calendar.getTime()) < 0) {
                decidedLender = assignFallackLender(application, ediModel);
            }
        } else {
            Gst3bSessionDetails gst3bSessionDetails = gst3bSessionDetailsDao.findFirstByMerchantIdOrderByIdDesc(application.getMerchantId());
            if (!ObjectUtils.isEmpty(gst3bSessionDetails) && Gst3bSessionStatus.SUCCESS.equals(gst3bSessionDetails.getStatus())) {
                calendar.setTime(gst3bSessionDetails.getCreatedAt());
                calendar.add(Calendar.MONTH, 1);
                if (new Date().compareTo(calendar.getTime()) < 0) {
                    decidedLender = assignFallackLender(application, ediModel);
                }
            }
        }
       return decidedLender;
    }

    private String checkForcefulAssignedLenderForMerchant(LendingApplication lendingApplication) {
        try {
            log.info("checking forced lender for merchantId : {}", lendingApplication.getMerchantId());
            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingApplication.getMerchantId());
            List<RiskSegment> riskSegments = Arrays.asList(RiskSegment.NTB_ETB_1, RiskSegment.NTB_ETB_2, RiskSegment.PURE_NTB, RiskSegment.NTB_PURE);
            if (!ObjectUtils.isEmpty(lendingRiskVariables) && riskSegments.contains(RiskSegment.valueOf(lendingRiskVariables.getRiskSegment()))){
                Map<Long, String> forcefulLenderMerchants = loanUtil.forcefulLenderMerchantList();
                if (ObjectUtils.isEmpty(forcefulLenderMerchants)) {
                    log.info("Empty forceful assigned lender merchants list");
                    return null;
                }
                String lender = forcefulLenderMerchants.get(lendingApplication.getMerchantId());
                if (ObjectUtils.isEmpty(lender)) {
                    log.info("merchantId is not there in forceful assigned lender merchants list : {}", lendingApplication.getMerchantId());
                    return null;
                }
                log.info("forced lender for merchantId : {}, {}", lendingApplication.getMerchantId(), lender);
                return lender;
            }
        } catch (Exception e) {
            log.error("Exception in checking forceful assigned lender for merchantId : {}, {}", lendingApplication.getMerchantId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
