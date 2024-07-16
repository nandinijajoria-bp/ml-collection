package com.bharatpe.lending.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.dao.LendingPincodesDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.entity.LendingPincodes;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.MileStoneDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.MileStoneEntity;
import com.bharatpe.lending.enums.CleverTapEvents;
import com.bharatpe.lending.enums.RTEProgramType;
import com.bharatpe.lending.enums.RTESessionStatus;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.BureauResponseDTO;
import com.bharatpe.lending.loanV3.revamp.constants.RTEConstants;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class MileStoneHelperServicev3 {

    @Autowired
    MileStoneDao mileStoneDao;

    @Autowired
    DsHandler dsHandler;

    @Autowired
    LoanUtil loanUtil;

    @Value("${milestone.deeplink}")
    private String deepLink;

    @Value("${milestone.splashScreen.visible}")
    private Boolean milestoneSplashVisible;
    @Value("${milestone.widget.visible}")
    private Boolean milestoneWidgetVisible;
    @Value("${milestone.easyLoan.visible}")
    private Boolean milestoneEasyLoanVisible;

    @Value("${rte.program.visible}")
    private Boolean showRTEProgram = true;
    @Autowired
    LendingCache lendingCache;

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LendingPincodesDao lendingPincodesDao;
    @Autowired
    KycHandler kycHandler;

    @Autowired
    MileStoneHelperService mileStoneHelperService;

    @Autowired
    FunnelService funnelService;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    CleverTapEventService cleverTapEventService;

    @Value("${enable.rte.v3:true}")
    boolean isRtev3Enabled;

    @Value("${rte.v3.rollout.percent:10}")
    int rtev3RolloutPercent;

    @Value("${milestone.inclusion.reasons}")
    private String inclusionReasons;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    public MileStoneEligibilityResponseDto calculateEligibility(BasicDetailsDto merchant, Boolean loanAmountPresent) {
        MileStoneEligibilityResponseDto responseDto = new MileStoneEligibilityResponseDto();
        responseDto.setShowHomeWidgets(milestoneWidgetVisible);
        responseDto.setShowSplashBanner(milestoneSplashVisible);
        responseDto.setShowRTELoansFlow(milestoneEasyLoanVisible);

        try {
            List<Long> rteEligibleMerchants = loanUtil.rteEligibleMerchant();

            List<MileStoneEntity> entityList = mileStoneDao.findByMerchantIdAndSessionStatus(merchant.getId(), RTESessionStatus.COMPLETED.name());
            log.info("entityList is {}", entityList.size());

            MileStoneEntity entity = mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
            log.info("entity for milestone journey {} for merchantId {}", entity, merchant.getId());

            if (!showRTEProgram && !RTESessionStatus.IN_PROGRESS.name().equalsIgnoreCase(entity.getSessionStatus())) {
                log.info("RTE program {} is disable for merchant {}", showRTEProgram, merchant.getId());
                return inEligibleForRTEResponse(responseDto);
            }

            if (rteEligibleMerchants.contains(merchant.getId())) {
                log.info("setting up date");
                merchant.setCreatedAt(new Date());
                merchant.setCreated_at(new Date());
                log.info("merchantId:{}, createdAt {}", merchant.getId(), merchant.getCreatedAt());
            }

            String mileStoneOfferCacheKey = RTEConstants.RTE_MILESTONE_OFFER_KEY + merchant.getId();
            String mileStoneCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();

            if(loanAmountPresent) {
                log.info("deleting keys: {} {}", mileStoneCacheKey, mileStoneOfferCacheKey);
                lendingCache.delete(mileStoneCacheKey);
                lendingCache.delete(mileStoneOfferCacheKey);
            }else{
                //loanAmount not provided, then check offer key data in cache
                MileStoneEligibilityResponseDto mileStoneOfferResponse = getCachedMileStoneOfferResponse(mileStoneOfferCacheKey);

                if (!ObjectUtils.isEmpty(mileStoneOfferResponse)) {
                    log.info("Returning milestone offer response from cache for {}", merchant.getId());
                    return mileStoneOfferResponse;
                }
                if (ObjectUtils.isEmpty(mileStoneOfferResponse)
                        && !ObjectUtils.isEmpty(entity)
                        && Boolean.TRUE.equals(entity.getMilestoneOffer())) {
                    return cacheMileStoneOfferData(merchant, entity, responseDto);
                }

                Object mileStoneCacheResponse = lendingCache.get(mileStoneCacheKey);
                if (!ObjectUtils.isEmpty(mileStoneCacheResponse)) {
                    return rteDetailsCacheValue(responseDto, merchant, mileStoneCacheResponse);
                }
            }
            responseDto = panExperianAndBureauCallHandler(merchant, entity, entityList, responseDto);

            if(!ObjectUtils.isEmpty(responseDto.getProgramType())) {
                String program_type = RTEProgramType.SLIDER.name().equals(responseDto.getProgramType()) ? "v3" : "v2";
                HashMap<String, String> cleverTapEvtData = new HashMap<String, String>() {{
                    put("program_type", program_type);
                }};

                if(responseDto.getMilStoneEligibility()
                        && (ObjectUtils.isEmpty(entity)
                        || !ObjectUtils.isEmpty(entity) && !RTESessionStatus.IN_PROGRESS.name().equals(entity.getSessionStatus()))) {
                    executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.RTE_V3_ELIGIBLE.name(), cleverTapEvtData, merchant.getMid()));
                    funnelService.submitEvent(merchant.getId(), null, null,
                            FunnelEnums.StageId.RTE, FunnelEnums.StageEvent.ELIGIBLE, responseDto.getProgramType());
                }

                if(!responseDto.getMilStoneEligibility()) {
                    String experianRejectionReason;
                    LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchant.getId());
                    if(ObjectUtils.isEmpty(lendingRiskVariables)) {
                        experianRejectionReason = "LRV not found";
                    }else{
                        experianRejectionReason = ObjectUtils.isEmpty(responseDto.getExperianRejectionReason()) ? "Experian Rejection reason not found" : responseDto.getExperianRejectionReason();
                    }
                    executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.RTE_V3_INELIGIBLE.name(), cleverTapEvtData, merchant.getMid()));
                    funnelService.submitEvent(merchant.getId(), null, null,
                            FunnelEnums.StageId.RTE, FunnelEnums.StageEvent.INELIGIBLE, responseDto.getProgramType() + "_" + experianRejectionReason);
                }
            }
        }
        catch (Exception e) {
            log.error("exception in calculate Eligibility flow for merchantId:{} and exception is {} ",merchant.getId(),Arrays.asList(e.getStackTrace()));
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
            responseDto.setShowHomeWidgets(milestoneWidgetVisible);
            responseDto.setShowSplashBanner(milestoneSplashVisible);
            responseDto.setShowRTELoansFlow(milestoneEasyLoanVisible);
        }
        return responseDto;

    }

    private MileStoneEligibilityResponseDto rteDetailsCacheValue(MileStoneEligibilityResponseDto responseDto, BasicDetailsDto merchant, Object mileStoneCacheResponse) {
        try {
            log.info("returning rte details response from cache for {}", merchant.getId());
            RTEProgramDetailsDto rteProgramDetailsDto = objectMapper.readValue((String) mileStoneCacheResponse, RTEProgramDetailsDto.class);
            if (!ObjectUtils.isEmpty(rteProgramDetailsDto.getRouteToEligibilityData())) {
                return rteProgramDetailsDto.getRouteToEligibilityData();
            }
        } catch (Exception e) {
            log.info("exception while fetching cache rte program details response is: {}", Arrays.asList(e.getStackTrace()));
        }
        return responseDto;
    }

    private MileStoneEligibilityResponseDto panExperianAndBureauCallHandler(BasicDetailsDto merchant, MileStoneEntity entity, List<MileStoneEntity> entityList, MileStoneEligibilityResponseDto responseDto) {
        try {
            String pinCodeColor = null;
            int pincode = 0;

            String kycPancard;
            Experian experian = experianDao.getByMerchantId(merchant.getId());
            log.info("Experian data for merchantId:{} is {} ",merchant.getId(),experian);

            kycPancard = kycHandler.getPanNumber(merchant.getId());
            log.info("kycPancard data for merchantId:{} is {} ",merchant.getId(),kycPancard);

            BureauResponseDTO bureauResponseDTO = null;
            String sessionStatus = ObjectUtils.isEmpty(entity) ? null : entity.getSessionStatus();
            if (ObjectUtils.isEmpty(entity) || !(RTESessionStatus.IN_PROGRESS.name().equalsIgnoreCase(sessionStatus))) {
                //fresh merchant
                if (ObjectUtils.isEmpty(kycPancard)) {
                    return panCardNotFound(merchant, pincode, responseDto);
                }

                if (ObjectUtils.isEmpty(experian)) {
                    return experianNotFound(merchant,experian, kycPancard, pincode, responseDto);
                }

                pinCodeColor = processExperianData(merchant, experian, responseDto,kycPancard, pincode, entityList);
                if(!ObjectUtils.isEmpty(responseDto.getMilStoneEligibility()) || pinCodeColor == null) {
                    return responseDto;
                }

                bureauResponseDTO = mileStoneHelperService.calculateBureauScore(kycPancard, merchant);
                log.info("bureauResponse {} for merchantId {}", bureauResponseDTO, merchant.getId());
                bureauResponsechecks(merchant, bureauResponseDTO, responseDto, experian, pinCodeColor, kycPancard);
                if(!ObjectUtils.isEmpty(responseDto.getMilStoneEligibility()) && !responseDto.getMilStoneEligibility()) {
                    return responseDto;
                }
            }


            if (!RTESessionStatus.IN_PROGRESS.name().equalsIgnoreCase(sessionStatus) && !ObjectUtils.isEmpty(bureauResponseDTO)) {
                callDSMileStoneApi(merchant, bureauResponseDTO, responseDto, experian,  pinCodeColor, kycPancard);
                if(!responseDto.getMilStoneEligibility()) {
                    return responseDto;
                }
            }

            //lrv checks for both fresh(session is not in progress) & existing(session is in progress) merchants --
            responseDto = mileStoneLRVHandler(merchant, entity, responseDto, bureauResponseDTO, experian, kycPancard);
        }catch (Exception e) {
            log.error("Exception inside panExperianAndBureauCallHandler for merchantId: {} {}", merchant.getId(), Arrays.asList(e.getStackTrace()));
        }
        return responseDto;
    }

    private MileStoneEligibilityResponseDto mileStoneLRVHandler(BasicDetailsDto merchant, MileStoneEntity entity, MileStoneEligibilityResponseDto responseDto, BureauResponseDTO bureauResponseDTO, Experian experian, String kycPancard) {
        log.info("LRV checks handler for merchantId: {}", merchant.getId());
        try {
            boolean isEligibleForMilestone = isMerchantEligibleForMilestone(merchant, entity, responseDto);

            if (isEligibleForMilestone) {
                responseDto.setMilStoneEligibility(true);

                if ((ObjectUtils.isEmpty(entity)) || !ObjectUtils.isEmpty(entity) && RTESessionStatus.COMPLETED.name().equalsIgnoreCase(entity.getSessionStatus())) {
                    //fresh user or re-enrolling
                    log.info("entity for milestone {}", entity);
                    return updateResponse(merchant, responseDto, bureauResponseDTO, experian, entity, kycPancard);
                }
                else {
                    // Old Session Flow->>>> expiry management
                    DSMileStoneResponse mileStoneResponse = mileStoneHelperService.fetchTarget(entity);
                    log.info("fetch Target data {} for merchant {}", mileStoneResponse, merchant.getId());

                    DSMileStoneAchievementResponse achievementResponse = dsHandler.fetchMilestoneAchievements(entity.getMerchantId(), entity.getSessionId());
                    log.info("achievementResponse is {}", achievementResponse);

                    String mileStoneCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();
                    Object mileStoneCacheResponse = lendingCache.get(mileStoneCacheKey);
                    RTEProgramDetailsDto rteProgramDetailsDto = null;
                    if (achievementResponse == null && (!ObjectUtils.isEmpty(mileStoneCacheResponse))) {
                        try {
                            log.info("returning response from cache for when achievements are null for merchantId:{}", merchant.getId());
                            rteProgramDetailsDto = objectMapper.readValue((String) mileStoneCacheResponse, RTEProgramDetailsDto.class);
                            return rteProgramDetailsDto.getRouteToEligibilityData();
                        } catch (Exception e) {
                            log.info("exception while fetching response is: {}", e.getMessage());
                        }
                    }

                    if (achievementResponse == null && (ObjectUtils.isEmpty(rteProgramDetailsDto))) {
                        return manageExpiryForExistingMerchant(merchant, bureauResponseDTO, mileStoneResponse, responseDto, experian, kycPancard);
                    }
                    return setGraphData(merchant, mileStoneResponse, achievementResponse, responseDto, bureauResponseDTO, entity, mileStoneCacheKey, mileStoneCacheResponse, experian, kycPancard);
                }
            }

            log.info("eligibility checks not satisfied for merchantId: {}",merchant.getId());
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
        }catch (Exception e) {
            log.error("Exception while handling lrv mileStone for merchantId: {} {}", merchant.getId(), Arrays.asList(e.getStackTrace()));
        }
        return responseDto;
    }

    private boolean isMerchantEligibleForMilestone(BasicDetailsDto merchant, MileStoneEntity entity, MileStoneEligibilityResponseDto responseDto) {
        log.info("Checks for merchant eligibility for milestone with merchantId: {}",merchant.getId());
        if (isActiveSliderSession(entity)) {
            log.info("Session in progress with SLIDER program of merchantId: {}", merchant.getId());
            return true;
        }

        if (isFreshOrReenrollingSliderMerchant(entity, responseDto)) {
            log.info("Skipping LRV checks for slider program of merchantId: {}", merchant.getId());
            return true;
        }
        return isNewMerchantEligibleForMilestone(merchant, entity, responseDto);
    }

    private boolean isActiveSliderSession(MileStoneEntity entity) {
        if (!ObjectUtils.isEmpty(entity) && RTESessionStatus.IN_PROGRESS.name().equals(entity.getSessionStatus())) {
            DSMileStoneResponse dsMileStoneResponse = mileStoneHelperService.fetchTarget(entity);
            return (!ObjectUtils.isEmpty(dsMileStoneResponse) && RTEProgramType.SLIDER.name().equals(dsMileStoneResponse.getProgram_type()));
        }
        return false;
    }

    private boolean isFreshOrReenrollingSliderMerchant(MileStoneEntity entity, MileStoneEligibilityResponseDto responseDto) {
        if (!ObjectUtils.isEmpty(responseDto.getProgramType()) && RTEProgramType.SLIDER.name().equals(responseDto.getProgramType())) {
            return (ObjectUtils.isEmpty(entity) || !RTESessionStatus.CLOSED.name().equals(entity.getSessionStatus()));
        }
        return false;
    }

    private boolean isNewMerchantEligibleForMilestone(BasicDetailsDto merchant, MileStoneEntity entity, MileStoneEligibilityResponseDto responseDto) {
        log.info("Performing LRV checks for merchantId: {}", merchant.getId());
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchant.getId());
        log.info("Lending risk variable {} for merchantId {}", lendingRiskVariables, merchant.getId());
        if(ObjectUtils.isEmpty(lendingRiskVariables)) {
            log.info("LRV not found for merchantId: {}", merchant.getId());
            return false;
        }

        List<String> inclusionReasonMilestoneList = getInclusionReasonMilestoneList();

        String experianRejection = lendingRiskVariables.getExperianRejection();
        boolean isEligible = (ObjectUtils.isEmpty(entity) || !RTESessionStatus.CLOSED.name().equalsIgnoreCase(entity.getSessionStatus()))
                && (ObjectUtils.isEmpty(experianRejection)
                || inclusionReasonMilestoneList.contains(experianRejection.toLowerCase()));

        if(!ObjectUtils.isEmpty(experianRejection)) {
            responseDto.setExperianRejectionReason(experianRejection);
            if (!inclusionReasonMilestoneList.contains(experianRejection.toLowerCase())) {
                log.info("Experian rejection reason not lies in inclusionReasonMilestone List for merchantId: {} {}", merchant.getId(), experianRejection);
            }
        }
        return isEligible;
    }

    private List<String> getInclusionReasonMilestoneList() {
        return Arrays.stream(inclusionReasons.split(","))
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    private void bureauResponsechecks(BasicDetailsDto merchant, BureauResponseDTO bureauResponseDTO, MileStoneEligibilityResponseDto responseDto, Experian experian, String pinCodeColor, String kycPancard) {
        try {
            if ((ObjectUtils.isEmpty(bureauResponseDTO)
                    || ObjectUtils.isEmpty(bureauResponseDTO.getVariables()))
                    && bureauResponseDTO.getIsNTC() != Boolean.TRUE) {
                log.info("bureau response {} for merchantId {}", bureauResponseDTO, merchant.getId());
                inEligibleForRTEResponse(responseDto);
            }
        }catch (Exception e) {
            log.error("Exception while handling bureau response for merchantId: {} {}", merchant.getId(), Arrays.asList(e.getStackTrace()));
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
        }
    }

    private MileStoneEligibilityResponseDto setGraphData(BasicDetailsDto merchant, DSMileStoneResponse mileStoneResponse, DSMileStoneAchievementResponse achievementResponse, MileStoneEligibilityResponseDto responseDto, BureauResponseDTO bureauResponseDTO, MileStoneEntity entity, String mileStoneCacheKey, Object mileStoneCacheResponse, Experian experian, String kycPancard) {
        log.info("Building graph data for merchantId: {}", merchant.getId());
        try {
            //TODO
            int value = 100 / mileStoneResponse.getTarget().size();
            double graph = 0d;
            String daysCount = null;
            HashMap<Integer, Target> targetMileStoneNoMap = new HashMap<>();

            for (Target target : mileStoneResponse.target)
                targetMileStoneNoMap.put(target.getMilestone_no(), target);

            int days = mileStoneResponse.getTarget_duration_days();

            //BUILDING GRAPH DATA
            if (!ObjectUtils.isEmpty(achievementResponse)) {
                for (DSMileStoneAchievementResponse.Achievement response1 : achievementResponse.achievement) {
                    Date date = new Date();
                    if (days%28==0) { //weekly flow
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

                        }
                    }
                    else if (days%30==0){
                        if ((date.after(response1.getMilestone_start_time())) && date.before(response1.getMilestone_end_time())) {
                            daysCount = "Milestone" + " " + response1.getMilestone_no();
                        }
                    }else {
                        daysCount = RTESessionStatus.COMPLETED.name();
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
                if (days%28==0){
                    daysCount = days == 28 ? "Week 1" : "Milestone 1";
                }else {
                    daysCount="Milestone 1";
                }
            }

            boolean isMileStoneExpiry = false;
            if (!ObjectUtils.isEmpty(entity)) {
                Date date = new Date();
                isMileStoneExpiry = entity.getExpiryDate().getTime() < date.getTime();
                if (isMileStoneExpiry == Boolean.TRUE) {
                    if (!ObjectUtils.isEmpty(mileStoneCacheResponse)) {
                        lendingCache.delete(mileStoneCacheKey);
                    }
                }
                log.info("milestone Expiry is {}", isMileStoneExpiry);
                if (isMileStoneExpiry && RTESessionStatus.IN_PROGRESS.name().equalsIgnoreCase(entity.getSessionStatus())) {
                    entity.setSessionStatus(RTESessionStatus.COMPLETED.name());
                    entity.setGraphData(graph);
                    mileStoneDao.save(entity);
                    responseDto.setIsMileStoneExpiry(true);
                    responseDto.setEnrollState(false);
                    log.info("setting program type when session is in progress for merchantId: {} {}", merchant.getId(), responseDto);
                    responseDto.setProgramType(ObjectUtils.isEmpty(mileStoneResponse.getProgram_type()) ? RTEProgramType.NEW_MERCHANT.name() : mileStoneResponse.getProgram_type());
                    responseDto.setMaxLimit(ObjectUtils.isEmpty(mileStoneResponse.getMax_limit()) ? null : mileStoneResponse.getMax_limit());
                    responseDto.setGraphData(graph);
                    responseDto.setWeekCount(daysCount);
                    responseDto.setPinCode(experian.getPincode());
                    responseDto.setPanCard(kycPancard);

                    if (bureauResponseDTO!=null){
                        responseDto.setProgramEligibleData(bureauResponseDTO.getIsNTC() == Boolean.TRUE ? mileStoneHelperService.setNTCProgramEligibleData() : mileStoneHelperService.setETCProgramEligibleData());
                        responseDto.setProgramActiveData(bureauResponseDTO.getIsNTC() == Boolean.TRUE?
                                mileStoneHelperService.setNTCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()):
                                mileStoneHelperService.setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
                    }else {
                        responseDto.setProgramEligibleData(mileStoneHelperService.setETCProgramEligibleData());
                        responseDto.setProgramActiveData(mileStoneHelperService.setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
                    }
                    responseDto.setDeepLinkUrl(deepLink);
                    responseDto.setIsEligibleForReapply(true);
                    responseDto.setMilStoneEligibility(responseDto.getIsEligibleForReapply());

                    if(isRtev3Enabled && easyLoanUtil.percentScaleUp(merchant.getId(), rtev3RolloutPercent)) {
                        if(!ObjectUtils.isEmpty(responseDto.getProgramType())) {
                            HashMap<String, String> cleverTapEvtData = new HashMap<String, String>() {{
                                put("program_type", RTEProgramType.SLIDER.name().equals(responseDto.getProgramType()) ? "v3" : "v2");
                            }};
                            if(!ObjectUtils.isEmpty(responseDto.getGraphData()) && responseDto.getGraphData() == 1D) {
                                //graph data is 100% when session was is in progress, now marked completed
                                executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.RTE_V3_ACTIVE_COMPLETE.name(), cleverTapEvtData, merchant.getMid()));
                                funnelService.submitEvent(merchant.getId(), null, null,
                                        FunnelEnums.StageId.RTE, FunnelEnums.StageEvent.ENROLL_COMPLETE, responseDto.getProgramType());
                            }else {
                                executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.RTE_V3_ACTIVE_NOT_COMPLETE.name(), cleverTapEvtData, merchant.getMid()));
                                funnelService.submitEvent(merchant.getId(), null, null,
                                        FunnelEnums.StageId.RTE, FunnelEnums.StageEvent.ENROLL_INCOMPLETE, responseDto.getProgramType());
                            }
                        }
                    }
                    return responseDto;
                }
            }

            responseDto.setIsMileStoneExpiry(isMileStoneExpiry);
            responseDto.setWeekCount(daysCount);
            // To replace this with graph data
            responseDto.setGraphData(graph);
            if (bureauResponseDTO!=null){
                responseDto.setProgramEligibleData(bureauResponseDTO.getIsNTC() == Boolean.TRUE ? mileStoneHelperService.setNTCProgramEligibleData() : mileStoneHelperService.setETCProgramEligibleData());
                responseDto.setProgramActiveData(bureauResponseDTO.getIsNTC() == Boolean.TRUE?
                        mileStoneHelperService.setNTCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()):
                        mileStoneHelperService.setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
            }else {
                responseDto.setProgramEligibleData(mileStoneHelperService.setETCProgramEligibleData());
                responseDto.setProgramActiveData(mileStoneHelperService.setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
            }

            responseDto.setDeepLinkUrl(deepLink);
            responseDto.setIsEligibleForReapply(true);
            log.info("setting program type while building graph data for merchantId:{} {}", merchant.getId(), responseDto);
            responseDto.setProgramType(ObjectUtils.isEmpty(mileStoneResponse.getProgram_type()) ? RTEProgramType.NEW_MERCHANT.name() : mileStoneResponse.getProgram_type());
            responseDto.setMaxLimit(ObjectUtils.isEmpty(mileStoneResponse.getMax_limit()) ? null : mileStoneResponse.getMax_limit());
        }catch (Exception e) {
            log.error("Exception while building graph data for merchantId: {} {}", merchant.getId(), Arrays.asList(e.getStackTrace()));
        }
        return responseDto;
    }

    private MileStoneEligibilityResponseDto manageExpiryForExistingMerchant(BasicDetailsDto merchant, BureauResponseDTO bureauResponseDTO,DSMileStoneResponse mileStoneResponse, MileStoneEligibilityResponseDto responseDto, Experian experian, String kycPancard) {
        log.info("achievement response is null for merchantId:{}", merchant.getId());
        try {
            responseDto.setDsErrorMessage("achievement response is null");
            responseDto.setMilStoneEligibility(true);
            responseDto.setEnrollState(true);
            responseDto.setGraphData(null);
            responseDto.setWeekCount(null);
            responseDto.setPinCode(experian.getPincode());
            responseDto.setPanCard(kycPancard);
            log.info("setting program type during expiry management for merchantId: {} {}", merchant.getId(), responseDto);
            responseDto.setProgramType(ObjectUtils.isEmpty(mileStoneResponse.getProgram_type()) ? RTEProgramType.NEW_MERCHANT.name() : mileStoneResponse.getProgram_type());
            responseDto.setMaxLimit(ObjectUtils.isEmpty(mileStoneResponse.getMax_limit()) ? null : mileStoneResponse.getMax_limit());

            if (bureauResponseDTO!=null){
                responseDto.setProgramEligibleData(bureauResponseDTO.getIsNTC() == Boolean.TRUE ? mileStoneHelperService.setNTCProgramEligibleData() : mileStoneHelperService.setETCProgramEligibleData());
                responseDto.setProgramActiveData(bureauResponseDTO.getIsNTC() == Boolean.TRUE?
                        mileStoneHelperService.setNTCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()):
                        mileStoneHelperService.setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
            }else {
                responseDto.setProgramEligibleData(mileStoneHelperService.setETCProgramEligibleData());
                responseDto.setProgramActiveData(mileStoneHelperService.setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
            }
            responseDto.setDeepLinkUrl(deepLink);
            responseDto.setIsEligibleForReapply(true);
        }catch (Exception e) {
            log.error("Exception while checking for existing merchant in rte for merchantId: {} {}", merchant.getId(), Arrays.asList(e.getStackTrace()));
        }
        return responseDto;
    }

    private MileStoneEligibilityResponseDto updateResponse(BasicDetailsDto merchant, MileStoneEligibilityResponseDto responseDto, BureauResponseDTO bureauResponseDTO, Experian experian, MileStoneEntity entity, String kycPancard) {
        log.info("updating response for merchantId: {}", merchant.getId());
        responseDto.setEnrollState(false);
        responseDto.setGraphData(null);
        responseDto.setWeekCount(null);
        log.info("setting program type while updating response for either fresh user or re-enroll merchantId:{} {}", merchant.getId(), responseDto);
        responseDto.setProgramType(ObjectUtils.isEmpty(responseDto.getProgramType()) ? RTEProgramType.NEW_MERCHANT.name() : responseDto.getProgramType());
        responseDto.setMaxLimit(ObjectUtils.isEmpty(responseDto.getMaxLimit()) ? null : responseDto.getMaxLimit());
        responseDto.setPinCode(experian.getPincode());
        responseDto.setPanCard(kycPancard);
        responseDto.setIsEligibleForReapply(true);
        responseDto.setDeepLinkUrl(deepLink);

        if (bureauResponseDTO != null && bureauResponseDTO.getIsNTC() == Boolean.TRUE) {
            responseDto.setProgramEligibleData(mileStoneHelperService.setNTCProgramEligibleData());
            responseDto.setProgramActiveData(mileStoneHelperService.setNTCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
        } else {
            responseDto.setProgramEligibleData(mileStoneHelperService.setETCProgramEligibleData());
            responseDto.setProgramActiveData(mileStoneHelperService.setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
        }

        if (!ObjectUtils.isEmpty(entity) && RTESessionStatus.COMPLETED.name().equalsIgnoreCase(entity.getSessionStatus())) {
            responseDto.setIsMileStoneExpiry(true);
            responseDto.setGraphData(entity.getGraphData());
        } else {
            responseDto.setIsMileStoneExpiry(false);
        }
        return responseDto;
    }

    private MileStoneEligibilityResponseDto getCachedMileStoneOfferResponse(String mileStoneOfferCacheKey) {
        Object mileStoneOfferResponse = lendingCache.get(mileStoneOfferCacheKey);
        if (!ObjectUtils.isEmpty(mileStoneOfferResponse)) {
            try {
                log.info("Returning milestone offer response from cache for {}", mileStoneOfferCacheKey);
                return objectMapper.readValue((String) mileStoneOfferResponse, MileStoneEligibilityResponseDto.class);
            } catch (Exception  e) {
                log.error("Exception while parsing cached milestone offer response: {}", Arrays.asList(e.getStackTrace()));
            }
        }
        return null;
    }

    private void callDSMileStoneApi(BasicDetailsDto merchant, BureauResponseDTO bureauResponseDTO, MileStoneEligibilityResponseDto responseDto, Experian experian, String pinCodeColor, String kycPancard) {
        try {
            String rteV3AmountKey = RTEConstants.RTE_V3_AMOUNT + merchant.getId();
            String loanAmountOfMerchant = ObjectUtils.isEmpty(lendingCache.get(rteV3AmountKey)) ? "25k" : (String) lendingCache.get(rteV3AmountKey);

            if (!ObjectUtils.isEmpty(bureauResponseDTO)) {
                if (bureauResponseDTO.getIsNTC() == Boolean.TRUE) {
                    callMerchantMileStoneDSApi(merchant, 0D, 0D, pinCodeColor, loanAmountOfMerchant, responseDto, bureauResponseDTO.getIsNTC());
                }
                if (bureauResponseDTO.getIsNTC() != Boolean.TRUE
                        && !ObjectUtils.isEmpty(bureauResponseDTO.getVariables()) &&
                        (bureauResponseDTO.getVariables().getBureauScore() != null
                                && bureauResponseDTO.getVariables().getBbs() != null)) {

                    callMerchantMileStoneDSApi(merchant, bureauResponseDTO.getVariables().getBureauScore(), bureauResponseDTO.getVariables().getBbs(), pinCodeColor, loanAmountOfMerchant, responseDto, bureauResponseDTO.getIsNTC());
                }
            }
        }catch (Exception e) {
            log.error("Exception while bureau call for merchantId: {} {}", merchant.getId(), Arrays.asList(e.getStackTrace()));
        }
    }

    private void callMerchantMileStoneDSApi(BasicDetailsDto merchant, Double bureauScore, Double bbs, String pinCodeColor, String loanAmountOfMerchant, MileStoneEligibilityResponseDto responseDto, Boolean isNTC) {
        try {
            DSMileStoneResponse dsMileStoneResponse = dsHandler.fetchMileStoneDatav3(merchant.getId(), bureauScore, bbs, pinCodeColor, loanAmountOfMerchant);
            log.info("milestone data {} for merchantId: {}", dsMileStoneResponse, merchant.getId());

            if (dsMileStoneResponse == null || dsMileStoneResponse.getProgram_type() == null || dsMileStoneResponse.getTarget().isEmpty() ) {
                String mileStoneCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();
                Object mileStoneCacheResponse = lendingCache.get(mileStoneCacheKey);
                if (!ObjectUtils.isEmpty(mileStoneCacheResponse)) {
                    lendingCache.delete(mileStoneCacheKey);
                }
                responseDto.setDsErrorMessage("error in target ds api");
                inEligibleForRTEResponse(responseDto);
            }
            else{
                log.info("milestone response for merchantId:{}, isNTC:{} {}", merchant.getId(), isNTC, dsMileStoneResponse);
                responseDto.setMilStoneEligibility(true);
                responseDto.setProgramType(ObjectUtils.isEmpty(dsMileStoneResponse.getProgram_type()) ? RTEProgramType.NEW_MERCHANT.name() : dsMileStoneResponse.getProgram_type());
                responseDto.setMaxLimit(ObjectUtils.isEmpty(dsMileStoneResponse.getMax_limit()) ? null : dsMileStoneResponse.getMax_limit());
            }
        }catch (Exception e) {
            log.error("Exception while calling merchantMileStone DS Api merchantId: {} {}", merchant.getId(), Arrays.asList(e.getStackTrace()));
        }
    }

    private MileStoneEligibilityResponseDto inEligibleForRTEResponse(MileStoneEligibilityResponseDto responseDto) {
        responseDto.setMilStoneEligibility(false);
        responseDto.setEnrollState(false);
        return responseDto;
    }

    private MileStoneEligibilityResponseDto experianNotFound(BasicDetailsDto merchant, Experian experian,String kycPancard, int pincode, MileStoneEligibilityResponseDto responseDto) {
        log.info("experian is {} for merchantId:{} ", experian, merchant.getId());
        responseDto.setEnrollState(false);
        responseDto.setMilStoneEligibility(true);
        responseDto.setGraphData(null);
        responseDto.setWeekCount(null);
        responseDto.setProgramType(RTEProgramType.NEW_MERCHANT.name());
        responseDto.setProgramEligibleData(mileStoneHelperService.setNTCProgramEligibleData());
        responseDto.setProgramActiveData(mileStoneHelperService.setNTCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
        responseDto.setIsMileStoneExpiry(false);
        responseDto.setDeepLinkUrl(deepLink);
        responseDto.setPinCode(pincode);
        responseDto.setPanCard(kycPancard);
        responseDto.setIsEligibleForReapply(true);
        return responseDto;
    }

    private String processExperianData(BasicDetailsDto merchant,Experian experian, MileStoneEligibilityResponseDto responseDto, String kycPancard, int pincode, List<MileStoneEntity> entityList) {
        String pinCodeColor = null;
        log.info("Processing experian data for pinCode color of merchantId: {}", merchant.getId());
        try {
            if (!ObjectUtils.isEmpty(experian) &&
                    entityList.size() >= 1 && experian.getPincode() != null) {
                pincode = experian.getPincode();
                responseDto.setPinCode(pincode);
                LendingPincodes lendingPincodes = lendingPincodesDao.findByPincode(experian.getPincode());
                if (!ObjectUtils.isEmpty(lendingPincodes)) {
                    log.info("pincode entity is {}", lendingPincodes);
                    pinCodeColor = lendingPincodes.getColor().getValue();
                }
            }

            if (!ObjectUtils.isEmpty(experian) && entityList.size()==0) {
                if (experian.getPincode() != null) {
                    pincode = experian.getPincode();
                    responseDto.setPinCode(pincode);
                    LendingPincodes lendingPincodes = lendingPincodesDao.findByPincode(experian.getPincode());
                    if (!ObjectUtils.isEmpty(lendingPincodes)) {
                        log.info("pincode entity is {}", lendingPincodes);
                        pinCodeColor = lendingPincodes.getColor().getValue();
                    }
                }

                // Infer pin code if not available in Experian
                if (ObjectUtils.isEmpty(experian.getPincode()) && !ObjectUtils.isEmpty(experian.getLatitude()) && !ObjectUtils.isEmpty(experian.getLongitude())) {
                    log.info("Pincode is empty for merchantId:{}", merchant.getId());
                    DEPinCode pinCodeResponse = dsHandler.getInferredPinCode(merchant.getId(), experian.getLatitude(), experian.getLongitude());
                    log.info("PincodeResponse is {}", pinCodeResponse);
                    if (pinCodeResponse.getPincode() == -1 || ObjectUtils.isEmpty(pinCodeResponse)) {
                        log.info("Pincode is -1 from DE response for merchant {}", merchant.getId());
                        responseDto = handleMissingPinCodeFromDEResponse(responseDto, kycPancard, pincode);
                        return null;
                    }
                    if (!ObjectUtils.isEmpty(pinCodeResponse) && pinCodeResponse.getPincode() != -1) {
                        log.info("Pincode is not -1");
                        pincode = pinCodeResponse.getPincode();
                        responseDto.setPinCode(pincode);
                        experian.setPincode(pincode);
                        experianDao.save(experian);
                        LendingPincodes lendingPincodes = lendingPincodesDao.findByPincode(pinCodeResponse.getPincode());
                        log.info("Pincode entity is {}", lendingPincodes);
                        if (!ObjectUtils.isEmpty(lendingPincodes)) {
                            log.info("Pincode entity is {}", lendingPincodes);
                            pinCodeColor = lendingPincodes.getColor().getValue();
                        }
                    }
                }
            }

            if (ObjectUtils.isEmpty(pinCodeColor) || Objects.isNull(pinCodeColor)) {
                log.info("pincode color is empty or null");
                responseDto.setEnrollState(false);
                responseDto.setMilStoneEligibility(false);
            }

        }catch (Exception e) {
            log.error("Exception occurred while processing experian data for merchantId: {} {}", merchant.getId(), Arrays.asList(e.getStackTrace()));
        }
        return pinCodeColor;
    }

    private MileStoneEligibilityResponseDto handleMissingPinCodeFromDEResponse(MileStoneEligibilityResponseDto responseDto, String kycPancard, int pincode) {
        responseDto.setEnrollState(false);
        responseDto.setMilStoneEligibility(true);
        responseDto.setGraphData(null);
        responseDto.setWeekCount(null);
        responseDto.setProgramType(RTEProgramType.NEW_MERCHANT.name());
        responseDto.setProgramEligibleData(mileStoneHelperService.setETCProgramEligibleData());
        responseDto.setProgramActiveData(mileStoneHelperService.setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
        responseDto.setIsMileStoneExpiry(false);
        responseDto.setDeepLinkUrl(deepLink);
        responseDto.setPinCode(pincode);
        responseDto.setPanCard(kycPancard);
        responseDto.setIsEligibleForReapply(true);
        return responseDto;
    }

    private MileStoneEligibilityResponseDto panCardNotFound(BasicDetailsDto merchant, int pincode, MileStoneEligibilityResponseDto responseDto) {
        log.info("panCard is empty for merchant", merchant.getId());
        responseDto.setEnrollState(false);
        responseDto.setMilStoneEligibility(true);
        responseDto.setGraphData(null);
        responseDto.setWeekCount(null);
        responseDto.setProgramType(RTEProgramType.NEW_MERCHANT.name());
        responseDto.setProgramEligibleData(mileStoneHelperService.setNTCProgramEligibleData());
        responseDto.setProgramActiveData(mileStoneHelperService.setNTCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
        responseDto.setIsMileStoneExpiry(false);
        responseDto.setDeepLinkUrl(deepLink);
        responseDto.setPanCard(null);
        responseDto.setPinCode(pincode);
        responseDto.setIsEligibleForReapply(true);
        return responseDto;
    }

    private MileStoneEligibilityResponseDto cacheMileStoneOfferData(BasicDetailsDto merchant, MileStoneEntity entity, MileStoneEligibilityResponseDto responseDto) {
        log.info("mileStoneOffer flag {}", entity.getMilestoneOffer());
        responseDto.setMilStoneEligibility(false);
        responseDto.setEnrollState(false);
        responseDto.setIsEligibleForReapply(false);
        try {
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(RTEConstants.RTE_MILESTONE_OFFER_KEY + merchant.getId());
            addCacheDto.setValue(objectMapper.writeValueAsString(responseDto));
            addCacheDto.setTtl(24);
            lendingCache.add(addCacheDto, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("exception occurred while caching RTE details {} !!", RTEConstants.RTE_MILESTONE_OFFER_KEY + merchant.getId());
        }
        return responseDto;
    }

}
