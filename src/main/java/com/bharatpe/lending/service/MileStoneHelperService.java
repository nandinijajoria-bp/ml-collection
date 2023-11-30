package com.bharatpe.lending.service;


import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.dao.LendingPincodesDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.entity.LendingPincodes;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.query.dao.MileStoneDaoSlave;
import com.bharatpe.lending.common.query.entity.MileStoneSlave;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.MileStoneDao;
import com.bharatpe.lending.dao.MileStoneRewardDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.MileStoneEntity;
import com.bharatpe.lending.entity.MileStoneReward;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.BureauResponseDTO;
import com.bharatpe.lending.loanV2.handlers.BureauHandler;
import com.bharatpe.lending.common.util.MapperUtil;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.LoanDashboardResponse;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MileStoneHelperService {
    @Autowired
    private MileStoneDaoSlave mileStoneDaoSlave;

    @Autowired
    BureauHandler bureauHandler;

    @Value("${bureau.milestone.score.pull.Days}")
    private Long bureauScorePullDays;

    @Autowired
    MileStoneDao mileStoneDao;

    @Autowired
    MapperUtil mapperUtil;

    @Autowired
    DsHandler dsHandler;

    @Autowired
    LoanUtil loanUtil;

    @Value("${milestone.deeplink}")
    private String deepLink;

    @Value("${milestone.active.buttonAction.deeplink}")
    private String activeButtonActionDeepLink;

    @Value("${milestone.eligible.buttonAction.deeplink}")
    private String eligibleButtonActionDeepLink;

    @Value("${milestone.splashScreen.visible}")
    private Boolean milestoneSplashVisible;

    @Value("${milestone.widget.visible}")
    private Boolean milestoneWidgetVisible;

    @Value("${rte.program.visible}")
    private Boolean showRTEProgram=true;

    @Value("${milestone.easyLoan.visible}")
    private Boolean milestoneEasyLoanVisible;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LendingPincodesDao lendingPincodesDao;

    @Value("${merchant.milestone.target.user}")
    Integer milestoneTargetUser;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    LoanDashboardService loanDashboardService;

    public BureauResponseDTO calculateBureauScore(String panNumber,BasicDetailsDto merchant) {
        return bureauHandler.getBureauData(panNumber, merchant.getId(), merchant.getMobile(),
                bureauScorePullDays,"RTE");
    }


    public MileStoneEntity createMileStoneSession(Long merchantId, DSMileStoneResponse response, int days) {
        MileStoneEntity entity = new MileStoneEntity();
        String sessionId = UUID.randomUUID().toString();
        entity.setSessionId(sessionId);
        entity.setMerchantId(merchantId);
        entity.setSessionStatus("IN_PROGRESS");
        entity.setResponse(mapperUtil.getJsonString(response));
        entity.setProgramDuration(response.getTarget_duration_days());
        entity.setCreatedAt(new Date());
        entity.setProgramStartDate(new Date());
        entity.setMilestoneOffer(false);
        entity.setKycStatus("APPROVED");
        entity.setGraphData(0D);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(entity.getCreatedAt());
        if (days == 28) {
            calendar.add(Calendar.DATE, 28);
        } else {
            calendar.add(Calendar.DATE, 56);
        }
        entity.setExpiryDate(calendar.getTime());
        mileStoneDao.save(entity);
        return entity;
    }

    public MileStoneEligibilityResponseDto.ProgramActiveData setProgramActiveData(Double graphData, String weekCount) {
        MileStoneEligibilityResponseDto.ProgramActiveData programActiveData = new MileStoneEligibilityResponseDto.ProgramActiveData();
        programActiveData.setStripHeading(weekCount);
        programActiveData.setMinorHeading("Get Set Loan in 30 days");
        programActiveData.setMajorHeading("Complete your weekly target!");
        programActiveData.setSubHeading("you are on the right track. Join the program to become eligible");
        programActiveData.setButtonText("Know More");
        programActiveData.setProgressText("ACHIEVED");
        programActiveData.setProgressPercentage(String.valueOf(graphData));
        programActiveData.setButtonActionDeeplink(activeButtonActionDeepLink);
        return programActiveData;

    }

    public MileStoneEligibilityResponseDto.ProgramEligibleData setProgramEligibleData() {
        MileStoneEligibilityResponseDto.ProgramEligibleData programEligibleData = new MileStoneEligibilityResponseDto.ProgramEligibleData();
        programEligibleData.setStripHeading("Join Program");
        programEligibleData.setHeading("Get Set Loan in 30 days!");
        programEligibleData.setSubHeading("Complete weekly targets to become eligible for loan");
        programEligibleData.setButtonText("START NOW");
        programEligibleData.setBannerImage("https://d30gqtvesfc1d5.cloudfront.net/hubble/r2e/home-joinprogram-lottie-1696315201206.json");
        programEligibleData.setButtonActionDeeplink(eligibleButtonActionDeepLink);
        return programEligibleData;
    }

 /*   public MileStoneEligibilityResponseDto fetchTargetMileStone(BasicDetailsDto merchant, BureauResponseDTO bureauResponseDTO, String pinCodeColor) {
        MileStoneEligibilityResponseDto milestoneEligibleResp = new MileStoneEligibilityResponseDto();

        DSMileStoneResponse fetchMileStoneData = dsHandler.fetchMileStoneData(merchant.getId(), bureauResponseDTO.getVariables().getBureauScore(),
                bureauResponseDTO.getVariables().getBbs(), pinCodeColor);

        if (fetchMileStoneData.getTarget().isEmpty()) {
            milestoneEligibleResp.setMilStoneEligibility(false);
            milestoneEligibleResp.setEnrollState(false);
            return milestoneEligibleResp;
        }
        return milestoneEligibleResp;
    }*/

    public MileStoneEligibilityResponseDto calculateEligibility(BasicDetailsDto merchant) {


        MileStoneEligibilityResponseDto responseDto = new MileStoneEligibilityResponseDto();

        responseDto.setShowHomeWidgets(milestoneWidgetVisible);
        responseDto.setShowSplashBanner(milestoneSplashVisible);
        responseDto.setShowRTELoansFlow(milestoneEasyLoanVisible);


        List<Long> rteEligibleMerchants = loanUtil.rteEligibleMerchant();
        if (!easyLoanUtil.percentScaleUp(merchant.getId(), milestoneTargetUser)
                && !rteEligibleMerchants.contains(merchant.getId())) {
            log.info("ineligible due to not present in percent scale up or not in list for merchant id {}",merchant.getId());
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
            return responseDto;
        }

        MileStoneEntity entity = mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        log.info("entity for milestone journey {} for merchantId {}", entity, merchant.getId());


        if (!showRTEProgram && !"IN_PROGRESS".equalsIgnoreCase(entity.getSessionStatus())) {
            log.info("RTE program {} is disable for merchant {}", showRTEProgram, merchant.getId());
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
            return responseDto;
        }


        if (rteEligibleMerchants.contains(merchant.getId())) {
            log.info("setting up date");
            merchant.setCreatedAt(new Date());
            merchant.setCreated_at(new Date());
            log.info("merchant id {}, createdAt {}", merchant.getId(), merchant.getCreatedAt());
        }


        String mileStoneOfferCacheKey = LoanDetailsConstant.RTE_MILESTONE_OFFER_KEY + merchant.getId();
        Object mileStoneOfferResponse = lendingCache.get(mileStoneOfferCacheKey);

        if (!ObjectUtils.isEmpty(mileStoneOfferResponse)) {
            try {
                log.info("returning milestone offer response from cache for {}", merchant.getId());
                responseDto = objectMapper.readValue((String) mileStoneOfferResponse, MileStoneEligibilityResponseDto.class);
                return responseDto;
            } catch (Exception e) {
                log.info("exception while fetching response is: {}", e.getMessage());
            }
        }


        if (!ObjectUtils.isEmpty(entity) && Boolean.TRUE.equals(entity.getMilestoneOffer())) {
            log.info("mileStoneOffer flag {}", entity.getMilestoneOffer());
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
            try {
                AddCacheDto addCacheDto = new AddCacheDto();
                addCacheDto.setKey(LoanDetailsConstant.RTE_MILESTONE_OFFER_KEY + merchant.getId());
                addCacheDto.setValue(objectMapper.writeValueAsString(responseDto));
                addCacheDto.setTtl(24);
                lendingCache.add(addCacheDto, TimeUnit.HOURS);
            } catch (Exception e) {
                log.error("exception occurred while caching RTE details {} !!", LoanDetailsConstant.RTE_MILESTONE_OFFER_KEY + merchant.getId());
            }
            return responseDto;
        }


        String mileStoneCacheKey = LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX + merchant.getId();
        Object mileStoneCacheResponse = lendingCache.get(mileStoneCacheKey);

        LoanDashboardResponse loanDashboardResponse = null;
        if (!ObjectUtils.isEmpty(mileStoneCacheResponse)) {
            try {
                log.info("returning loan details response from cache for {}", merchant.getId());
                loanDashboardResponse = objectMapper.readValue((String) mileStoneCacheResponse, LoanDashboardResponse.class);
                if (!ObjectUtils.isEmpty(loanDashboardResponse.getRouteToEligibilityData())) {
                    return loanDashboardResponse.getRouteToEligibilityData();
                }
            } catch (Exception e) {
                log.info("exception while fetching response is: {}", e.getMessage());
            }
        }

        boolean lessThan30 = false;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(merchant.getCreatedAt());
        calendar.add(Calendar.MONTH, 1);
        if (new Date().compareTo(calendar.getTime()) <= 0) {
            lessThan30 = true;
        }

        log.info("less Than 30 is {}",lessThan30);
        if (!lessThan30)
        {
            log.info("lesstThan 30 flag is {}",lessThan30);
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
            return responseDto;
        }

        String pinCodeColor = null;
        Experian experian = experianDao.getByMerchantId(merchant.getId());
        if (lessThan30 && ObjectUtils.isEmpty(experian)) {
            log.info("experian is {} for merchant id {} ",experian,merchant.getId());
            responseDto.setEnrollState(false);
            responseDto.setMilStoneEligibility(true);
            responseDto.setGraphData(null);
            responseDto.setWeekCount(null);
            responseDto.setProgramEligibleData(setProgramEligibleData());
            responseDto.setProgramActiveData(setProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
            responseDto.setIsMileStoneExpiry(false);
            responseDto.setDeepLinkUrl(deepLink);
            responseDto.setPinCode(0);
            responseDto.setPanCard(null);
            return responseDto;
        }

        if (lessThan30 && !ObjectUtils.isEmpty(experian)) {
            if (experian.getPincode() != null) {
                responseDto.setPinCode(experian.getPincode());
                LendingPincodes lendingPincodes = lendingPincodesDao.findByPincode(experian.getPincode());
                if (!ObjectUtils.isEmpty(lendingPincodes)) {
                    log.info("pincode entity is {}", lendingPincodes);
                    pinCodeColor = lendingPincodes.getColor().getValue();
                }
            }

           if(ObjectUtils.isEmpty(experian.getPincode())
                   && !ObjectUtils.isEmpty(experian.getLatitude()) &&
                   !ObjectUtils.isEmpty(experian.getLongitude())) {
                DEPinCode pinCodeResponse = dsHandler.getInferredPinCode(merchant.getId(), experian.getLatitude(), experian.getLongitude());
                if ((lessThan30 && (pinCodeResponse.getPincode() == -1 || ObjectUtils.isEmpty(pinCodeResponse))))

                {
                    log.info("pincode is -1 from de response for merchant {}",merchant.getId(),merchant.getId());
                    responseDto.setEnrollState(false);
                    responseDto.setMilStoneEligibility(true);
                    responseDto.setGraphData(null);
                    responseDto.setWeekCount(null);
                    responseDto.setProgramEligibleData(setProgramEligibleData());
                    responseDto.setProgramActiveData(setProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
                    responseDto.setIsMileStoneExpiry(false);
                    responseDto.setDeepLinkUrl(deepLink);
                    responseDto.setPinCode(0);
                    responseDto.setPanCard(null);
                    return responseDto;
                }
                if (lessThan30 && !ObjectUtils.isEmpty(pinCodeResponse) && pinCodeResponse.getPincode()!=-1) {
                    responseDto.setPinCode(pinCodeResponse.getPincode());
                    LendingPincodes lendingPincodes = lendingPincodesDao.findByPincode(pinCodeResponse.getPincode());
                    log.info("pincode entity is {} ", lendingPincodes);
                    if (!ObjectUtils.isEmpty(lendingPincodes)) {
                        log.info("pincode entity is {}", lendingPincodes);
                        pinCodeColor = lendingPincodes.getColor().getValue();
                    }
                }
            }
        }


        if (ObjectUtils.isEmpty(pinCodeColor) || Objects.isNull(pinCodeColor)) {
            log.info("pincode color is empty or null");
            responseDto.setEnrollState(false);
            responseDto.setMilStoneEligibility(false);
            return responseDto;
        }

        String kycPancard = kycHandler.getPanNumber(merchant.getId());
        if (ObjectUtils.isEmpty(kycPancard))
        {
            log.info("panCard is empty for merchant",merchant.getId());
            responseDto.setEnrollState(false);
            responseDto.setMilStoneEligibility(true);
            responseDto.setGraphData(null);
            responseDto.setWeekCount(null);
            responseDto.setProgramEligibleData(setProgramEligibleData());
            responseDto.setProgramActiveData(setProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
            responseDto.setIsMileStoneExpiry(false);
            responseDto.setDeepLinkUrl(deepLink);
            responseDto.setPanCard(null);
            responseDto.setPinCode(experian.getPincode());
            return responseDto;
        }
        BureauResponseDTO bureauResponseDTO = calculateBureauScore(kycPancard,merchant);

        if ((ObjectUtils.isEmpty(bureauResponseDTO)
                || ObjectUtils.isEmpty(bureauResponseDTO.getVariables()))
                && bureauResponseDTO.getIsNTC() != Boolean.TRUE) {
            log.info("bureau response {} for merchant id {}", bureauResponseDTO, merchant.getId());
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
            return responseDto;
        }

        log.info("bureauResponse {} for merchant {}", bureauResponseDTO, merchant.getId());

        MileStoneEntity inProgressEntity = mileStoneDao.findByMerchantIdAndSessionStatus(merchant.getId(), "IN_PROGRESS");

        if (!ObjectUtils.isEmpty(bureauResponseDTO)) {
                if (bureauResponseDTO.getIsNTC() == Boolean.TRUE)
                {
                    DSMileStoneResponse fetchMileStoneData;
                    BureauResponseDTO.BureauVariables variables = new BureauResponseDTO.BureauVariables();
                    variables.setBbs(0D);
                    variables.setBureauScore(0D);
                    fetchMileStoneData = dsHandler.fetchMileStoneData(merchant.getId(), variables.getBureauScore(), variables.getBbs(), pinCodeColor);
                    log.info("milestone data {} for merchant id {}", fetchMileStoneData, merchant.getId());

                    if (fetchMileStoneData == null) {
                        String loanDetailsCacheKey = LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX + merchant.getId();
                        Object loanDetailsCacheResponse = lendingCache.get(loanDetailsCacheKey);
                        if (!ObjectUtils.isEmpty(loanDetailsCacheResponse)) {
                            lendingCache.delete(loanDetailsCacheKey);
                        }

                        if (ObjectUtils.isEmpty(inProgressEntity)) {
                            responseDto.setMilStoneEligibility(false);
                            responseDto.setEnrollState(false);
                            return responseDto;
                        }
                        else{
                            log.info("milestone data is null for merchant id {}", merchant.getId());
                            responseDto.setMilStoneEligibility(true);
                            responseDto.setEnrollState(true);
                            responseDto.setDsErrorMessage("error in target ds api");
                            responseDto.setProgramEligibleData(setProgramEligibleData());
                            responseDto.setGraphData(null);
                            responseDto.setWeekCount(null);
                            responseDto.setPinCode(experian.getPincode());
                            responseDto.setPanCard(kycPancard);
                            responseDto.setProgramActiveData(setProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
                            responseDto.setDeepLinkUrl(deepLink);
                            return responseDto;
                        }

                    }

                    log.info("milestone response for new user merchant id {}",merchant.getId());
                    if (fetchMileStoneData.getTarget().isEmpty()) {
                        log.info("targets are null for merchantId {}",merchant.getId());
                        responseDto.setMilStoneEligibility(false);
                        responseDto.setEnrollState(false);
                        return responseDto;
                    }
                }
                if (!ObjectUtils.isEmpty(bureauResponseDTO.getVariables()) &&
                        (bureauResponseDTO.getVariables().getBureauScore() != null
                                && bureauResponseDTO.getVariables().getBbs() != null)) {

                    DSMileStoneResponse fetchMileStoneData = dsHandler.fetchMileStoneData(merchant.getId(), bureauResponseDTO.getVariables().getBureauScore(),
                            bureauResponseDTO.getVariables().getBbs(), pinCodeColor);

                    log.info("milestone data {} for merchant id {}",fetchMileStoneData,merchant.getId());

                    if (fetchMileStoneData == null) {
                        String loanDetailsCacheKey = LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX + merchant.getId();
                        Object loanDetailsCacheResponse = lendingCache.get(loanDetailsCacheKey);
                        if (!ObjectUtils.isEmpty(loanDetailsCacheResponse)) {
                            lendingCache.delete(loanDetailsCacheKey);
                        }
                        if (ObjectUtils.isEmpty(inProgressEntity)) {
                            responseDto.setMilStoneEligibility(false);
                            responseDto.setEnrollState(false);
                            return responseDto;

                        } else {
                            log.info("milestone data is null for merchant id {}", merchant.getId());
                            responseDto.setMilStoneEligibility(true);
                            responseDto.setEnrollState(true);
                            responseDto.setDsErrorMessage("error in target ds api");
                            responseDto.setProgramEligibleData(setProgramEligibleData());
                            responseDto.setGraphData(null);
                            responseDto.setWeekCount(null);
                            responseDto.setPinCode(experian.getPincode());
                            responseDto.setPanCard(kycPancard);
                            responseDto.setProgramActiveData(setProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
                            responseDto.setDeepLinkUrl(deepLink);
                            return responseDto;
                        }

                    }

                    log.info("milestone response for old user merchant id {}",merchant.getId());

                    if (fetchMileStoneData.getTarget().isEmpty()) {
                        log.info("targets are null for merchantId {}",merchant.getId());
                        responseDto.setMilStoneEligibility(false);
                        responseDto.setEnrollState(false);
                        return responseDto;
                    }
                }
            }


        List inclusionReasonMilestoneList = Arrays.asList
                ("Something went wrong - merchant_summary",
                        "LIMIT BLOCKED: Offer set 0",
                        "LIMIT BLOCKED: Less than 10K offer",
                        "Risk Segment Exclusion: RegularNTCReject:R2/R3&DRS<15&Cust<250",
                        "Something went wrong - DS pan pin",
                        "Something went wrong - Bureau",
                        "LIMIT BLOCKED: Pending application",
                        "Something went wrong - Experian",
                        "NTC",
                        "Risk Segment Exclusion: NTB vintage less than 30",
                        " Thin File ETC");


        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchant.getId());
        log.info("lending risk variable {} for merchantId {}", lendingRiskVariables, merchant.getId());


        if (lessThan30 &&
                ((bureauResponseDTO.getIsNTC() == Boolean.TRUE)
                        || (bureauResponseDTO.getIsNTC() == Boolean.FALSE))
                && (Objects.isNull(lendingRiskVariables.getExperianRejection())
                       || lendingRiskVariables.getExperianRejection().isEmpty()
                || inclusionReasonMilestoneList.contains(lendingRiskVariables.getExperianRejection()))) {
                responseDto.setMilStoneEligibility(true);
                log.info("entity for milestone {}", entity);
                if ((ObjectUtils.isEmpty(entity)) ||
                        ("COMPLETED".equalsIgnoreCase(entity.getSessionStatus()))) {
                    responseDto.setEnrollState(false);
                    responseDto.setGraphData(null);
                    responseDto.setWeekCount(null);
                    responseDto.setPinCode(experian.getPincode());
                    responseDto.setPanCard(kycPancard);
                    responseDto.setProgramEligibleData(setProgramEligibleData());
                    responseDto.setProgramActiveData(setProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));

                    if(!ObjectUtils.isEmpty(entity) && "COMPLETED".equalsIgnoreCase(entity.getSessionStatus()))
                    {
                        responseDto.setIsMileStoneExpiry(true);
                        responseDto.setGraphData(entity.getGraphData());
                    }
                    else {
                        responseDto.setIsMileStoneExpiry(false);
                    }


                }
                else {
                    DSMileStoneResponse response = fetchTarget(entity);
                    log.info("fetch Target data {} for merchant {}", response, merchant.getId());

                    DSMileStoneAchievementResponse achievementResponse;

                    achievementResponse = dsHandler.fetchMilestoneAchievements(entity.getMerchantId(), entity.getSessionId());
                    log.info("achievementResponse is {}", achievementResponse);

                    if (achievementResponse == null && (!ObjectUtils.isEmpty(loanDashboardResponse))) {
                        try {
                            log.info("returning loan details response from cache for {}", merchant.getId());
                            loanDashboardResponse = objectMapper.readValue((String) mileStoneCacheResponse, LoanDashboardResponse.class);
                            return loanDashboardResponse.getRouteToEligibilityData();
                        } catch (Exception e) {
                            log.info("exception while fetching response is: {}", e.getMessage());
                        }
                    }

                    if (achievementResponse == null && (ObjectUtils.isEmpty(loanDashboardResponse))) {
                        log.info("achievement response is null for merchant id {}",merchant.getId());
                        responseDto.setDsErrorMessage("achievement response is null");
                        responseDto.setMilStoneEligibility(true);
                        responseDto.setEnrollState(true);
                        responseDto.setProgramEligibleData(setProgramEligibleData());
                        responseDto.setGraphData(null);
                        responseDto.setWeekCount(null);
                        responseDto.setPinCode(experian.getPincode());
                        responseDto.setPanCard(kycPancard);
                        responseDto.setProgramActiveData(setProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
                        responseDto.setDeepLinkUrl(deepLink);
                        return responseDto;
                    }

                    int value = 100 / response.getTarget().size();
                    double graph = 0d;
                    String daysCount = null;
                    HashMap<Integer, Target> targetMileStoneNoMap = new HashMap<>();

                    for (Target target : response.target)
                        targetMileStoneNoMap.put(target.getMilestone_no(), target);

                    int days = response.getTarget_duration_days();

                    if (!ObjectUtils.isEmpty(achievementResponse)) {
                        for (DSMileStoneAchievementResponse.Achievement response1 : achievementResponse.achievement) {
                            Date date = new Date();
                            if (days == 56) {
                                if ((date.after(response1.getMilestone_start_time())) && date.before(response1.getMilestone_end_time())) {
                                    daysCount = "Milestone" + " " + response1.getMilestone_no();
                                }
                            }
                            if (days == 28) {
                                if ((date.after(response1.getMilestone_start_time()))
                                        && date.before(response1.getMilestone_end_time())) {
                                    daysCount = "Week" + " " + response1.getMilestone_no();
                                }

                            } else {
                                daysCount = "COMPLETED";
                            }

                            Target target = targetMileStoneNoMap.get(response1.getMilestone_no());
                            double activeDays = ((double) value / 2) * ((double) response1.getActive_days() / target.getActive_days());
                            double uniquePayers = ((double) value / 2) * ((double) response1.getUnq_payer() / target.getUnq_payer());

                            if (activeDays > (double) value / 2) {
                                activeDays = (double) value / 2;
                            }

                            if (uniquePayers > (double) value / 2) {
                                uniquePayers = (double) value / 2;
                            }

                            graph = graph + ((activeDays + uniquePayers) / 100);
                            log.info("graph data {} for milestone:{}", graph, target.getMilestone_no());
                        }
                    }
                    responseDto.setEnrollState(true);

                    if (daysCount == null) {
                        daysCount = days == 28 ? "Week 1" : "Milestone 1";
                    }

                    boolean isMileStoneExpiry = false;
                    if (!ObjectUtils.isEmpty(entity)) {
                        Date date = new Date();
                        isMileStoneExpiry = entity.getExpiryDate().getTime() < date.getTime();
                        if (isMileStoneExpiry == Boolean.TRUE)
                        {
                            String loanDetailsCacheKey = LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX + merchant.getId();
                            Object loanDetailsCacheResponse = lendingCache.get(loanDetailsCacheKey);
                            if (!ObjectUtils.isEmpty(loanDetailsCacheResponse)) {
                                lendingCache.delete(loanDetailsCacheKey);
                            }
                        }
                        log.info("milestone Expiry is {}", isMileStoneExpiry);
                        if (isMileStoneExpiry && "IN_PROGRESS".equalsIgnoreCase(entity.getSessionStatus())) {
                            entity.setSessionStatus("COMPLETED");
                            entity.setGraphData(graph);
                            mileStoneDao.save(entity);
                            responseDto.setIsMileStoneExpiry(true);
                            responseDto.setEnrollState(false);
                            responseDto.setMilStoneEligibility(lessThan30);
                            responseDto.setProgramEligibleData(setProgramEligibleData());
                            responseDto.setGraphData(graph);
                            responseDto.setWeekCount(daysCount);
                            responseDto.setPinCode(experian.getPincode());
                            responseDto.setPanCard(kycPancard);
                            responseDto.setProgramActiveData(setProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
                            responseDto.setDeepLinkUrl(deepLink);
                            return responseDto;
                        }
                    }

                    responseDto.setIsMileStoneExpiry(isMileStoneExpiry);
                    responseDto.setWeekCount(daysCount);
                    // To replace this with graph data
                    responseDto.setGraphData(graph);
                    responseDto.setProgramEligibleData(setProgramEligibleData());
                    responseDto.setProgramActiveData(setProgramActiveData(responseDto.getGraphData(), daysCount));
                    responseDto.setDeepLinkUrl(deepLink);

                }
                return responseDto;

            }
        responseDto.setMilStoneEligibility(false);
        responseDto.setEnrollState(false);

        return responseDto;
    }


    public DSMileStoneAchievementResponse getAchievementData(DsHandler dsHandler, MileStoneEntity entity) {
        return dsHandler.fetchMilestoneAchievements(entity.getMerchantId(), entity.getSessionId());
    }
    public DSMileStoneResponse fetchTarget(MileStoneEntity entity) {
        return mapperUtil.objectMapper.convertValue(mapperUtil.getObjectFromJsonString(entity.getResponse()), DSMileStoneResponse.class);

    }

    public boolean updateEntity(MileStoneOfferRequest request, MileStoneEntity entity) {
        if (Boolean.TRUE.equals(request.getIsOfferAchieved())) {
            entity.setMilestoneOffer(true);
            entity.setSessionStatus("CLOSED");
            mileStoneDao.save(entity);
            log.info("Updated the entity {}", entity);
            return true;
        }
        return false;
    }

    public Boolean rewardClaim(Long merchantId, MileStoneSlave entity, String rewardName, Boolean rewardStatus, MileStoneRewardDao mileStoneRewardDao) {
        if (!ObjectUtils.isEmpty(entity)) {
            if (merchantId.equals(entity.getMerchantId())) {
                MileStoneReward reward = new MileStoneReward();
                reward.setRewardName(rewardName);
                reward.setRewardClaimedStatus(rewardStatus);
                reward.setClaimDate(new Date());
                reward.setSessionId(entity.getSessionId());
                reward.setMerchantId(merchantId);
                mileStoneRewardDao.save(reward);
            }
            return true;
        }
        return false;
    }

}

