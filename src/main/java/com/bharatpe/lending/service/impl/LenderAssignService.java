package com.bharatpe.lending.service.impl;

import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.service.ILenderAssignService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.entity.LenderAssignmentRules;
import com.bharatpe.lending.entity.LendingLenderQuota;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.utils.OfferUtils;
import com.bharatpe.lending.service.*;
import com.bharatpe.lending.util.LoanUtil;
import com.sun.org.apache.xpath.internal.operations.Bool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Slf4j
@Service
public class LenderAssignService implements ILenderAssignService {

    @Autowired
    LenderAssignmentRulesDao lenderAssignmentRulesDao;

    @Autowired
    LenderDisbursalLimitsDao lenderDisbursalLimitsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Value("${disbursal_target}")
    Double WEEKLY_TARGET_AMOUNT;

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

    @Override
    public LendingEnum.LENDER assignLender(EdiModel ediModel) {
        return null;
    }

    public String lenderAssignmentHandler(LendingApplication application, EdiModel ediModel) {
        refreshDisbursalLimitsForLender();
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(application.getMerchantId());
        Double bureauScore = 0D;
        String riskSegment = "";
        String riskGroupLike = null;
        String pincodeColor = null;
        if(Objects.nonNull(lendingRiskVariables)){
            bureauScore = Objects.nonNull(lendingRiskVariables.getBureauScore()) ? lendingRiskVariables.getBureauScore() : 0D;
            riskSegment = Objects.nonNull(lendingRiskVariables.getRiskSegment()) ? "%" + lendingRiskVariables.getRiskSegment() + "%" : "";
            riskGroupLike = Objects.nonNull(lendingRiskVariables.getRiskGroup()) ? "%" + lendingRiskVariables.getRiskGroup() + "%" : "";
            pincodeColor = Objects.nonNull(lendingRiskVariables.getPincodeColor()) ? "%"+lendingRiskVariables.getPincodeColor().name() + "%" : "";
        }
        String tenure = "%" + application.getTenureInMonths() + "%";
        try {
            log.info("Lender assignment parameters -> bureau:{}, loanType:{}, tenure:{}, loanAmount:{}, riskGroup:{}, pincodeColor:{}", bureauScore, riskSegment, application.getTenure(),
                    application.getLoanAmount(), riskGroupLike, pincodeColor);
            List<String> lenders = new ArrayList<>();
            String decidedLender = null;
            List<LenderAssignmentRules> defaultRules = lenderAssignmentRulesDao.findByIsDefaultAndIsActive(Boolean.TRUE, Boolean.TRUE);
            List<LenderAssignmentRules> ruleList = lenderAssignmentRulesDao.fetchEligibleRules(application.getLoanAmount(), bureauScore, riskSegment, tenure, riskGroupLike, pincodeColor);
            log.info("Fetched Rules:{}", ruleList);
            if (ObjectUtils.isEmpty(ruleList)) {
                log.info("Assigning default lender");
                lenders = getLenderList(defaultRules, ediModel, application.getLender(), application.getMerchantId());
                // assign default lender and EdiModel.
            } else {
                log.info("Found matching rules.");
                lenders = getLenderList(ruleList, ediModel, application.getLender(), application.getMerchantId());
            }
            decidedLender = getLender(application, lenders, ediModel, defaultRules);
            log.info("assigned lender {} {}", application.getLender(), application.getId());
            return decidedLender;
        } catch(Exception ex){
            log.error("Exception occurred while assigning lender : {}, {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            return null;
        }
    }

    public LendingApplication assignLender(LendingApplication application, EdiModel ediModel) {

        if (ObjectUtils.isEmpty(application)) {
            throw new RuntimeException("Application not found for merchant:" + application.getMerchantId());
        }
        String decidedLender = null;
        if (loanUtil.isInternalMerchant(application.getMerchantId()) && ObjectUtils.isEmpty(application.getLender())
                // TODO: 15/02/23 remove this checks later (only for temp roll out) 
                && !ObjectUtils.isEmpty(application.getExternalLoanId())
                && application.getLoanAmount() > 10000) {
            log.info("internal merchant or rollout {}", application.getMerchantId());
            decidedLender =  Lender.ABFL.name();
        } else {
            decidedLender = lenderAssignmentHandler(application, ediModel);
        }
        if(!ObjectUtils.isEmpty(decidedLender)){
            saveLenderChangeAudit(application, decidedLender);
        }else{
            EdiModel modifiedEdiModel = ediModel.getNoOfEdiDaysInAWeek() == 6 ? EdiModel.SEVEN_DAY_MODEL:EdiModel.SIX_DAY_MODEL;
            log.info("EDI MODEL CHANGED TO -> {}", modifiedEdiModel);
            // ModifyEdiModel
            decidedLender = lenderAssignmentHandler(application, modifiedEdiModel);
            if(ObjectUtils.isEmpty(decidedLender)){
                decidedLender = assignFallackLender(application, ediModel);
            } else{
                modifyEdiModel(application, modifiedEdiModel);
            }
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


    public String getLender(LendingApplication lendingApplication, List<String> lenders, EdiModel ediModel, List<LenderAssignmentRules> defaultRules){
        log.info("Implementing logic for lender assignment.");
        LendingLenderQuota assigneeLender = null;
        List<LendingLenderQuota> toBeAssignedLenders = new ArrayList<>();
        if(!ObjectUtils.isEmpty(lenders)){
            toBeAssignedLenders = lenderDisbursalLimitsDao.fetchEligibleLenderLimits(lenders, lendingApplication.getLoanAmount());
        }
        if(ObjectUtils.isEmpty(toBeAssignedLenders)){
            log.info("Not enough balance remaining on eligible lenders. Fetching default Lenders");
            lenders = getLenderList(defaultRules, ediModel, lendingApplication.getLender(), lendingApplication.getMerchantId());
            if(!ObjectUtils.isEmpty(lenders)){
                toBeAssignedLenders = lenderDisbursalLimitsDao.fetchEligibleLenderLimits(lenders, lendingApplication.getLoanAmount());
            }
        }
        if(ObjectUtils.isEmpty(toBeAssignedLenders)){
            log.info("No Eligible lenders found. Changing ediModel.");
            return null;
        }
        assigneeLender = toBeAssignedLenders.get(0);
        log.info("Selected Lender : {}", assigneeLender);
        EdiModel ediModel1 = LenderOffDays.valueOf(assigneeLender.getLender()).getEdiModel();
        log.info("Selected EDI Model : {}", ediModel1);
        ediModelAudit(lendingApplication, ediModel1);

        //updating lender limits
        LendingEnum.LENDER lender = LendingEnum.LENDER.valueOf(assigneeLender.getLender());
        Double updatedAssignedAmount = assigneeLender.getAssignedAmount() + lendingApplication.getLoanAmount();
        assigneeLender.setAssignedAmount(updatedAssignedAmount);
        Double updatedBalance = ObjectUtils.isEmpty(assigneeLender.getRemainingBalance()) ? null:assigneeLender.getRemainingBalance() - lendingApplication.getLoanAmount();
        assigneeLender.setRemainingBalance(updatedBalance);
        lenderDisbursalLimitsDao.save(assigneeLender);
        return lender.name();
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
            return assignLender(lendingApplication.get(), EdiModel.valueOf(ediModel)).getLender();
        }
        return "Application Not Found.";
    }

    List<String> getLenderList(List<LenderAssignmentRules> lenderAssignmentRules, EdiModel ediModel, String assignedLender, Long merchantId){
        log.info("Assigned Lender: {}  EdiModel: {}", assignedLender, ediModel );
        List<String> eligibleLenders = new ArrayList<>();
        List<String> ageCheckLenderList = Arrays.asList(ageCheckLenders.split(","));
        Integer age = apiGatewayService.getMerchantAge(merchantId);
        for(LenderAssignmentRules rule:lenderAssignmentRules){
            String lender = rule.getLender();
            if(ObjectUtils.isEmpty(ediModel) || ediModel.name().equals(LenderOffDays.valueOf(lender).getEdiModel().name())){
                // in case lender is to be changed.
                if(!ObjectUtils.isEmpty(assignedLender) && rule.getLender().equals(assignedLender)){
                    continue;
                }
                if (ageCheckLenderList.contains(rule.getLender()) && (age < 21 || age > 65)){
                    continue;
                }
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
                EdiModel ediModel = LenderOffDays.valueOf(Lender.LDC.name()).getEdiModel();
                EdiModel modifiedEdiModel = ediModel.getNoOfEdiDaysInAWeek() == 6 ? EdiModel.SEVEN_DAY_MODEL:EdiModel.SIX_DAY_MODEL;
                LendingApplicationDetails ediDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
                if (!ediDetails.getEdiModel().equals(ediModel.name())) {
                    modifyEdiModel(application.get(), modifiedEdiModel);
                }
                String oldLender = application.get().getLender();
                application.get().setLender(Lender.LDC.name());
                updateOfferDetailsInApplication(application.get(),ediModel,oldLender);
                lendingApplicationDao.save(application.get());
                return Lender.LDC;
            }
            EdiModel ediModel = LenderOffDays.valueOf(application.get().getLender()).getEdiModel();
            assignLender(application.get(), ediModel);
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
            return Lender.LDC.name();
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
            lender = assignLender(lendingApplication, EdiModel.SIX_DAY_MODEL).getLender();
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
                lender = activeLoan.getNbfc();
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

    public void updateOfferDetailsInApplication(LendingApplication lendingApplication, EdiModel ediModel, String lender) {
        try {
            if (ObjectUtils.isEmpty(lender) || lendingApplication.getLender().equalsIgnoreCase(lender)) {
                log.info("skiping updated offer if first time assignment or same lender is set for {}", lendingApplication.getId());
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
            lendingAuditTrial.setOldStatus("OLD_LENDER" + lender);
            lendingAuditTrial.setNewStatus("NEW_LENDER" + lendingApplication.getLender());
            lendingAuditTrialDao.save(lendingAuditTrial);
        } catch (Exception e) {
            log.error("exception while updating applicationDetails {} {}",lendingApplication.getId(), e.getMessage());
        }
    }
}
