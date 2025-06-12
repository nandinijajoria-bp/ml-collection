package com.bharatpe.lending.service.impl;

import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.query.dao.ForeClosureConfigDao;
import com.bharatpe.lending.common.query.dao.PenaltyFeeConfigDaoSlave;
import com.bharatpe.lending.common.query.entity.ForeClosureConfig;
import com.bharatpe.lending.common.query.entity.PenaltyFeeConfigSlave;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.ILenderAssignService;
import com.bharatpe.lending.common.service.merchant.dto.*;
import com.bharatpe.lending.common.service.merchant.service.*;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.LoanApplicationDetailsDto;
import com.bharatpe.lending.dto.PanVerifyKYCResponseDto;
import com.bharatpe.lending.dto.RiskVariablesDTO;
import com.bharatpe.lending.entity.*;
import com.bharatpe.lending.enums.EnachMode;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.loanV2.handlers.*;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.config.*;
import com.bharatpe.lending.loanV3.dto.LenderAggregationResponseDto;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.services.LendingApplicationServiceV3Base;
import com.bharatpe.lending.loanV3.utils.OfferUtils;
import com.bharatpe.lending.util.EdiUtil;
import com.bharatpe.lending.util.EntityToDtoConvertorUtil;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static com.bharatpe.lending.common.enums.RiskSegment.REGULAR_ETC;
import static com.bharatpe.lending.constant.LendingConstants.*;
import static com.bharatpe.lending.enums.Lender.*;

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
    LendingLenderPricingDao lendingLenderPricingDao;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

    @Autowired
    LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Value("${whitelisted.topup.lenders}")
    String topupLenders;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    BureauHandler bureauHandler;

    @Autowired
    MerchantService merchantService;

    @Value("${gst_offer_lender}")
    String gstOfferLender;

    @Value("${lender.assign.rollout}")
    Integer lenderAssignmentNewFlowRollOutPercent;

    @Value("${lender.eligible.pincode.check:PIRAMAL}")
    List<String> lenderEligiblePincodeCheckList;

    @Value("${default.assign.abfl:false}")
    boolean defaultAssignAbfl;

    @Value("${default.assign.lender:PIRAMAL}")
    String defaultAssignLender;

    @Value("${run.internal.rules:false}")
    boolean runInternalRules;

    @Value("${piramal.rollout.percent:1}")
    Integer piramalRolloutPercentage;

    @Value("${usfb.rollout.percent:1}")
    Integer usfbRolloutPercentage;

    @Value("${trillionLoans.rollout.percent:1}")
    Integer trillionLoansRolloutPercentage;

    @Value("${is.gst.offer.enabled:false}")
    boolean isGstOfferEnabled;

    @Value("${is.wildcard.lender.config.enabled:true}")
    boolean isWildcardLenderConfigEnabled;

    @Value("${lending.wildcard.lender.name:TRILLIONLOANS}")
    String lendingWildcardLenderName;

    @Value("${muthoot.max.irr:36.0}")
    Double muthootMaxIrr;

    @Value("${piramal.max.irr:36.0}")
    Double piramalMaxIrr;

    @Value("${piramal.max.apr:48.0}")
    Double piramalMaxApr;

    @Lazy
    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Value("${aadhaar.seeding.status.check.lenders:}")
    String aadhaarSeedingStatusCheckLenders;

    @Autowired
    LendingPancardDetailsDao lendingPancardDetailsDao;

    @Autowired
    KycHandler kycHandler;

    @Value("${muthoot.rollout.percent:1}")
    Integer muthootRolloutPercentage;

    @Value("${capri.rollout.percent:1}")
    Integer capriRolloutPercent;

    @Value("${payu.rollout.percent:1}")
    Integer payuRolloutPercent;

    @Value("${max.irr.eligible.lenders:CREDITSAISON,MUTHOOT,PIRAMAL}")
    String maxIrrEligibleLender;

    @Value("${max.apr.eligible.lenders:PIRAMAL}")
    String maxAprEligibleLender;

    @Value("${max.pf.eligible.lenders:}")
    String maxPfEligibleLender;

    @Lazy
    @Autowired
    CreditSaisonConfig csConfig;

    @Autowired
    BankStatementSessionDetailsDao bankStatementSessionDetailsDao;

    @Autowired
    Gst3bSessionDetailsDao gst3bSessionDetailsDao;

    @Autowired
    LmsFieldValuesDao lmsFieldValuesDao;

    @Autowired
    LenderBusinessCategoryDao lenderBusinessCategoryDao;

    @Autowired
    FunnelService funnelService;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Value("${max.eligible.lenders.for.modify:2}")
    Integer maxEligibleLendersCountForModify;

    @Autowired
    LendingApplicationServiceV3Base lendingApplicationServiceV3Base;


    @Value("${lender.assign.threshold}")
    Integer maxLenderAssignThreshold;

    @Autowired
    PenaltyFeeConfigDaoSlave penaltyFeeConfigDaoSlave;

    @Autowired
    ForeClosureConfigDao foreClosureDao;

    @Value("${trillion.topup.lenders:-}")
    public void setTrillionTopupLenders(String trillionTopupLenders) {
        LenderAssignService.trillionTopupLenders = Arrays.asList(trillionTopupLenders.split(","));
    }

    static List<String> trillionTopupLenders;

    @Autowired
    SmfgConfig smfgConfig;

    @Lazy
    @Autowired
    UgroConfig ugroConfig;

    @Lazy
    @Autowired
    OxyzoConfig oxyzoConfig;

    @Value("${lender.apr.enable_new_logic:true}")
    private boolean enableNewLenderAprLogic;

    @Value("${wildcard.lender.base.check.enabled:true}")
    private boolean baseCheckForWildCardLender;

    @Autowired
    private EdiUtil ediUtil;

    @Override
    public LendingEnum.LENDER assignLender(EdiModel ediModel) {
        return null;
    }

    public String lenderAssignmentHandler(LendingApplication application, EdiModel ediModel, BasicDetailsDto merchantDetails, Boolean isApplicableForAggregation) {
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(application.getMerchantId());
        RiskVariablesDTO riskVariables = EntityToDtoConvertorUtil.convertToRiskVariablesDTO(lendingRiskVariables);

        int maxTenure = riskVariables.getMaxTenure();
        double tpvOffer = riskVariables.getTpvOffer();
        double summaryTpv = riskVariables.getSummaryTpv();

        if(maxTenure != 0 && tpvOffer != 0D && !ObjectUtils.isEmpty(application.getTenureInMonths()) && application.getTenureInMonths() != 0) {
            tpvOffer = (tpvOffer / maxTenure) * application.getTenureInMonths();

        }
        try {
            double bureauScore = riskVariables.getBureauScore();
            String riskSegment = riskVariables.getRiskSegment();
            String riskGroupLike = riskVariables.getRiskGroupLike();
            String pincodeColor = ObjectUtils.isEmpty(lendingRiskVariables.getPincodeColor()) ? "" : "%" + lendingRiskVariables.getPincodeColor().name() + "%";
            String tenure = "%" + application.getTenureInMonths() + "%";
            log.info("Lender assignment parameters -> bureau:{}, loanType:{}, tenure:{}, loanAmount:{}, riskGroup:{}, pincodeColor:{}", bureauScore, riskSegment, application.getTenure(),
                    application.getLoanAmount(), riskGroupLike, pincodeColor);
            List<String> lenders = new ArrayList<>();
            String decidedLender = null;
            List<LenderAssignmentRules> ruleList=lenderAssignmentRulesDao.fetchEligibleRules(application.getLoanAmount(), bureauScore, riskSegment, tenure, riskGroupLike, pincodeColor);
            if (loanUtil.isInternalMerchant(application.getMerchantId()) || runInternalRules) {
                ruleList=lenderAssignmentRulesDao.fetchEligibleRulesForInternal(application.getLoanAmount(), bureauScore, riskSegment, tenure, riskGroupLike, pincodeColor);
            }
            log.info("Fetched Rules:{}", ruleList);
            try {
                List<String> eligibleLendersAsPerRules = ruleList.stream().map(LenderAssignmentRules::getLender).collect(Collectors.toCollection(ArrayList::new));
                saveEligibleLenderAudit(application, "", CollectionUtils.isEmpty(eligibleLendersAsPerRules) ? "" : String.join(",", eligibleLendersAsPerRules), "ELIGIBLE_RULE_LENDERS");
            } catch (Exception exception) {
                log.info("exception while logging the lender assignment details under rules based lenders", exception);
            }

            boolean isPanAadhaarLinked = false;
            boolean isPanAadhaarLinkedStatusChecked = false;

            if(!ObjectUtils.isEmpty(ruleList)) {
                lenders = getLenderList(ruleList, ediModel, application.getLender(), application.getMerchantId(), application.getId());
                try {
                    if (!CollectionUtils.isEmpty(lenders)) {
                        ListIterator<String> iterator = lenders.listIterator();
                        while (iterator.hasNext()) {
                            String lender = iterator.next().toUpperCase();
                            if (riskVariables.getRejectedLenders().contains(loanUtil.getLenderRejectedMapping(lender))) {
                                log.info("skipping {} due to lender in rejected lender list for {}", lender, application.getId());
                                String remarks = "skipping " + lender + " due to lender in rejected lender list in lending risk variables for " + application.getId();
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                                continue;
                            }
                            if (lenderEligiblePincodeCheckList.contains(lender)) {

                                boolean shouldCheckPincodeList = true;

                                if (PAYU.name().equalsIgnoreCase(lender)) {
                                    shouldCheckPincodeList = application.getLoanAmount() > 500000;
                                    log.info("Inside payu pincode check : amount {} - shouldCheckPincodeList {} - applicationId {}", application.getLoanAmount(), shouldCheckPincodeList, application.getId());
                                }

                                if (shouldCheckPincodeList) {

                                    LenderEligiblePincodes lenderEligiblePincodes = lenderEligiblePincodesDao.findByLenderAndPincodeAndStatus(
                                            lender, lendingRiskVariables.getPincode(), LenderEligiblePincodes.LenderEligiblePincodesStatus.ACTIVE
                                    );
                                    if (ObjectUtils.isEmpty(lenderEligiblePincodes)) {
                                        funnelService.submitEventV3(application.getMerchantId(), null, application.getId(),
                                                FunnelEnums.StageId.LENDER_ASSIGNMENT, FunnelEnums.StageEvent.LENDER_SKIPPED_NEGATIVE_PINCODE, lender, LoanDetailsConstant.FUNNEL_VERSION_TAG);
                                        log.info("removing lender : {} from eligible as pincode : {} not serviceable", lender, lendingRiskVariables.getPincode());
                                        String remarks = "Removing lender: " + lender + " from eligible as pincode: " + lendingRiskVariables.getPincode() + " not serviceable";
                                        createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                        iterator.remove();
                                        continue;
                                    }
                                }
                            }

                            if (!baseChecksPassedForLenders(application, lender, ediModel, riskVariables)) {
                                log.info("base checks failed, skipping {} for {}", lender, application.getId());
                                iterator.remove();
                                continue;
                            }

                            if(!aadhaarSeedingStatusCheckLenders.isEmpty() && aadhaarSeedingStatusCheckLenders.contains(lender)) {

                                // check if pan aadhaar linked status already checked so that we don't call the api again to re check it
                                if (!isPanAadhaarLinkedStatusChecked) {
                                    isPanAadhaarLinked = isPanAndAadhaarLinked(application.getMerchantId());
                                    isPanAadhaarLinkedStatusChecked = true;
                                }

                                log.info("isPanAadhaarLinkedStatusChecked {} isPanAadhaarLinked {} applicationId {}", isPanAadhaarLinkedStatusChecked, isPanAadhaarLinked, application.getId());

                                if (!isPanAadhaarLinked) {
                                    log.info("removing {} from eligible lenders since panAndAdhaar is not linked for applicationId: {} and merchantId : {}", lender, application.getId(), application.getMerchantId());
                                    String remarks = "removing " + lender + " from eligible lenders since panAndAdhaar is not linked for applicationId: " + application.getId() + " and merchantId : " + application.getMerchantId();
                                    createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(),  lender, "LENDER_REMOVED", remarks);
                                    iterator.remove();
                                    continue;
                                }
                            }


                            if (Lender.CAPRI.name().equalsIgnoreCase(lender) && summaryTpv < application.getEdi()) {
                                log.info("skipping capri {} due to merchant edi is greater than summaryTpv {}", lender, application.getId());
                                String remarks = "skipping capri  due to merchant edi: " + application.getEdi() + " is greater than summaryTpv: " + summaryTpv;
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                                continue;
                            }
                            if (Collections.singletonList(MUTHOOT.name()).contains(lender) && application.getLoanAmount() > (Math.round(tpvOffer / 1000) * 1000)) {
                                log.info("skipping muthoot for application id : {} due to merchant loan amount {} is greater than tpvOffer {}", application.getLoanAmount(), application.getId(), (Math.round(tpvOffer / 1000) * 1000));
                                String remarks = "skipping muthoot for application id : " + application.getId() + " due to merchant loan amount: " + application.getLoanAmount() + " is greater than tpvOffer: " + (Math.round(tpvOffer / 1000) * 1000);
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                                continue;
                            }
                            if (Collections.singletonList(PAYU.name()).contains(lender) && application.getLoanAmount() > (Math.ceil(tpvOffer / 10000) * 10000)) {
                                log.info("skipping payU for application id : {} due to merchant loan amount {} is greater than tpvOffer {}", application.getLoanAmount(), application.getId(), (Math.ceil(tpvOffer / 10000) * 10000));
                                String remarks = "skipping payU for application id : " + application.getId() + " due to merchant loan amount: " + application.getLoanAmount() + " is greater than tpvOffer: " + (Math.ceil(tpvOffer / 10000) * 10000);
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                                continue;
                            }
                            if (MUTHOOT.name().equalsIgnoreCase(lender) && application.getEdi() > 0.9 * summaryTpv) {
                                log.info("skipping muthoot for application id : {} due to merchant loan edi amount is greater than 0.9 * summary_tpv {}", application.getId(), 0.9 * summaryTpv);
                                String remarks = "skipping muthoot for application id : " + application.getId() + " due to merchant loan edi amount: " + application.getEdi() + " is greater than 0.9 * summary_tpv " + 0.9 * summaryTpv;
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                                continue;
                            }
                            if (PAYU.name().equalsIgnoreCase(lender) && application.getEdi() > summaryTpv) {
                                log.info("skipping payu for application id : {} due to merchant loan edi amount is greater than summary_tpv {}", application.getId(), summaryTpv);
                                String remarks = "skipping payu for application id : " + application.getId() + " due to merchant loan edi amount: " + application.getEdi() + " is greater than summary_tpv " + summaryTpv;
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                                continue;
                            }
                            if (UGRO.name().equalsIgnoreCase(lender) && (application.getLoanAmount() < ugroConfig.getMinAmount() || application.getLoanAmount() > ugroConfig.getMaxAmount())) {
                                String remarks = "UGRO: skipping for application id : " + application.getId() + " due to loan amount: " + application.getLoanAmount() + " is greater than: " + ugroConfig.getMaxAmount() + " / less than: " + ugroConfig.getMinAmount();
                                log.info(remarks);
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                                continue;
                            }
                            if (OXYZO.name().equalsIgnoreCase(lender) && application.getEdi() > 0.7 * (riskVariables.getMonthlyTpv()/30)){
                                String remarks = " OXYZO: skipping oxyzo for application id : " + application.getId() + " due to edi amount: " + application.getEdi() + " is greater than 0.7 *  (monthly_tpv/30) " + 0.7 * (riskVariables.getMonthlyTpv()/30);
                                log.info(remarks);
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                                continue;
                            }
                            if (OXYZO.name().equalsIgnoreCase(lender) && application.getLoanAmount() > Math.ceil(tpvOffer)){
                                String remarks = " OXYZO: skipping oxyzo for application id : " + application.getId() + "due to merchant loan amount: " + application.getLoanAmount() + " is greater than tpvOffer: " + Math.ceil(tpvOffer);
                                log.info(remarks);
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                                continue;
                            }
                            if (OXYZO.name().equalsIgnoreCase(lender) && "R1".equalsIgnoreCase(riskVariables.getRiskGroup()) && ((application.getLoanAmount() + riskVariables.getUnsecuredPos()) > (6 * riskVariables.getMonthlyTpv()))){
                                String remarks = " OXYZO: skipping oxyzo for application id : " + application.getId() + "due to merchant loan amount: " + application.getLoanAmount() + "+ unsecuredPos : " + riskVariables.getUnsecuredPos() + "is greater than 6 * Monthly Adj TPV: " + 6 * riskVariables.getMonthlyTpv();
                                log.info(remarks);
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                                continue;
                            }
                            if (OXYZO.name().equalsIgnoreCase(lender) && "R2".equalsIgnoreCase(riskVariables.getRiskGroup()) && ((application.getLoanAmount() + riskVariables.getUnsecuredPos()) > (4.5 * riskVariables.getMonthlyTpv()))){
                                String remarks = " OXYZO: skipping oxyzo for application id : " + application.getId() + "due to merchant loan amount: " + application.getLoanAmount() + "+ unsecuredPos : " + riskVariables.getUnsecuredPos() + "is greater than 4.5 * Monthly Adj TPV: " + 4.5 * riskVariables.getMonthlyTpv();
                                log.info(remarks);
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                                continue;
                            }
                            if (additionalChecksFailed(application, Lender.valueOf(lender), merchantDetails)) {
                                log.info("skipping {} due to additional checks failing for {}", lender, application.getId());
                                iterator.remove();
                                continue;
                            }
                            if (!negativeCategoryAndLoanAmountCheckPassed(application, lendingRiskVariables.getRiskSegment(), lender)) {
                                log.info("skipping {} due to business category check failure for {}", lender, application.getId());
                                iterator.remove();
                            }
                            if (riskVariables.getRejectedLenders().contains(loanUtil.getLenderRejectedMapping(lender))) {
                                log.info("skipping {} due to lender in rejected lender list for {}", lender, application.getId());
                                String remarks = "skipping " + lender + " due to lender in rejected lender list in lending risk variables for " + application.getId();
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                            }
                        }
                    }
                } catch (Exception exception) {
                    log.error("exception while custom pincode check for lender for application id : {}, {}", application.getId(), Arrays.asList(exception.getStackTrace()));
                }
            }
            if (ObjectUtils.isEmpty(lenders)) {
                LendingLenderQuota lendingLenderQuota = lenderDisbursalLimitsDao.findByClassification(LendingLenderQuota.Classification.WILDCARD.name());
                if(ObjectUtils.isEmpty(lendingLenderQuota)){
                    return LendingConstants.NONE_LENDER;
                }
                if (isWildcardLenderConfigEnabled && !ObjectUtils.isEmpty(lendingLenderQuota)) {
                    log.info("Assigning Wild Card Lender as : {} for application id : {} because eligible lender list : {}",
                            lendingLenderQuota.getLender() , application.getId(), lenders);
                    try {
                        saveEligibleLenderAudit(application, lendingLenderQuota.getLender(), CollectionUtils.isEmpty(lenders) ? "" : String.join(",", lenders), "WILDCARD_LENDER");
                    } catch (Exception exception) {
                        log.info("exception while logging the lender assignment details", exception);
                    }
                    lenders.add(lendingLenderQuota.getLender());
                }
            }
            if (!isApplicableForAggregation){
                decidedLender = getLender(application, lenders, ediModel, riskVariables.getIsGstOffer(), riskSegment.substring(1, riskSegment.length()-1));
                log.info("lender to be assigned: {} {}", decidedLender, application.getId());
            }
            try {
                saveEligibleLenderAudit(application, ObjectUtils.isEmpty(decidedLender) ? "" : decidedLender, CollectionUtils.isEmpty(lenders) ? "" : String.join(",", lenders), "ELIGIBLE_LENDER");
            } catch (Exception exception) {
                log.info("exception while logging the lender assignment details", exception);
            }

            return decidedLender;
        } catch(Exception ex){
            log.error("Exception occurred while assigning lender : {}, {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            return null;
        }
    }

    public boolean isPanAndAadhaarLinked(Long merchantId) {
        LendingPancardDetails lendingPancardDetails = lendingPancardDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
        if (!ObjectUtils.isEmpty(lendingPancardDetails) && !ObjectUtils.isEmpty(lendingPancardDetails.getName()) && !ObjectUtils.isEmpty(lendingPancardDetails.getPancardNumber()) && !ObjectUtils.isEmpty(lendingPancardDetails.getDob())) {

            PanVerifyKYCResponseDto responseDto = kycHandler.verifyPanDetailsInternal(lendingPancardDetails.getPancardNumber(), lendingPancardDetails.getName(), lendingPancardDetails.getDob(), merchantId);

            if (!ObjectUtils.isEmpty(responseDto)) {

                String aadhaarSeedingStatus = !ObjectUtils.isEmpty(responseDto.getData())
                        && !ObjectUtils.isEmpty(responseDto.getData().getAadhaarSeedingStatus()) ? responseDto.getData().getAadhaarSeedingStatus() : null;

                if ("Y".equalsIgnoreCase(aadhaarSeedingStatus)) {
                    return true;
                }

            }

        }
        return false;
    }

    public boolean baseChecksPassedForLenders(LendingApplication lendingApplication, String lender, EdiModel ediModel, RiskVariablesDTO riskVariables) {
        if(maxIrrEligibleLender.contains(lender) && maxIrrCheckFailed(lendingApplication, ediModel, lender)) {
            log.info("skipping {} due to maxIrr checks failing for {}", lender, lendingApplication.getId());
            String remarks = "skipping " + lender + " due to maxIrr checks failing for " + lendingApplication.getId();
            createAndSaveLendingAuditTrial(lendingApplication.getId(),lendingApplication.getMerchantId(), lender, "LENDER_REMOVED", remarks);
            return false;
        }
        if(maxAprEligibleLender.contains(lender) && maxAprCheckFailed(lendingApplication, ediModel, lender)){
            log.info("skipping {} due to maxApr checks failing for {}", lender, lendingApplication.getId());
            String remarks = "skipping " + lender + " due to maxApr checks failing for " + lendingApplication.getId();
            createAndSaveLendingAuditTrial(lendingApplication.getId(),lendingApplication.getMerchantId(), lender, "LENDER_REMOVED", remarks);
            return false;
        }

        if(maxPfEligibleLender.contains(lender) && maxPfCheckFailed(lendingApplication,lender)){
            log.info("skipping {} due to maxPf checks failing for {}", lender, lendingApplication.getId());
            String remarks = "skipping " + lender + " due to maxPf checks failing for " + lendingApplication.getId();
            createAndSaveLendingAuditTrial(lendingApplication.getId(),lendingApplication.getMerchantId(), lender, "LENDER_REMOVED", remarks);
            return false;
        }
        if (SMFG.name().equalsIgnoreCase(lender) && lendingApplication.getEdi() > 0.7 * riskVariables.getSummaryTpv()) {
            log.info("skipping {} due to EDI amount greater than 0.7 * summary Tpv for {}", lender, lendingApplication.getId());
            String remarks = "skipping " + lender + " for " + lendingApplication.getId() + " due to EDI amount greater than 0.7 * summary Tpv " + 0.7 * riskVariables.getSummaryTpv();
            createAndSaveLendingAuditTrial(lendingApplication.getId(), lendingApplication.getMerchantId(), lender, "LENDER_REMOVED", remarks);
            return false;
        }
        return true;
    }

    public boolean lenderBaseChecksCleared(LendingApplication lendingApplication, String lender, EdiModel ediModel, RiskVariablesDTO riskVariables) {
        if(maxIrrCheckFailedV2(lendingApplication, ediModel, lender, riskVariables)) {
            log.info("skipping {} due to lender pricing based maxIrr checks failing for {}", lender, lendingApplication.getId());
            String remarks = "skipping " + lender + " due to maxIrr checks failing for " + lendingApplication.getId();
            createAndSaveLendingAuditTrial(lendingApplication.getId(),lendingApplication.getMerchantId(), lender, "LENDER_REMOVED", remarks);
            return false;
        }
        if(maxAprCheckFailedV2(lendingApplication, ediModel, lender, riskVariables)){
            log.info("skipping {} due to lender pricing based maxApr checks failing for {}", lender, lendingApplication.getId());
            String remarks = "skipping " + lender + " due to maxApr checks failing for " + lendingApplication.getId();
            createAndSaveLendingAuditTrial(lendingApplication.getId(),lendingApplication.getMerchantId(), lender, "LENDER_REMOVED", remarks);
            return false;
        }

        if(maxPfEligibleLender.contains(lender) && maxPfCheckFailedV2(lendingApplication,lender, riskVariables)){
            log.info("skipping {} due to maxPf checks failing for {}", lender, lendingApplication.getId());
            String remarks = "skipping " + lender + " due to maxPf checks failing for " + lendingApplication.getId();
            createAndSaveLendingAuditTrial(lendingApplication.getId(),lendingApplication.getMerchantId(), lender, "LENDER_REMOVED", remarks);
            return false;
        }
        return true;
    }

    private boolean negativeCategoryAndLoanAmountCheckPassed(LendingApplication lendingApplication, String riskSegment, String lender){
        if(RiskSegment.REPEAT.name().equalsIgnoreCase(riskSegment)){
            LendingApplication lastLmsDisbursedApplication = lendingApplicationDao.getLastLmsDisbursedLoan(lendingApplication.getMerchantId());
            if(ObjectUtils.isEmpty(lastLmsDisbursedApplication)){
                log.info("last lms disbursed application not available for checks on app {}", lendingApplication.getId());
                return true;
            }
            List<Long> lmsFieldIds = new ArrayList<>();
            lmsFieldIds.add(BUSINESS_CATEGORY_LMS_FIELD_ID);
            lmsFieldIds.add(BUSINESS_SUBCATEGORY_LMS_FIELD_ID);
            List<LmsFieldValues> lmsFieldValuesList = lmsFieldValuesDao.findByLendingApplicationIdAndFieldIdIn(
                    lastLmsDisbursedApplication.getId(), lmsFieldIds
            );
            if(ObjectUtils.isEmpty(lmsFieldValuesList)){
                log.info("business category not available from last disbursed app {}", lendingApplication.getId());
                return true;
            }
            String businessCategory = null;
            String businessSubcategory = null;
            for(LmsFieldValues lmsFieldValues : lmsFieldValuesList){
                if(lmsFieldValues.getFieldId() == BUSINESS_CATEGORY_LMS_FIELD_ID){
                    businessCategory = lmsFieldValues.getFieldDropdownValue();
                }
                else if (lmsFieldValues.getFieldId() == BUSINESS_SUBCATEGORY_LMS_FIELD_ID){
                    businessSubcategory = lmsFieldValues.getFieldDropdownValue();
                }
            }

            LenderBusinessCategory lendingLenderBusinessCategory = lenderBusinessCategoryDao.findBusinessCategoryChecks(
                    lender, businessCategory, businessSubcategory
            );
            if(ObjectUtils.isEmpty(lendingLenderBusinessCategory)){
                log.info("business category not available for {}, {}", lendingApplication.getId(), lender);
                return true;
            }
            if("INACTIVE".equalsIgnoreCase(lendingLenderBusinessCategory.getStatus())){
                log.info("skipping lender {} due to negative category for {}", lender, lendingApplication.getId());
                funnelService.submitEventV3(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                        FunnelEnums.StageId.LENDER_ASSIGNMENT, FunnelEnums.StageEvent.LENDER_SKIPPED_NEGATIVE_CATEGORY, lender, LoanDetailsConstant.FUNNEL_VERSION_TAG);
                String remarks = "skipping lender " + lender + " due to lending business category status: " + lendingLenderBusinessCategory.getStatus() + " is inactive for " + lendingApplication.getId();
                createAndSaveLendingAuditTrial(lendingApplication.getId(), lendingApplication.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                return false;
            }
            else if ("ACTIVE".equalsIgnoreCase(lendingLenderBusinessCategory.getStatus())){
                if(Objects.nonNull(lendingLenderBusinessCategory.getMaxAmount()) &&
                        (lendingApplication.getLoanAmount() > lendingLenderBusinessCategory.getMaxAmount())
                ){
                    funnelService.submitEventV3(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                            FunnelEnums.StageId.LENDER_ASSIGNMENT, FunnelEnums.StageEvent.LENDER_SKIPPED_CATEGORY_AMOUNT_LIMIT, lender, LoanDetailsConstant.FUNNEL_VERSION_TAG);
                    log.info("skipping {} due to breach of business category amount limit for {}", lender, lendingApplication.getId());
                    String remarks = "skipping " + lender + " due to breach of business category amount limit: " + lendingLenderBusinessCategory.getMaxAmount() + "is less than lending application amount: " + lendingApplication.getLoanAmount() + " for " + lendingApplication.getId();
                    createAndSaveLendingAuditTrial(lendingApplication.getId(), lendingApplication.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                    return false;
                }
            }
        }
        else{
            List<LendingApplication> rejectedApplicationList = lendingApplicationDao.getLastThreeRejectedApplications(lendingApplication.getMerchantId());
            for(LendingApplication rejectedApplication : rejectedApplicationList){
                if(rejectedApplication.getLender().equalsIgnoreCase(lender)){
                    if(NEGATIVE_BUSINESS_CATEGORY_REJECTION.equalsIgnoreCase(rejectedApplication.getManualKycReason()) ||
                            NEGATIVE_BUSINESS_CATEGORY_REJECTION.equalsIgnoreCase(rejectedApplication.getPhysicalReason())
                    ){
                        log.info("skipping lender {} due to last rejected application on negative category for {}", lender, lendingApplication.getId());
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public LendingApplication assignLender(LendingApplication application, EdiModel ediModel, BasicDetailsDto merchantDetails, Boolean isApplicableForAggregationFLow) {

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
                    if(LIQUILOANS_NBFC.name().equalsIgnoreCase(decidedLender)) {
                        LendingLenderQuota lendingLenderQuota = lenderDisbursalLimitsDao.findByClassification(LendingLenderQuota.Classification.WILDCARD.name());
                        if (isWildcardLenderConfigEnabled && !ObjectUtils.isEmpty(lendingLenderQuota)) {
                            log.info("Assigning Wild Card Lender as : {} for application id : {} because decided lender is : {}", lendingLenderQuota.getLender(), application.getId(), decidedLender);
                            decidedLender = lendingLenderQuota.getLender();
                        } else {
                            log.info("Assigning fallback Lender for application id : {} because decided lender is : {} and wildCard lender is not available", application.getId(), decidedLender);
                            decidedLender = assignFallackLender(application, ediModel);
                        }
                    }
                    if(!isApplicableForAggregationFLow){
                        saveLenderChangeAudit(application, decidedLender, application.getLender());
                        String oldLender = application.getLender();
                        application.setLender(decidedLender);
                        EdiModel updatedEdiModel= LendingConstants.NONE_LENDER.equalsIgnoreCase(decidedLender) ? null : LenderOffDays.valueOf(decidedLender).getEdiModel();
                        updateOfferDetailsInApplication(application,updatedEdiModel, oldLender);
                        lendingApplicationDao.save(application);
                        updateOfferDetails(application, decidedLender);
                        return application;
                    }
                }
            }

            if (loanUtil.isLenderPricingApplicableMerchant(merchantDetails.getId())) {
                decidedLender = lenderAssignmentHandlerV2(application, ediModel, merchantDetails, isApplicableForAggregationFLow);
            } else {
                decidedLender = lenderAssignmentHandler(application, ediModel, merchantDetails, isApplicableForAggregationFLow);
            }
            if (isGstOfferEnabled && Lender.ABFL.name().equals(decidedLender)) {
                decidedLender = updateLenderForGstAndBS(application, ediModel, decidedLender);
            }
        }
        if(ObjectUtils.isEmpty(decidedLender)){
            if(!isApplicableForAggregationFLow){
                decidedLender = assignFallackLender(application, ediModel);
            }
        }
        if (Lender.LDC.name().equals(decidedLender)){
            String enachMode = loanUtil.getEnachBankMode(application.getMerchantId()).getMode();
            if (EnachMode.ADHAAR.name().equalsIgnoreCase(enachMode)) {
                decidedLender = LIQUILOANS_NBFC.name();
            }
        }
        if(LIQUILOANS_NBFC.name().equalsIgnoreCase(decidedLender)) {
            LendingLenderQuota lendingLenderQuota = lenderDisbursalLimitsDao.findByClassification(LendingLenderQuota.Classification.WILDCARD.name());
            if (isWildcardLenderConfigEnabled && !ObjectUtils.isEmpty(lendingLenderQuota)) {
                log.info("Assigning Wild Card Lender as : {} for application id : {} because decided lender is : {}", lendingLenderQuota.getLender(), application.getId(), decidedLender);
                decidedLender = lendingLenderQuota.getLender();
            } else {
                log.info("Assigning fallback Lender for application id : {} because decided lender is : {} and wildCard lender is not available", application.getId(), decidedLender);
                decidedLender = assignFallackLender(application, ediModel);
            }
        }


        if(!isApplicableForAggregationFLow){
            saveLenderChangeAudit(application, decidedLender, application.getLender());
            String oldLender = application.getLender();
            application.setLender(decidedLender);
            updateOfferDetails(application, decidedLender);
            EdiModel updatedEdiModel= LendingConstants.NONE_LENDER.equalsIgnoreCase(decidedLender) ? null : LenderOffDays.valueOf(decidedLender).getEdiModel();
            updateOfferDetailsInApplication(application,updatedEdiModel, oldLender);
            return lendingApplicationDao.save(application);

        }
        return null;
    }

    public void saveLenderChangeAudit(LendingApplication lendingApplication, String newLender, String oldLender){
        LendingAuditTrial auditLender = new LendingAuditTrial();
        auditLender.setApplicationId(lendingApplication.getId());
        auditLender.setMerchantId(lendingApplication.getMerchantId());
        auditLender.setType("LENDER_SET");
        auditLender.setLoanId("BPL"+lendingApplication.getId());
        auditLender.setOldStatus(oldLender);
        auditLender.setNewStatus(newLender);
        log.info("Audit Trail: {}", auditLender);
        lendingAuditTrialDao.save(auditLender);
    }

    public void saveEligibleLenderAudit(LendingApplication lendingApplication, String selectedLender, String eligibleLender, String type){
        LendingAuditTrial auditLender = new LendingAuditTrial();
        auditLender.setApplicationId(lendingApplication.getId());
        auditLender.setMerchantId(lendingApplication.getMerchantId());
        auditLender.setType(type);
        auditLender.setLoanId("BPL"+lendingApplication.getId());
        auditLender.setOldStatus(eligibleLender);
        auditLender.setNewStatus(selectedLender);
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
            toBeAssignedLenders = lenderDisbursalLimitsDao.fetchEligibleLenderLimitsOrderByAssignment(lenders, lendingApplication.getLoanAmount());
        }
        log.info("Final eligible lenders:{}", toBeAssignedLenders);
        if(ObjectUtils.isEmpty(toBeAssignedLenders)){
            log.info("No Eligible lenders found. Changing ediModel.");
            return null;
        }
        //new flow
        for(LendingLenderQuota lender: toBeAssignedLenders){
            log.info("Lender:{}", lender.getLender());
//            if(isGstOffer){
//                log.info("Offer increased due to gst for merchant:{}", lendingApplication.getMerchantId());
//                if(!LenderOffDays.valueOf(gstOfferLender).getEdiModel().equals(ediModel)){
//                    modifyEdiModel(lendingApplication, LenderOffDays.valueOf(gstOfferLender).getEdiModel());
//                }
//                return gstOfferLender;
//            }

            // START remove giving preference to already kyc done lender
//            LendingApplicationKycDetails kycDetails = lendingApplicationKycDetailsDao.findSuccessKycDetails(lendingApplication.getMerchantId(), lender.getLender());
//            if("REPEAT".equalsIgnoreCase(riskSegment) && Objects.nonNull(kycDetails)){
//                //skip KYC
//                log.info("merchant {}  can skip KYC done on:{} for lender:{}", lendingApplication.getMerchantId(),kycDetails.getConsentDate() ,lender.getLender());
//                kycSkippableLender=lender;
//                flag=true;
//            }
            // END remove giving preference to already kyc done lender


//            if(loanUtil.isEligibleForNachSkip(lendingApplication, lender.getLender())){
//                //skip NACH
//                log.info("merchant {}  can skip NACH for lender:{}", lendingApplication.getMerchantId(), lender.getLender());
//                nachSkippableLender=lender;
//                flag = true;
//            }
            
            if(Objects.nonNull(nachSkippableLender) && Objects.nonNull(kycSkippableLender) && nachSkippableLender.equals(kycSkippableLender)){
                log.info("merchant {} can skip both KYC and NACH for lender: {}", lendingApplication.getMerchantId(), kycSkippableLender);
                break;
            }
        }

        log.info("lender eligible to be assigned : {} : {}", lendingApplication.getId(), toBeAssignedLenders);

        if(easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), lenderAssignmentNewFlowRollOutPercent)){
            //new flow
            if(ObjectUtils.isEmpty(assignedLender)){
                assignedLender = flag ? (ObjectUtils.isEmpty(nachSkippableLender) ? kycSkippableLender : nachSkippableLender) : toBeAssignedLenders.get(0);
                LendingApplicationDetails lendingApplicationDetails=lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
                if(ObjectUtils.isEmpty(lendingApplicationDetails)){
                    lendingApplicationDetails = new LendingApplicationDetails();
                    lendingApplicationDetails.setApplicationId(lendingApplication.getId());
                }
                lendingApplicationDetails.setIsKycSkip(Objects.nonNull(kycSkippableLender) && assignedLender.equals(kycSkippableLender));
            }
        } else{
            assignedLender = toBeAssignedLenders.get(0);
        }

        log.info("lender assigned for application : {} : {}", lendingApplication.getId(), assignedLender);

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

    public void updateLenderLimits(LendingLenderQuota lender, LendingApplication lendingApplication) {
        if(lender.getRemainingBalance()>lendingApplication.getLoanAmount()){
            log.info("updating lender limits for lender : {} with application id : {} and loan amount : {}", lender, lendingApplication.getId(), lendingApplication.getLoanAmount());
            Double updatedAssignedAmount = lender.getAssignedAmount() + lendingApplication.getLoanAmount();
            log.info("updated assigned amount : {}", updatedAssignedAmount);
            lender.setAssignedAmount(updatedAssignedAmount);
            Double updatedBalance = ObjectUtils.isEmpty(lender.getRemainingBalance()) ? null : lender.getRemainingBalance() - lendingApplication.getLoanAmount();
            log.info("updated remaining balance for lender : {}", updatedBalance);
            lender.setRemainingBalance(updatedBalance);
            log.info("updated lender limits : {}", lender);
            lenderDisbursalLimitsDao.save(lender);
            log.info("updated the lender limits {}", lender);
        }
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
            /*
            LendingLenderQuota updatedLimit = new LendingLenderQuota(limit.getLender(), limit.getTotalWeeklyAmount(),
                    limit.getRemainingBalance(), limit.getAssignedAmount(), null, LendingLenderQuota.Classification.REGULAR.name());
            */
            LendingLenderQuota updatedLimit = new LendingLenderQuota(limit.getLender(), limit.getTotalWeeklyAmount(),
                    limit.getRemainingBalance(), limit.getAssignedAmount(), null, limit1.get().getClassification());
            updatedLimit.setCreatedAt(limit1.get().getCreatedAt());
            updatedLimit.setId(id);
            updatedLimit.setEdiModel(LenderOffDays.valueOf(LendingEnum.LENDER.valueOf(limit1.get().getLender()).name()).getEdiModel().name());
            return lenderDisbursalLimitsDao.save(updatedLimit);
        }
        return null;
    }

    public String assignLenderAndEdiModel(Long applicationId, String ediModel, Boolean isApplicableForAggregationFlow){
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
        if(lendingApplication.isPresent()){

            log.info("EDI MODEL -> {}", EdiModel.valueOf(ediModel));
            return assignLender(lendingApplication.get(), EdiModel.valueOf(ediModel), null, isApplicableForAggregationFlow).getLender();
//            return null;
        }
        return "Application Not Found.";
    }

    List<String> getLenderList(List<LenderAssignmentRules> lenderAssignmentRules, EdiModel ediModel, String assignedLender, Long merchantId, Long applicationId){
        log.info("Assigned Lender: {}  EdiModel: {}", assignedLender, ediModel );
        List<String> eligibleLenders = new ArrayList<>();
        log.info("lender assignment rules: {}", lenderAssignmentRules);
        log.info("is internal merchant {}", loanUtil.isInternalMerchant(merchantId));
        for(LenderAssignmentRules rule:lenderAssignmentRules){
            String lender = rule.getLender();
            log.info("running skip check for lender {} for  {}", lender, merchantId);
            if(ObjectUtils.isEmpty(ediModel) || ediModel.name().equals(LenderOffDays.valueOf(lender).getEdiModel().name())){
                if(!ObjectUtils.isEmpty(assignedLender) && rule.getLender().equals(assignedLender)){
                    log.info("lender change workflow, skip {} for {}", lender, merchantId);
                    continue;
                }
                log.info("adding {} to the eligible list for merchantId: {}", lender, merchantId);
                if(Lender.PIRAMAL.name().equalsIgnoreCase(lender) && !loanUtil.isInternalMerchant(merchantId) && !easyLoanUtil.percentScaleUp(merchantId, piramalRolloutPercentage)) {
                    log.info("removing {} from eligible list for merchantId : {} due to not in rollout percentage {}", lender, merchantId, piramalRolloutPercentage);
                    String remarks = "removing " + lender + " from eligible list for merchantId : " + merchantId + " due to not in rollout percentage " + piramalRolloutPercentage;
                    createAndSaveLendingAuditTrial(applicationId, merchantId, lender, "LENDER_REMOVED", remarks);
                    continue;
                }
                if(lenderRolloutFailedCheck(lender, merchantId)) {
                    continue;
                }
                eligibleLenders.add(lender);
            }
        }
        log.info("Eligible Lenders: {}", eligibleLenders);
        return eligibleLenders;
    }

    private boolean lenderRolloutFailedCheck(String lender, Long merchantId) {
        List<Lender> skipRolloutCheckForLenders = Arrays.asList(LDC, MAMTA, HINDON, LIQUILOANS, LIQUILOANS_NBFC, LIQUILOANS_P2P, LIQUILOANS_P2P_OF, MAMTA0, MAMTA1, MAMTA2, ABFL,PIRAMAL);
        if(skipRolloutCheckForLenders.contains(Lender.valueOf(lender))) {
            return false;
        }
        Integer rolloutPercent = 0;
        switch (lender) {
            case "USFB":
                rolloutPercent = usfbRolloutPercentage;
                break;
            case "TRILLIONLOANS":
                rolloutPercent = trillionLoansRolloutPercentage;
                break;
            case "MUTHOOT":
                rolloutPercent = muthootRolloutPercentage;
                break;
            case "CAPRI":
                rolloutPercent = capriRolloutPercent;
                break;
            case "PAYU":
                rolloutPercent = payuRolloutPercent;
                break;
            case "CREDITSAISON":
                rolloutPercent = csConfig.getRolloutPercent();
                break;
            case "SMFG":
                rolloutPercent = smfgConfig.getRolloutPercentage();
                break;
            case "UGRO":
                rolloutPercent = ugroConfig.getRolloutPercentage();
                break;
            case "OXYZO":
                rolloutPercent = oxyzoConfig.getRolloutPercentage();
                break;
            default:
                rolloutPercent = 0;
        }
        if(!loanUtil.isInternalMerchant(merchantId) && !easyLoanUtil.percentScaleUp(merchantId, rolloutPercent)) {
            log.info("removing {} from eligible lender list for merchantId : {} due to not in rollout percentage {}", lender, merchantId, rolloutPercent);
            return true;
        }
        return false;
    }

    public Lender modifyLender(Long applicationId){
        Optional<LendingApplication> application = lendingApplicationDao.findById(applicationId);
        if(application.isPresent()){
            log.info("Modifying lender for application:{}", application.get().getId());
            String eligibleLenders = lendingAuditTrialDao.findByApplicationIdAndMerchantIdAndTypeOrderByIdDesc(application.get().getId(),
                    application.get().getMerchantId(), "ELIGIBLE_LENDER");
            List<String> initialEligibleLenders = ObjectUtils.isEmpty(eligibleLenders) ? new ArrayList<>() : Arrays.asList(eligibleLenders.split(","));
            log.info("Initial eligible lenders for applicationId : {} {}", application.get().getId(), initialEligibleLenders);
            List<String> alreadyAssignedLender = lendingApplicationLenderDetailsDao.findLendersByApplicationId(applicationId);
            log.info("Already assigned lenders for applicationId : {} {}", application.get().getId(), alreadyAssignedLender);
            List<String> availableLenders = initialEligibleLenders.stream().filter(lender -> !alreadyAssignedLender.contains(lender)).collect(Collectors.toCollection(ArrayList::new));
            log.info("Available lenders {} from initial eligible lenders for applicationId : {} ", availableLenders, application.get().getId());
            if(availableLenders.size() > 0 && alreadyAssignedLender.size() < maxEligibleLendersCountForModify) {
                LendingApplicationDetails ediDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
                String decidedLender = getLender(application.get(), availableLenders, EdiModel.valueOf(ediDetails.getEdiModel()), false, null);
                if(!ObjectUtils.isEmpty(decidedLender)) {
                    log.info("assigning lender {} from available lenders {} for applicationId {} with old lender {}", decidedLender, availableLenders, applicationId, application.get().getLender());
                    EdiModel ediModel = EdiModel.valueOf(ediDetails.getEdiModel());
                    String oldLender = application.get().getLender();
                    application.get().setLender(decidedLender);
                    updateOfferDetails(application.get(), decidedLender);
                    lendingApplicationDao.save(application.get());
                    updateOfferDetailsInApplication(application.get(), ediModel, oldLender);
                    return Lender.valueOf(decidedLender);
                }
            }
            LendingLenderQuota fallbackLender = lenderDisbursalLimitsDao.findByEdiModelIsNull();
            if(!ObjectUtils.isEmpty(fallbackLender) && !alreadyAssignedLender.contains(fallbackLender.getLender())){
                LendingApplicationDetails ediDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
                LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(application.get().getMerchantId());
                RiskVariablesDTO riskVariables = EntityToDtoConvertorUtil.convertToRiskVariablesDTO(lendingRiskVariables);

                LendingLenderPricing lenderPricing = lendingLenderPricingDao.findBySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColor(
                        lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                        application.get().getTenureInMonths(), fallbackLender.getLender(), lendingRiskVariables.getPincodeColor().name(), application.get().getCreatedAt());
                if (Objects.nonNull(lenderPricing)) {
                    Map<String, LendingLenderPricing> lenderPricingMap = new HashMap<>();
                    lenderPricingMap.put(lenderPricing.getLender(), lenderPricing);
                    riskVariables.setLenderPricingMap(lenderPricingMap);
                }

                if(baseCheckForWildCardLender && ! lenderBaseChecksCleared(
                        application.get(), fallbackLender.getLender(), EdiModel.valueOf(ediDetails.getEdiModel()), riskVariables)){
                    log.info("irr_apr check failed for fallback lender: {}, for application: {}", fallbackLender.getLender(), application.get().getId());
                    return null;
                }
                log.info("assigning fallback lender for applicationId and lender : {} {}", applicationId, application.get().getLender());
                EdiModel ediModel = EdiModel.valueOf(ediDetails.getEdiModel());
                String oldLender = application.get().getLender();
                String newLender = assignFallackLender(application.get(), ediModel);
                application.get().setLender(newLender);
                updateOfferDetails(application.get(), newLender);
                lendingApplicationDao.save(application.get());
                updateOfferDetailsInApplication(application.get(),ediModel, oldLender);
                return Lender.valueOf(newLender);
            } else {
                log.info("Fallback lender already assigned for applicationId : {}", application.get().getId());
                return null;
            }
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
            return LendingConstants.NONE_LENDER;
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
            LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(lendingApplication.getMerchantId(), Arrays.asList("ACTIVE"));
            if(topupLenders.contains(activeLoan.getNbfc())){
                lender = topupLenderMapper(activeLoan.getNbfc());
                if(!StringUtils.isEmpty(lender)){
                    updateLendingApplication(lendingApplication, lender);
                }
                lendingApplication.setLender(lender);
                lendingApplicationDao.save(lendingApplication);
                saveLenderChangeAudit(lendingApplication, lender, lendingApplication.getLender());
                ediModelAudit(lendingApplication, LenderOffDays.valueOf(lender).getEdiModel());
            }else {
                log.error("Lender could not be assigned for application:{}", lendingApplication.getId());
                throw new RuntimeException("Lender not same/Not eligible for topup loans");
            }
        }
        return lender;
    }

    private void updateLendingApplication(@NotNull LendingApplication lendingApplication, @NotNull String lender) {
        Double interestAmt = (lendingApplication.getLoanAmount() * (lendingApplication.getInterestRate() * lendingApplication.getTenureInMonths()) / 100);
        double ediAmount = ((lendingApplication.getLoanAmount() + interestAmt) / lendingApplication.getPayableDays());
        ediAmount = ediUtil.getEdiAfterRoundingLogic(lendingApplication.getId(), ediAmount, lender);
        Double repayment = ediAmount * lendingApplication.getPayableDays();
        lendingApplication.setEdi(ediAmount);
        lendingApplication.setRepayment(repayment);
    }

    public static String topupLenderMapper(String prevLender){

        if(trillionTopupLenders.contains(prevLender)) return TRILLIONLOANS.toString();

        if(LDC.toString().equals(prevLender)) return LIQUILOANS_NBFC.toString();

        if(LIQUILOANS_NBFC.toString().equals(prevLender)) return LIQUILOANS_NBFC.toString();

        if(LIQUILOANS_P2P.toString().equals(prevLender) || LIQUILOANS_P2P_OF.toString().equals(prevLender)) return LIQUILOANS_P2P.toString();

        if(ABFL.name().equalsIgnoreCase(prevLender)) return ABFL.name();

        if(PIRAMAL.name().equalsIgnoreCase(prevLender)) return PIRAMAL.name();

        return null;
    }

    public void updateOfferDetailsInApplication(LendingApplication lendingApplication, EdiModel ediModel, String oldLender) {
        try {
            if(LendingConstants.NONE_LENDER.equalsIgnoreCase(lendingApplication.getLender())){
                log.info("skipping updated offer for {} lender of {}",lendingApplication.getLender(), lendingApplication.getId());
                return;
            }
            Long currentPayableDays = (long) OfferUtils.getEdiDays(lendingApplication.getTenureInMonths(), ediModel);
            if (currentPayableDays == lendingApplication.getPayableDays()) {
                log.info("skipping updated offer as offer remains same for {}", lendingApplication.getId());
                return;
            }
            log.info("modifying application details post lender change for {}", lendingApplication.getId());
            Long payableDays = (long) OfferUtils.getEdiDays(lendingApplication.getTenureInMonths(), ediModel);
            Double interestAmt = (lendingApplication.getLoanAmount() * (lendingApplication.getInterestRate() * lendingApplication.getTenureInMonths()) / 100) ;
            Double edi = ((lendingApplication.getLoanAmount() + interestAmt) / payableDays);
            edi = ediUtil.getEdiAfterRoundingLogic(lendingApplication.getId(), edi, lendingApplication.getLender());
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
                if (flag) {
                    String remarks = "skipping " + lender + " due to max Dpd 6 months: " + responseDTO.getVariables().getMaxDpd6Months() + " is greater than 30 for " + lendingApplication.getId();
                    createAndSaveLendingAuditTrial(lendingApplication.getId(), lendingApplication.getMerchantId(), lender.name(), "LENDER_REMOVED", remarks);
                }
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

    public boolean maxIrrCheckFailed(LendingApplication lendingApplication, EdiModel ediModel, String lender) {
        Double irr = lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getLoanAmount(), ediModel.getNoOfEdiDaysInAWeek(), lender);
        log.info("IRR generated for application_id:{} IRR:{} and lender:{}", lendingApplication.getId(), irr, lender);
        Double maxIrr = 0.0D;
        switch (lender) {
            case "CREDITSAISON":
                maxIrr = csConfig.getMaxIRR();
                break;
            case "MUTHOOT":
                maxIrr = muthootMaxIrr;
                break;
            case "SMFG":
                maxIrr = smfgConfig.getMaxApr();
                break;
            case "PIRAMAL":
                maxIrr = piramalMaxIrr;
                break;
            case "UGRO":
                maxIrr = ugroConfig.getMaxIrr();
                break;
            case "OXYZO":
                maxIrr = oxyzoConfig.getMaxIrr();
                break;
            default:
                maxIrr = 0D;
        }
        return irr > maxIrr;
    }


    public boolean maxAprCheckFailed(LendingApplication lendingApplication, EdiModel ediModel, String lender) {
        Double apr = lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getLoanAmount() - lendingApplication.getProcessingFee(), ediModel.getNoOfEdiDaysInAWeek(), lender);
        log.info("APR generated for application_id:{} APR:{} and lender:{}", lendingApplication.getId(), apr, lender);
        Double maxApr = 0D;
        switch (lender) {
            case "PIRAMAL":
                maxApr = piramalMaxApr;
                break;
            case "CREDITSAISON":
                maxApr = csConfig.getMaxApr();
                break;
            default:
                maxApr = 0D;
        }
        return apr > maxApr;
    }

    private boolean maxPfCheckFailed(LendingApplication lendingApplication, String lender) {
        Double processingFee =  lendingApplication.getProcessingFee();
        Double loanAmount= lendingApplication.getLoanAmount();
        log.info("PF generated for application_id:{} PF:{} and lender:{}", lendingApplication.getId(), processingFee, lender);
        Double pfPercentage = (processingFee/loanAmount)*100D;
        Double maxPf = 0D;
        switch (lender) {
            case "SMFG":
                maxPf = smfgConfig.getMaxProcessingFee();
                break;
            default:
                maxPf = 0D;
        }
        return pfPercentage > maxPf;
    }

    public boolean maxAprCheckFailedV2(LendingApplication lendingApplication, EdiModel ediModel, String lender, RiskVariablesDTO riskVariables) {
        BigDecimal maxApr = BigDecimal.ZERO;
        double processingFee = lendingApplication.getProcessingFee();
        LendingLenderPricing lendingLenderPricing = !CollectionUtils.isEmpty(riskVariables.getLenderPricingMap()) ? riskVariables.getLenderPricingMap().get(lender) : null;
        log.info("Lending Lender pricing fetched : {}", lendingLenderPricing);

        if (!ObjectUtils.isEmpty(lendingLenderPricing)) {
            maxApr = BigDecimal.valueOf(lendingLenderPricing.getApr());
            processingFee = lendingApplication.getLoanAmount() * (lendingLenderPricing.getProcessingFeeRate() / 100);
        }

        LoanApplicationDetailsDto loanApplicationDetailsDto = LoanApplicationDetailsDto.builder().id(lendingApplication.getId()).
                edi(lendingApplication.getEdi()).tenureInMonths(lendingApplication.getTenureInMonths()).
                loanAmount(lendingApplication.getLoanAmount()).payableDays(lendingApplication.getPayableDays()).
                lender(lendingApplication.getLender()).build();
        log.info("Processing fee {}, loan amount : {}, edi model : {} of loan id : {}", processingFee, lendingApplication.getLoanAmount(), ediModel.getNoOfEdiDaysInAWeek(), lendingApplication.getId());
        Double apr = lendingApplicationServiceV2.getAprForBaseChecks(loanApplicationDetailsDto, lendingApplication.getLoanAmount() - processingFee, ediModel.getNoOfEdiDaysInAWeek(), lender, lendingLenderPricing);
        log.info("Calculated APR : {}, APR in DB : {}, application id : {}", apr, maxApr, lendingApplication.getId());
        return BigDecimal.valueOf(apr).setScale(2, RoundingMode.DOWN).compareTo(maxApr.setScale(2, RoundingMode.DOWN)) > 0;
    }

    public boolean maxIrrCheckFailedV2(LendingApplication lendingApplication, EdiModel ediModel, String lender, RiskVariablesDTO riskVariables) {
        BigDecimal maxIrr = BigDecimal.ZERO;
        LendingLenderPricing lendingLenderPricing = !CollectionUtils.isEmpty(riskVariables.getLenderPricingMap()) ? riskVariables.getLenderPricingMap().get(lender) : null;
        log.info("Lending Lender pricing fetched : {}", lendingLenderPricing);
        if (!ObjectUtils.isEmpty(lendingLenderPricing)) {
            maxIrr = BigDecimal.valueOf(lendingLenderPricing.getIrr());
        }

        LoanApplicationDetailsDto loanApplicationDetailsDto = LoanApplicationDetailsDto.builder().id(lendingApplication.getId()).
                edi(lendingApplication.getEdi()).tenureInMonths(lendingApplication.getTenureInMonths()).
                loanAmount(lendingApplication.getLoanAmount()).payableDays(lendingApplication.getPayableDays()).
                lender(lendingApplication.getLender()).build();
        log.info("loan amount : {}, edi model : {} of loan id : {}", lendingApplication.getLoanAmount(), ediModel.getNoOfEdiDaysInAWeek(), lendingApplication.getId());
        Double apr = lendingApplicationServiceV2.getAprForBaseChecks(loanApplicationDetailsDto, lendingApplication.getLoanAmount(), ediModel.getNoOfEdiDaysInAWeek(), lender, lendingLenderPricing);

        log.info("Calculated IRR : {}, IRR in DB : {}, application id : {}", apr, maxIrr, lendingApplication.getId());
        return BigDecimal.valueOf(apr).setScale(2, RoundingMode.DOWN).compareTo(maxIrr.setScale(2, RoundingMode.DOWN)) > 0;
    }

    public boolean maxAprCheckFailedV2(LendingEligibleLoan eligibleLoan, EdiModel ediModel, String lender, RiskVariablesDTO riskVariables) {
        BigDecimal maxApr = BigDecimal.ZERO;
        double processingFee = eligibleLoan.getProcessingFee();
        LendingLenderPricing lendingLenderPricing = !CollectionUtils.isEmpty(riskVariables.getLenderPricingMap()) ? riskVariables.getLenderPricingMap().get(lender) : null;
        log.info("Lending Lender pricing fetched : {}", lendingLenderPricing);

        if (!ObjectUtils.isEmpty(lendingLenderPricing)) {
            maxApr = BigDecimal.valueOf(lendingLenderPricing.getApr());
            processingFee = eligibleLoan.getAmount() * (lendingLenderPricing.getProcessingFeeRate() / 100);
        }

        LoanApplicationDetailsDto loanApplicationDetailsDto = LoanApplicationDetailsDto.builder().id(eligibleLoan.getId()).
                edi(Double.valueOf(eligibleLoan.getEdi())).tenureInMonths(eligibleLoan.getTenureInMonths()).
                loanAmount(eligibleLoan.getAmount()).payableDays(Long.valueOf(eligibleLoan.getEdiCount())).
                lender(lender).build();
        log.info("Processing fee {}, loan amount : {}, edi model : {} of loan id : {}", processingFee, eligibleLoan.getAmount(), ediModel.getNoOfEdiDaysInAWeek(), eligibleLoan.getId());
        Double apr = lendingApplicationServiceV2.getAprForBaseChecks(loanApplicationDetailsDto, eligibleLoan.getAmount() - processingFee, ediModel.getNoOfEdiDaysInAWeek(), lender, lendingLenderPricing);
        log.info("Calculated APR : {}, APR in DB : {}, application id : {}", apr, maxApr, eligibleLoan.getId());
        return BigDecimal.valueOf(apr).setScale(2, RoundingMode.DOWN).compareTo(maxApr.setScale(2, RoundingMode.DOWN)) > 0;
    }

    public boolean maxIrrCheckFailedV2(LendingEligibleLoan eligibleLoan, EdiModel ediModel, String lender, RiskVariablesDTO riskVariables) {
        BigDecimal maxIrr = BigDecimal.ZERO;
        LendingLenderPricing lendingLenderPricing = !CollectionUtils.isEmpty(riskVariables.getLenderPricingMap()) ? riskVariables.getLenderPricingMap().get(lender) : null;
        log.info("Lending Lender pricing fetched : {}", lendingLenderPricing);
        if (!ObjectUtils.isEmpty(lendingLenderPricing)) {
            maxIrr = BigDecimal.valueOf(lendingLenderPricing.getIrr());
        }

        LoanApplicationDetailsDto loanApplicationDetailsDto = LoanApplicationDetailsDto.builder().id(eligibleLoan.getId()).
                edi(Double.valueOf(eligibleLoan.getEdi())).tenureInMonths(eligibleLoan.getTenureInMonths()).
                loanAmount(eligibleLoan.getAmount()).payableDays(Long.valueOf(eligibleLoan.getEdiCount())).
                lender(lender).build();
        log.info("loan amount : {}, edi model : {} of loan id : {}", eligibleLoan.getAmount(), ediModel.getNoOfEdiDaysInAWeek(), eligibleLoan.getId());
        Double apr = lendingApplicationServiceV2.getAprForBaseChecks(loanApplicationDetailsDto, eligibleLoan.getAmount(), ediModel.getNoOfEdiDaysInAWeek(), lender, lendingLenderPricing);

        log.info("Calculated IRR : {}, IRR in DB : {}, application id : {}", apr, maxIrr, eligibleLoan.getId());
        return BigDecimal.valueOf(apr).setScale(2, RoundingMode.DOWN).compareTo(maxIrr.setScale(2, RoundingMode.DOWN)) > 0;
    }


    private boolean maxPfCheckFailedV2(LendingApplication lendingApplication, String lender, RiskVariablesDTO riskVariables) {
        Double processingFee =  lendingApplication.getProcessingFee();
        Double loanAmount= lendingApplication.getLoanAmount();
        log.info("PF generated for application_id:{} PF:{} and lender:{}", lendingApplication.getId(), processingFee, lender);
        Double pfPercentage = (processingFee/loanAmount)*100D;

        LendingLenderPricing lendingLenderPricing = !CollectionUtils.isEmpty(riskVariables.getLenderPricingMap()) ? riskVariables.getLenderPricingMap().get(lender) : null;
        log.info("Lending Lender pricing fetched : {}", lendingLenderPricing);
        if (!ObjectUtils.isEmpty(lendingLenderPricing)) {
            pfPercentage = lendingLenderPricing.getProcessingFeeRate();
        }
        Double maxPf = 0D;
        switch (lender) {
            case "SMFG":
                maxPf = smfgConfig.getMaxProcessingFee();
                break;
            default:
                maxPf = 0D;
        }
        return pfPercentage > maxPf;
    }

    public List<LenderAggregationResponseDto.LenderData> getLenderData(List<String> eligibleLenders, List<String> prevAssignedLenders, LendingApplication lendingApplication) {
        try {
            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingApplication.getMerchantId());
            LendingLenderQuota defaultLender = lenderDisbursalLimitsDao.findByEdiModelIsNull();
            if(!ObjectUtils.isEmpty(eligibleLenders)) {
                List<LendingLenderQuota> lenderLimits;
                lenderLimits = lenderDisbursalLimitsDao.fetchEligibleLenderLimits(eligibleLenders, lendingApplication.getLoanAmount());
                eligibleLenders.clear();
                log.info("lender limits : {}", lenderLimits);
                if (Objects.nonNull(lenderLimits)) {
                    for (LendingLenderQuota lendingLenderQuota : lenderLimits) {
                        if(Objects.nonNull(defaultLender) && lendingLenderQuota.getLender().equals(defaultLender.getLender())){
                            continue;
                        }
                        eligibleLenders.add(lendingLenderQuota.getLender());
                    }
                    log.info("eligible lenders:{}", eligibleLenders);
                }
            }
            List<LenderAggregationResponseDto.LenderData> eligibleLenderList = new ArrayList<>();
            addDefaultLender(eligibleLenders, prevAssignedLenders, defaultLender);
            log.info("previous lenders:{}", prevAssignedLenders);

            for (String lender : eligibleLenders) {
                LendingLenderPricing lendingLenderPricing = null;
                if(loanUtil.isLenderPricingApplicableMerchant(lendingApplication.getMerchantId())){
                    lendingLenderPricing = lendingLenderPricingDao.findBySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColor(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(), lendingApplication.getTenureInMonths(), lender,
                            lendingRiskVariables.getPincodeColor().name(), lendingApplication.getCreatedAt());
                }
                if (Objects.nonNull(prevAssignedLenders) && prevAssignedLenders.contains(lender)) {
                    continue;
                }
                log.info("adding lender {} to list", lender);
                Double apr;
                Double irr;
                if(!ObjectUtils.isEmpty(lendingLenderPricing) && loanUtil.isLenderPricingApplicableMerchant(lendingApplication.getMerchantId())){
                    apr = lendingLenderPricing.getApr();
                    irr = lendingLenderPricing.getIrr();
                }
                else{
                    apr = lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getLoanAmount() - lendingApplication.getProcessingFee(), LenderOffDays.valueOf(lender).getEdiModel().getNoOfEdiDaysInAWeek(), lender);
                    irr = lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getLoanAmount(), LenderOffDays.valueOf(lender).getEdiModel().getNoOfEdiDaysInAWeek(), lender);
                }
                LenderAggregationResponseDto.LenderData lenderData = new LenderAggregationResponseDto.LenderData();
                lenderData.setPenaltyConfigs(getPenaltyConfig(lender));
                lenderData.setLenderName(lender);
                lenderData.setApr(apr);
                lenderData.setIrr(irr);
                lenderData.setRejected(Objects.nonNull(prevAssignedLenders) && prevAssignedLenders.contains(lender));
                lenderData.setApprovalRate(getPropensityMatrix(Lender.valueOf(lender)));
                lenderData.setForeClosureEntityDTOList(getForeclosureAmount(Lender.valueOf(lender)));
                lenderData.setNachBounceAmount(getNachBounceAmount(Lender.valueOf(lender)));
                eligibleLenderList.add(lenderData);
            }

            log.info("adding rejected lenders to the list for lendingApplication:{}:{}", lendingApplication.getId(), prevAssignedLenders);
            if (Objects.nonNull(prevAssignedLenders)) {
                for (String lender : prevAssignedLenders) {
                    LenderAggregationResponseDto.LenderData lenderData = new LenderAggregationResponseDto.LenderData();
                    lenderData.setLenderName(lender);
                    lenderData.setRejected(true);
                    lenderData.setApr(lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getLoanAmount(), LenderOffDays.valueOf(lender).getEdiModel().getNoOfEdiDaysInAWeek(), lender));
                    lenderData.setApprovalRate(getPropensityMatrix(Lender.valueOf(lender)));
                    lenderData.setPenaltyConfigs(getPenaltyConfig(lender));
                    eligibleLenderList.add(lenderData);

                }
            }
            return eligibleLenderList;
        } catch (Exception ex) {
            log.info("exception occurred:{},{}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            return null;
        }
    }

    public String getPropensityMatrix(Lender lender) {
        Map<Lender, String> propensityMap = new HashMap<Lender, String>() {{
            put(ABFL, "HIGH");
            put(MUTHOOT, "MEDIUM");
            put(CAPRI, "MEDIUM");
            put(CREDITSAISON, "LOW");
            put(PIRAMAL, "LOW");
            put(SMFG, "LOW");
            put(TRILLIONLOANS, "HIGH");
            put(LIQUILOANS_P2P, "HIGH");
            put(PAYU, "MEDIUM");

        }};

        return propensityMap.getOrDefault(lender, "LOW");
    }

    public Integer getNachBounceAmount(Lender lender) {
        Map<Lender, Integer> nachBounceAmountMap = new HashMap<Lender, Integer>() {{
            put(LIQUILOANS_P2P, 650);
            put(LIQUILOANS_P2P_OF, 650);
        }};
        return nachBounceAmountMap.getOrDefault(lender, null);
    }

    public void addDefaultLender(List<String> lenders, List<String> prevAssignedLenders, LendingLenderQuota defaultLender) {

        LendingLenderQuota wildcardLender = null;
        if (ObjectUtils.isEmpty(lenders)) {
            wildcardLender = lenderDisbursalLimitsDao.findByClassification(LendingLenderQuota.Classification.WILDCARD.name());
            if(Objects.nonNull(wildcardLender)){
                log.info("no eligible lenders found for app. Adding wildcard lender:{} to list", wildcardLender.getLender());
                lenders.add(wildcardLender.getLender());
                return;
            }
        }


        if (Objects.nonNull(defaultLender)) {
            if (ObjectUtils.isEmpty(prevAssignedLenders) || (prevAssignedLenders.size() + 1) < maxLenderAssignThreshold) {
                lenders.add(defaultLender.getLender());
                log.info("adding default lender at last");

            } else {
                lenders.add(0, defaultLender.getLender());
                log.info("adding default lender at top");
            }
        } else{
            defaultLender = lenderDisbursalLimitsDao.findByEdiModelIsNull();
            if(ObjectUtils.isEmpty(defaultLender)){
                return;
            }
            if (ObjectUtils.isEmpty(prevAssignedLenders) || (prevAssignedLenders.size() + 1) < maxLenderAssignThreshold) {
                lenders.add(defaultLender.getLender());
                log.info("adding default lender at last");

            } else {
                lenders.add(0, defaultLender.getLender());
                log.info("adding default lender at top");
            }
        }
    }

    public List<LenderAggregationResponseDto.LenderData.PenaltyConfig> getPenaltyConfig(String lender) {
        List<PenaltyFeeConfigSlave> penaltyFeeConfigSlaves = penaltyFeeConfigDaoSlave.findByVersionAndStatusAndLenderOrderByMinAmountAsc(2D, true, lender);
        log.info("penal charges for lender:{}:{}", lender, penaltyFeeConfigSlaves);
        List<LenderAggregationResponseDto.LenderData.PenaltyConfig> penaltyConfigs = new ArrayList<>();
        if (!ObjectUtils.isEmpty(penaltyFeeConfigSlaves)) {
            for (PenaltyFeeConfigSlave penaltyFeeConfigSlave : penaltyFeeConfigSlaves) {
                LenderAggregationResponseDto.LenderData.PenaltyConfig penaltyConfig = new LenderAggregationResponseDto.LenderData.PenaltyConfig();
                penaltyConfig.setMinAmount(penaltyFeeConfigSlave.getMinAmount());
                penaltyConfig.setMaxAmount(penaltyFeeConfigSlave.getMaxAmount());
                penaltyConfig.setPenalty(penaltyFeeConfigSlave.getPenalty());
                penaltyConfigs.add(penaltyConfig);
            }
        }
        return penaltyConfigs;
    }

    List<LenderAggregationResponseDto.LenderData.ForeClosureEntityDTO> getForeclosureAmount(Lender lender){

        List<ForeClosureConfig> foreClosureConfigs = foreClosureDao.findByLender(lender.name());
        return convertToForeClosureEntityDto(foreClosureConfigs);

    }
    private List<LenderAggregationResponseDto.LenderData.ForeClosureEntityDTO> convertToForeClosureEntityDto(List<ForeClosureConfig> foreClosureConfigs) {
        List<LenderAggregationResponseDto.LenderData.ForeClosureEntityDTO> foreClosureEntityDTOList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(foreClosureConfigs)) {
            for (ForeClosureConfig foreClosureConfig: foreClosureConfigs){
                LenderAggregationResponseDto.LenderData.ForeClosureEntityDTO foreClosureEntityDTO = new LenderAggregationResponseDto.LenderData.ForeClosureEntityDTO();
                foreClosureEntityDTO.setRate(foreClosureConfig.getRate());
                foreClosureEntityDTO.setDurationFrom(foreClosureEntityDTO.getDurationFrom());
                foreClosureEntityDTO.setDurationTo(foreClosureConfig.getDurationTo());
                foreClosureEntityDTO.setMinAmount(foreClosureConfig.getMinAmount());
                foreClosureEntityDTO.setTenure(foreClosureConfig.getTenure());
                foreClosureEntityDTOList.add(foreClosureEntityDTO);
            }
        }
        return foreClosureEntityDTOList;
    }

    public Map<String, Object> assignLender(Long applicationId, BasicDetailsDto merchantDetails, LendingEnum.LENDER lender) {
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantDetails.getId());
        if(ObjectUtils.isEmpty(lendingApplication)){
            log.error("application not found with id :{}", applicationId);

        }
        String oldLender = lendingApplication.getLender();
        lendingApplication.setLender(lender.name());
        lendingApplicationDao.save(lendingApplication);
        updateOfferDetails(lendingApplication, lender.name());
        log.info("assigning lender:{} for application:{}", lender, lendingApplication.getId());
        LendingLenderQuota lendingLenderQuota = lenderDisbursalLimitsDao.findByLender(lender.name());
        if(!ObjectUtils.isEmpty(lendingLenderQuota)) {
            updateLenderLimits(lendingLenderQuota, lendingApplication);
        }
        saveLenderChangeAudit(lendingApplication, lender.name(), oldLender);
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        if(ObjectUtils.isEmpty(lendingApplicationDetails)){
            lendingApplicationDetails = new LendingApplicationDetails();
            lendingApplicationDetails.setApplicationId(lendingApplicationDetails.getId());
        }
        lendingApplicationDetails.setLenderAssc(Boolean.FALSE);
        lendingApplicationDetails.setStage(LenderAssociationStages.INIT.name());
        lendingApplicationDetailsDao.save(lendingApplicationDetails);

        Map<String, Object> response = new HashMap<>();
        response.put("success", !ObjectUtils.isEmpty(lendingApplication));
        Boolean bpKycRequired = lendingApplicationServiceV3Base.checkForBPKycRequired(lendingApplication);
        LendingApplicationKycDetails lendingApplicationKycDetails = null;
        if(bpKycRequired){
            lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), oldLender);
        }
        response.put("bpKycRequired", !ObjectUtils.isEmpty(lendingApplicationKycDetails));
        return response;
    }

    public List<LenderAggregationResponseDto.LenderData> getEligibleLenderList(LendingApplication lendingApplication, List<String> prevLenders) {
        LendingAuditTrial lendingAuditTrial = lendingAuditTrialDao.findTopByApplicationIdAndType(lendingApplication.getId(), "ELIGIBLE_LENDER");

        if (!ObjectUtils.isEmpty(lendingAuditTrial) && !ObjectUtils.isEmpty(lendingAuditTrial.getOldStatus())){
            List<String> eligibleLenders = new ArrayList<>(Arrays.asList(lendingAuditTrial.getOldStatus().split(",")));
            return getLenderData(eligibleLenders, prevLenders, lendingApplication);
        }
        return null;
    }

    private void createAndSaveLendingAuditTrial(Long applicationId, Long merchantId,  String oldStatus, String type, String remarks) {
        try {
            log.info("Auditing lender remove log for applicationId {}", applicationId);
            LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
            lendingAuditTrial.setApplicationId(applicationId);
            lendingAuditTrial.setMerchantId(merchantId);
            lendingAuditTrial.setOldStatus(oldStatus);
            lendingAuditTrial.setType(type);
            lendingAuditTrial.setRemarks(remarks);
            lendingAuditTrial.setLoanId("BPL" + applicationId);
            lendingAuditTrialDao.save(lendingAuditTrial);
            log.info("Details getting saved in Lending audit Trial");
        } catch (Exception e) {
            log.info("Exception in saving lender remove log for applicationId {} {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
    }
    public void updateOfferDetails(LendingApplication lendingApplication, String newLender){
        if(!loanUtil.isLenderPricingApplicableMerchant(lendingApplication.getMerchantId())) return;
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingApplication.getMerchantId());
        LendingLenderPricing lendingLenderPricing = lendingLenderPricingDao.findBySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColor(
                lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(), lendingApplication.getTenureInMonths(), newLender, lendingRiskVariables.getPincodeColor().name(), lendingApplication.getCreatedAt());
        if(!ObjectUtils.isEmpty(lendingLenderPricing)){
            Long payableDays = (long) OfferUtils.getEdiDays(lendingApplication.getTenureInMonths(), LenderOffDays.valueOf(newLender).getEdiModel());
            Double interestAmt = (lendingApplication.getLoanAmount() * (lendingLenderPricing.getInterestRate() * lendingApplication.getTenureInMonths()) / 100) ;
            double ediAmount = ((lendingApplication.getLoanAmount() + interestAmt) / payableDays);
            ediAmount = ediUtil.getEdiAfterRoundingLogic(lendingApplication.getId(), ediAmount, newLender);
            Double repayment = ediAmount * payableDays;
            Double processingFee = Math.ceil((lendingLenderPricing.getProcessingFeeRate() * lendingApplication.getLoanAmount()) / 100);

            log.info("Values to set for lender {} : payableDays : {} , interestAmt : {} , ediAmount : {} , repayment : {} , processingFee : {}", newLender, payableDays, interestAmt, ediAmount, repayment, processingFee);

            lendingApplication.setProcessingFee(processingFee);
            lendingApplication.setInterestRate(lendingLenderPricing.getInterestRate());
            lendingApplication.setEdi(ediAmount);
            lendingApplication.setDisbursalAmount(lendingApplication.getLoanAmount()-lendingApplication.getProcessingFee());
            lendingApplication.setRepayment(repayment);

            log.info("Values set for lender {} : interest rate {}, edi {}, disbursal {}", newLender, lendingApplication.getInterestRate(), lendingApplication.getEdi(), lendingApplication.getDisbursalAmount());
            lendingApplicationDao.save(lendingApplication);
        }
    }

    public String lenderAssignmentHandlerV2(LendingApplication application, EdiModel ediModel, BasicDetailsDto merchantDetails, Boolean isApplicableForAggregation) {
        log.info("Running V2 lender assignment handler");
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(application.getMerchantId());
        RiskVariablesDTO riskVariables = EntityToDtoConvertorUtil.convertToRiskVariablesDTO(lendingRiskVariables);

        log.info("riskVariables in lender assignment v2 {}", riskVariables);

        //Change 1 : Fetched lender pricing list
        List<LendingLenderPricing> lenderPricingList = lendingLenderPricingDao.findBySegmentAndRiskGroupAndTenureInMonthsAndPincodeColor(
                lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                application.getTenureInMonths(), lendingRiskVariables.getPincodeColor().name(), application.getCreatedAt());
        if (!CollectionUtils.isEmpty(lenderPricingList)) {
            riskVariables.setLenderPricingMap(lenderPricingList.stream().collect(Collectors.toMap(
                    LendingLenderPricing::getLender,
                    java.util.function.Function.identity(),
                    (existing, duplicate) -> existing  // Keep the first occurrence (latest due to id DESC)
            )));
        }
        try {
            double bureauScore = riskVariables.getBureauScore();
            String riskSegment = riskVariables.getRiskSegment();
            String riskGroupLike = riskVariables.getRiskGroupLike();
            String pincodeColor = ObjectUtils.isEmpty(lendingRiskVariables.getPincodeColor()) ? "" : "%" + lendingRiskVariables.getPincodeColor().name() + "%";
            String tenure = "%" + application.getTenureInMonths() + "%";
            log.info("Lender assignment parameters -> bureau:{}, loanType:{}, tenure:{}, loanAmount:{}, riskGroup:{}, pincodeColor:{}", bureauScore, riskSegment, application.getTenure(),
                    application.getLoanAmount(), riskGroupLike, pincodeColor);
            List<String> lenders = new ArrayList<>();
            String decidedLender = null;
            List<LenderAssignmentRules> ruleList=lenderAssignmentRulesDao.fetchEligibleRules(application.getLoanAmount(), bureauScore, riskSegment, tenure, riskGroupLike, pincodeColor);
            if (loanUtil.isInternalMerchant(application.getMerchantId()) || runInternalRules) {
                ruleList=lenderAssignmentRulesDao.fetchEligibleRulesForInternal(application.getLoanAmount(), bureauScore, riskSegment, tenure, riskGroupLike, pincodeColor);
            }
            log.info("Fetched Rules:{}", ruleList);

            try {
                List<String> eligibleLendersAsPerRules = ruleList.stream().map(LenderAssignmentRules::getLender).collect(Collectors.toCollection(ArrayList::new));
                saveEligibleLenderAudit(application, "", CollectionUtils.isEmpty(eligibleLendersAsPerRules) ? "" : String.join(",", eligibleLendersAsPerRules), "ELIGIBLE_RULE_LENDERS");
            } catch (Exception exception) {
                log.info("exception while logging the lender assignment details under rules based lenders", exception);
            }

            boolean isPanAadhaarLinked = false;
            boolean isPanAadhaarLinkedStatusChecked = false;

            if(!ObjectUtils.isEmpty(ruleList)) {
                lenders = getLenderList(ruleList, ediModel, application.getLender(), application.getMerchantId(), application.getId());
                try {
                    if (!CollectionUtils.isEmpty(lenders)) {
                        ListIterator<String> iterator = lenders.listIterator();
                        while (iterator.hasNext()) {
                            String lender = iterator.next().toUpperCase();
                            if (riskVariables.getRejectedLenders().contains(loanUtil.getLenderRejectedMapping(lender))) {
                                log.info("skipping {} due to lender in rejected lender list for {}", lender, application.getId());
                                String remarks = "skipping " + lender + " due to lender in rejected lender list in lending risk variables for " + application.getId();
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                                continue;
                            }
                            if (lenderEligiblePincodeCheckList.contains(lender)) {

                                boolean shouldCheckPincodeList = true;

                                if (PAYU.name().equalsIgnoreCase(lender)) {
                                    shouldCheckPincodeList = application.getLoanAmount() > 500000;
                                    log.info("Inside payu pincode check : amount {} - shouldCheckPincodeList {} - applicationId {}", application.getLoanAmount(), shouldCheckPincodeList, application.getId());
                                }

                                if (shouldCheckPincodeList) {

                                    LenderEligiblePincodes lenderEligiblePincodes = lenderEligiblePincodesDao.findByLenderAndPincodeAndStatus(
                                            lender, lendingRiskVariables.getPincode(), LenderEligiblePincodes.LenderEligiblePincodesStatus.ACTIVE
                                    );
                                    if (ObjectUtils.isEmpty(lenderEligiblePincodes)) {
                                        funnelService.submitEventV3(application.getMerchantId(), null, application.getId(),
                                                FunnelEnums.StageId.LENDER_ASSIGNMENT, FunnelEnums.StageEvent.LENDER_SKIPPED_NEGATIVE_PINCODE, lender, LoanDetailsConstant.FUNNEL_VERSION_TAG);
                                        log.info("removing lender : {} from eligible as pincode : {} not serviceable", lender, lendingRiskVariables.getPincode());
                                        String remarks = "Removing lender: " + lender + " from eligible as pincode: " + lendingRiskVariables.getPincode() + " not serviceable";
                                        createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                        iterator.remove();
                                        continue;
                                    }
                                }
                            }

                            // Change 2 : Run lender specific base checks
                            if (!lenderBaseChecksCleared(application, lender, ediModel, riskVariables)) {
                                log.info("lender config based base checks failed, skipping {} for {}", lender, application.getId());
                                iterator.remove();
                                continue;
                            }
                            if(!aadhaarSeedingStatusCheckLenders.isEmpty() && aadhaarSeedingStatusCheckLenders.contains(lender)) {

                                // check if pan aadhaar linked status already checked so that we don't call the api again to re check it
                                if (!isPanAadhaarLinkedStatusChecked) {
                                    isPanAadhaarLinked = isPanAndAadhaarLinked(application.getMerchantId());
                                    isPanAadhaarLinkedStatusChecked = true;
                                }

                                log.info("isPanAadhaarLinkedStatusChecked {} isPanAadhaarLinked {} applicationId {}", isPanAadhaarLinkedStatusChecked, isPanAadhaarLinked, application.getId());

                                if (!isPanAadhaarLinked) {
                                    log.info("removing {} from eligible lenders since panAndAdhaar is not linked for applicationId: {} and merchantId : {}", lender, application.getId(), application.getMerchantId());
                                    String remarks = "removing " + lender + " from eligible lenders since panAndAdhaar is not linked for applicationId: " + application.getId() + " and merchantId : " + application.getMerchantId();
                                    createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(),  lender, "LENDER_REMOVED", remarks);
                                    iterator.remove();
                                    continue;
                                }
                            }

                            Pair<Boolean, String> lenderChecksResponse = runLenderChecksForApplication(application, lender, riskVariables);
                            boolean lenderChecksSuccess = lenderChecksResponse.getKey();
                            if (!lenderChecksSuccess) {
                                String remarks = lenderChecksResponse.getValue();
                                log.info(remarks);
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                                continue;
                            }

                            if (additionalChecksFailed(application, Lender.valueOf(lender), merchantDetails)) {
                                log.info("skipping {} due to additional checks failing for {}", lender, application.getId());
                                iterator.remove();
                                continue;
                            }
                            if (!negativeCategoryAndLoanAmountCheckPassed(application, lendingRiskVariables.getRiskSegment(), lender)) {
                                log.info("skipping {} due to business category check failure for {}", lender, application.getId());
                                iterator.remove();
                            }
                            if (riskVariables.getRejectedLenders().contains(loanUtil.getLenderRejectedMapping(lender))) {
                                log.info("skipping {} due to lender in rejected lender list for {}", lender, application.getId());
                                String remarks = "skipping " + lender + " due to lender in rejected lender list in lending risk variables for " + application.getId();
                                createAndSaveLendingAuditTrial(application.getId(), application.getMerchantId(), lender, "LENDER_REMOVED", remarks);
                                iterator.remove();
                            }
                        }
                    }
                } catch (Exception exception) {
                    log.error("exception while custom pincode check for lender for application id : {}, {}", application.getId(), Arrays.asList(exception.getStackTrace()));
                }
            }
            if (ObjectUtils.isEmpty(lenders)) {
                LendingLenderQuota lendingLenderQuota = lenderDisbursalLimitsDao.findByClassification(LendingLenderQuota.Classification.WILDCARD.name());
                if(ObjectUtils.isEmpty(lendingLenderQuota)){
                    return LendingConstants.NONE_LENDER;
                }
                if (isWildcardLenderConfigEnabled && !ObjectUtils.isEmpty(lendingLenderQuota)) {
                    if(baseCheckForWildCardLender && ! lenderBaseChecksCleared(application, lendingLenderQuota.getLender(), ediModel, riskVariables)){
                        log.info("irr_apr check failed for wildcard lender: {}, for application: {}", lendingLenderQuota.getLender(), application.getId());
                        return LendingConstants.NONE_LENDER;
                    }
                    log.info("Assigning Wild Card Lender as : {} for application id : {} because eligible lender list : {}",
                            lendingLenderQuota.getLender() , application.getId(), lenders);
                    try {
                        saveEligibleLenderAudit(application, lendingLenderQuota.getLender(), CollectionUtils.isEmpty(lenders) ? "" : String.join(",", lenders), "WILDCARD_LENDER");
                    } catch (Exception exception) {
                        log.info("exception while logging the lender assignment details", exception);
                    }
                    lenders.add(lendingLenderQuota.getLender());
                }
            }
            if (!isApplicableForAggregation){
                decidedLender = getLender(application, lenders, ediModel, riskVariables.getIsGstOffer(), riskSegment.substring(1, riskSegment.length()-1));
                log.info("lender to be assigned: {} {}", decidedLender, application.getId());
            }
            try {
                saveEligibleLenderAudit(application, ObjectUtils.isEmpty(decidedLender) ? "" : decidedLender, CollectionUtils.isEmpty(lenders) ? "" : String.join(",", lenders), "ELIGIBLE_LENDER");
            } catch (Exception exception) {
                log.info("exception while logging the lender assignment details", exception);
            }

            return decidedLender;
        } catch(Exception ex){
            log.error("Exception occurred while assigning lender : {}, {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            return null;
        }
    }

    private Pair<Boolean, String> runLenderChecksForApplication(LendingApplication application, String lender, RiskVariablesDTO riskVariables) {
        boolean success = true;
        String response = null;
        Lender lenderEnum = Lender.valueOf(lender);

        long applicationId = application.getId();
        double loanAmount = application.getLoanAmount();
        Double summaryTpv = riskVariables.getSummaryTpv();
        String riskSegment = riskVariables.getRiskSegment();
        if(!ObjectUtils.isEmpty(riskSegment)) {
            riskSegment = riskSegment.replace("%", "");
        }
        int maxTenure = riskVariables.getMaxTenure();
        double tpvOffer = riskVariables.getTpvOffer();
        if(maxTenure != 0 && tpvOffer != 0D && !ObjectUtils.isEmpty(application.getTenureInMonths()) && application.getTenureInMonths() != 0) {
            tpvOffer = (tpvOffer / maxTenure) * application.getTenureInMonths();
        }

        double edi = application.getEdi();
        LendingLenderPricing lenderPricing = riskVariables.getLenderPricingMap().get(lender);
        if (!ObjectUtils.isEmpty(lenderPricing)) {
            Long payableDays = (long) OfferUtils.getEdiDays(application.getTenureInMonths(), LenderOffDays.valueOf(lender).getEdiModel());
            Double interestAmt = (loanAmount * (lenderPricing.getInterestRate() * application.getTenureInMonths()) / 100) ;
            edi = ((loanAmount + interestAmt) / payableDays);
            edi = ediUtil.getEdiAfterRoundingLogic(applicationId, edi, lender);
        }

        switch (lenderEnum) {
            case CAPRI:
                if (edi > summaryTpv) {
                    response = "skipping capri for application id : " + applicationId + " due to merchant edi: " + edi + " is greater than summaryTpv: " + summaryTpv;
                    success = false;
                    break;
                }
                break;
            case MUTHOOT:
                if (loanAmount > (Math.round(tpvOffer / 1000) * 1000)) {
                    response = "skipping muthoot for application id : " + applicationId + " due to merchant loan amount: " + loanAmount + " is greater than tpvOffer: " + (Math.round(tpvOffer / 1000) * 1000);
                    success = false;
                    break;
                }
                if (edi > 0.9 * riskVariables.getSummaryTpv()) {
                    response = "skipping muthoot for application id : " + applicationId + " due to merchant loan edi amount: " + edi + " is greater than 0.9 * summary_tpv " + 0.9 * summaryTpv;
                    success = false;
                    break;
                }
                break;
            case PAYU :
                if (loanAmount > (Math.ceil(tpvOffer / 10000) * 10000)) {
                    response = "skipping payU for application id : " + applicationId + " due to merchant loan amount: " + loanAmount + " is greater than tpvOffer: " + (Math.ceil(tpvOffer / 10000) * 10000);
                    success = false;
                    break;
                }
                if (edi > riskVariables.getSummaryTpv()) {
                    response = "skipping payu for application id : " + applicationId + " due to merchant loan edi amount: " + edi + " is greater than summary_tpv " + summaryTpv;
                    success = false;
                    break;
                }
                break;
            case SMFG:
                if (edi > 0.7 * summaryTpv) {
                    response = "skipping smfg for application id : " + applicationId + " due to edi amount: " + edi + " is greater than 0.7 * summary_tpv " + 0.7 * summaryTpv;
                    success = false;
                    break;
                }
                if (REGULAR_ETC.name().equalsIgnoreCase(riskSegment) && riskVariables.isSTPFlag() && application.getLoanAmount() <= 200000) {
                    response = "skipping smfg for application id : " + applicationId + " due to risk segment: " + riskVariables.getRiskSegment() + ", loan amount: " + application.getLoanAmount() + " is smaller than 2,00,000";
                    success = false;
                    break;
                }
                break;
            case OXYZO:

                log.info("runLenderChecksForApplication for Oxyzo {} {} {}", riskVariables.getUnsecuredPos(), riskVariables.getMonthlyTpv(), loanAmount);

                if(edi > 0.7 * (riskVariables.getMonthlyTpv()/30)){
                    response = "skipping oxyzo for application id : " + applicationId + " due to edi amount: " + edi + " is greater than 0.7 * monthly_tpv " + 0.7 * (riskVariables.getMonthlyTpv()/30);
                    success = false;
                    break;
                }
                if(loanAmount > Math.ceil(tpvOffer)){
                    response = "skipping oxyzo for application id : " + applicationId + " due to merchant loan amount: " + loanAmount + " is greater than tpvOffer: " + (Math.ceil(tpvOffer));
                    success = false;
                    break;
                }
                if("R1".equalsIgnoreCase(riskVariables.getRiskGroup()) && ((loanAmount + riskVariables.getUnsecuredPos()) > (6 * riskVariables.getMonthlyTpv()))){
                    response = "skipping oxyzo for application id : " + applicationId + " due to merchant loan amount: " + loanAmount + "+ unsecuredPos : " + riskVariables.getUnsecuredPos() + " is greater than 6 * Monthly Adj TPV: " + 6 * riskVariables.getMonthlyTpv();
                    success = false;
                    log.info("inside unsecuredPOs check for R1");
                    break;
                }
                if("R2".equalsIgnoreCase(riskVariables.getRiskGroup()) && ((loanAmount + riskVariables.getUnsecuredPos()) > (4.5 * riskVariables.getMonthlyTpv()))){
                    response = "skipping oxyzo for application id : " + applicationId + " due to merchant loan amount: " + loanAmount + "+ unsecuredPos : " + riskVariables.getUnsecuredPos() + " is greater than 4.5 * Monthly Adj TPV: " + 4.5 * riskVariables.getMonthlyTpv();
                    success = false;
                    log.info("inside unsecuredPOs check for R2");
                    break;
                }
                break;
            default:
                log.warn("No specific checks found for lender : {} for application : {}", lender, applicationId);
        }

        return new ImmutablePair<>(success, response);
    }

}
