package com.bharatpe.lending.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.LendingEligibleLoanDao;
import com.bharatpe.lending.common.dao.LendingPincodesDao;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.entity.LendingEligibleLoan;
import com.bharatpe.lending.common.entity.LendingPincodes;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.query.dao.MileStoneDaoSlave;
import com.bharatpe.lending.common.query.dao.MileStoneRewardDaoSlave;
import com.bharatpe.lending.common.query.entity.MileStoneRewardSlave;
import com.bharatpe.lending.common.query.entity.MileStoneSlave;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.common.util.MapperUtil;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.MileStoneDao;
import com.bharatpe.lending.dao.MileStoneRewardDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.MileStoneEntity;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.exception.BureauCallMaskedApiException;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.BureauResponseDTO;
import com.bharatpe.lending.loanV2.dto.Eligibility;
import com.bharatpe.lending.loanV3.revamp.constants.RTEConstants;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.revamp.util.DateUtils;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MileStoneProgramService {

    @Autowired
    MileStoneDao mileStoneDao;

    @Autowired
    MileStoneDaoSlave mileStoneDaoSlave;

    @Autowired
    DsHandler dsHandler;

    @Autowired
    MileStoneRewardDao mileStoneRewardDao;

    @Autowired
    MileStoneRewardDaoSlave mileStoneRewardDaoSlave;


    @Value("${bureau.milestone.score.pull.Days}")
    private Long bureauScorePullDays;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    MileStoneHelperService mileStoneHelperService;

    @Autowired
    LendingPincodesDao lendingPincodesDao;

    @Autowired
    MapperUtil mapperUtil;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    FunnelService funnelService;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    private MerchantSummaryHandler merchantSummaryHandler;

    @Autowired
    LoanDashboardService loanDashboardService;

    @Autowired
    LendingEligibleLoanDao eligibleLoanDao;

    @Autowired
    MerchantService merchantService;

    @Value("${eligibility.refresh.window:1}")
    int eligibilityRefreshWindow;

    @Value("${achievement.cache.ttl:10}")
    int achievementCacheTtl;

    @Value("${enable.rte.v3:true}")
    boolean isRtev3Enabled;

    @Value("${rte.v3.rollout.percent:10}")
    int rtev3RolloutPercent;

    @Autowired
    DateTimeUtil dateTimeUtil;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    MileStoneHelperServicev3 mileStoneHelperServicev3;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    CleverTapEventService cleverTapEventService;

    ExecutorService executorService = Executors.newFixedThreadPool(10);


    public ApiResponse<MileStoneEligibilityResponseDto> checkEligibility(BasicDetailsDto merchant, String loanAmount) {
        log.info("checking milestone eligibility for merchant id {}", merchant.getId());

        if(!ObjectUtils.isEmpty(loanAmount)) {
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(RTEConstants.RTE_V3_AMOUNT + merchant.getId());
            addCacheDto.setValue(loanAmount);
            addCacheDto.setTtl(2);
            lendingCache.add(addCacheDto, TimeUnit.DAYS);
        }

        MileStoneEligibilityResponseDto mileStoneEligibilityResponseDto = isRtev3Enabled && easyLoanUtil.percentScaleUp(merchant.getId(), rtev3RolloutPercent)
                ? mileStoneHelperServicev3.calculateEligibility(merchant, !ObjectUtils.isEmpty(loanAmount))
                : mileStoneHelperService.calculateEligibility(merchant);

        if (Boolean.TRUE.equals(mileStoneEligibilityResponseDto.getMilStoneEligibility())) {
            return new ApiResponse<>(mileStoneEligibilityResponseDto, "200", "ELIGIBLE");
        } else {
            return new ApiResponse<>(mileStoneEligibilityResponseDto, "200", "INELIGIBLE");
        }
    }

    @Async
    public void evictCache(Long merchantId) {
        if (Objects.isNull(merchantId)) {
            log.info("merchant id empty");
        }
        try {
            String offerCacheKey = RTEConstants.RTE_MILESTONE_OFFER_KEY + merchantId;
            log.info("deleting cached key of offer details in RTE for merchant: {}", merchantId);
            lendingCache.delete(offerCacheKey);

            String dashboardDetailsCacheKey = RTEConstants.RTE_MILESTONE_DASHBOARD + merchantId;
            log.info("deleting dashboard details in RTE for merchant: {}", merchantId);
            lendingCache.delete(dashboardDetailsCacheKey);


            String eligibilityCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchantId;
            log.info("deleting cached key of eligibility details in RTE for merchant: {}", merchantId);
            lendingCache.delete(eligibilityCacheKey);

            String loanAmountKey = RTEConstants.RTE_V3_AMOUNT + merchantId;
            log.info("deleting cached key of loanAmount details in RTE for merchant: {}", merchantId);
            lendingCache.delete(loanAmountKey);
        } catch (Exception e) {
            log.info("unable to evict cache for : {}", merchantId);
        }

    }

    public ApiResponse<DSMileStoneResponse> programSummary(BasicDetailsDto merchant) {
        MileStoneEntity entity = mileStoneDao.findTop1ByMerchantIdAndSessionStatus(merchant.getId(), "IN_PROGRESS");

        if (!ObjectUtils.isEmpty(entity)) {
            log.info("milestone entity found for merchant {},entity {}", merchant.getId(), entity);
            DSMileStoneResponse mileStoneResponse = mileStoneHelperService.fetchTarget(entity);
            return new ApiResponse<>(mileStoneResponse);
        }

        log.info("RTE program Summary for Merchant Id is : {}", merchant.getId());
        DSMileStoneResponse response = null;

        String pinCodeColor = null;
        Experian experian = experianDao.getByMerchantId(merchant.getId());
        if (ObjectUtils.isEmpty(experian)) {
            return new ApiResponse<>(false, "400", "PINCODE_NOT_FOUND");
        }
        if (!ObjectUtils.isEmpty(experian.getPincode())) {
            LendingPincodes lendingPincodes = lendingPincodesDao.findByPincode(experian.getPincode());
            pinCodeColor = lendingPincodes.getColor().getValue();
        }

        String kycPancard = kycHandler.getPanNumber(merchant.getId());
        if (ObjectUtils.isEmpty(kycPancard)) {
            return new ApiResponse<>(false, "400", "PANCARD_NOT_FOUND");
        }

        if (merchant.getMobile() != null && !merchant.getMobile().isEmpty()) {
            log.info("Mobile number already set for merchantId: {}", merchant.getId());
        } else {
            Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchant.getId());
            if (basicDetailsDto.isPresent()) {
                BasicDetailsDto details = basicDetailsDto.get();
                String mobile = details.getMobile();
                if (mobile != null && !mobile.isEmpty()) {
                    log.info("mobile_no {} for merchantId: {}", mobile, merchant.getId());
                    merchant.setMobile(mobile);
                } else {
                    log.warn("Mobile number is null or empty for merchantId: {} and details: {}", merchant.getId(),details);
                }
            }
        }

        BureauResponseDTO responseDTO = mileStoneHelperService.calculateBureauScore(kycPancard, merchant);

        log.info("bureau data {} for merchant id {} is :", responseDTO, merchant.getId());

        String rteV3AmountKey = RTEConstants.RTE_V3_AMOUNT + merchant.getId();
        String loanAmountOfMerchant = ObjectUtils.isEmpty(lendingCache.get(rteV3AmountKey)) ? "25k" : (String) lendingCache.get(rteV3AmountKey);

        if (!ObjectUtils.isEmpty(responseDTO) && responseDTO.getIsNTC() == Boolean.TRUE) {
            BureauResponseDTO.BureauVariables variables = new BureauResponseDTO.BureauVariables();
            variables.setBbs(0D);
            variables.setBureauScore(0D);

            if(isRtev3Enabled && easyLoanUtil.percentScaleUp(merchant.getId(), rtev3RolloutPercent)) {
                response = dsHandler.fetchMileStoneDatav3(merchant.getId(), variables.getBureauScore(),
                        variables.getBbs(), pinCodeColor, loanAmountOfMerchant);
            }else{
                response = dsHandler.fetchMileStoneData(merchant.getId(), variables.getBureauScore(),
                        variables.getBbs(), pinCodeColor);
            }

            if (!ObjectUtils.isEmpty(response) && !ObjectUtils.isEmpty(response.getTarget())) {
                return new ApiResponse<>(response);
            }
        }

        if (!ObjectUtils.isEmpty(responseDTO) && responseDTO.getIsNTC() == Boolean.FALSE) {
            log.info("response DTO for merchant {}, bureauScore {}, bbsScore{} ,pinCodeColor {}",
                    merchant.getId(),
                    responseDTO.getVariables().getBureauScore(),
                    responseDTO.getVariables().getBbs(),
                    pinCodeColor);

            if(isRtev3Enabled && easyLoanUtil.percentScaleUp(merchant.getId(), rtev3RolloutPercent)) {
                response = dsHandler.fetchMileStoneDatav3(merchant.getId(), responseDTO.getVariables().getBureauScore(),
                        responseDTO.getVariables().getBbs(), pinCodeColor, loanAmountOfMerchant);
            }else{
                response = dsHandler.fetchMileStoneData(merchant.getId(), responseDTO.getVariables().getBureauScore(),
                        responseDTO.getVariables().getBbs(), pinCodeColor);
            }

            if (!ObjectUtils.isEmpty(response) && !ObjectUtils.isEmpty(response.getTarget())) {
                return new ApiResponse<>(response);
            }
        }

        return new ApiResponse<>(false, "400", "Bureau Response Or DS Response Not Found");

    }


    public ApiResponse<?> createSession(BasicDetailsDto merchant, DSMileStoneResponse dsMileStoneResponse) {
        log.info("start creating session for merchant {}", merchant.getId());
        MileStoneEntity entity = mileStoneDao.findTop1ByMerchantIdAndSessionStatus(merchant.getId(), "IN_PROGRESS");
        if (!ObjectUtils.isEmpty(entity)) {
            if (("IN_PROGRESS".equalsIgnoreCase(entity.getSessionStatus()))) {
                log.info("milestone journey is already in-progress for merchant id {}", entity.getMerchantId());
                return new ApiResponse<>(false, "400", "milestone journey is already in-progress for merchant id");
            }
        }

        try {
            MileStoneEligibilityResponseDto responseDto = isRtev3Enabled && easyLoanUtil.percentScaleUp(merchant.getId(), rtev3RolloutPercent)
                    ? mileStoneHelperServicev3.calculateEligibility(merchant, !ObjectUtils.isEmpty(lendingCache.get(RTEConstants.RTE_V3_AMOUNT + merchant.getId())))
                    : mileStoneHelperService.calculateEligibility(merchant);

            if (Boolean.TRUE.equals(responseDto.getMilStoneEligibility())) {
                entity = mileStoneHelperService.createMileStoneSession(merchant.getId(), dsMileStoneResponse, dsMileStoneResponse.getTarget_duration_days());
                log.info("created an entry for mileStone Entry {}", entity);

                String mileStoneProgramCache = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();
                Object mileStoneCacheResponse = lendingCache.get(mileStoneProgramCache);
                if (!ObjectUtils.isEmpty(mileStoneCacheResponse)) {
                    lendingCache.delete(mileStoneProgramCache);
                }

                String milestoneDashboardCacheKey = RTEConstants.RTE_MILESTONE_DASHBOARD + merchant.getId();
                Object dashboardDetailsCacheKey = lendingCache.get(milestoneDashboardCacheKey);
                if (!ObjectUtils.isEmpty(dashboardDetailsCacheKey)) {
                    log.info("deleting milestone dashboard details data for merchantId: {}",merchant.getId());
                    lendingCache.delete(milestoneDashboardCacheKey);
                }

                if(isRtev3Enabled && easyLoanUtil.percentScaleUp(merchant.getId(), rtev3RolloutPercent)) {
                    if(!ObjectUtils.isEmpty(responseDto.getProgramType())) {
                        Map<String, String> cleverTapEvtData = new HashMap<>();
                        cleverTapEvtData.put("program_type", mileStoneHelperServicev3.getProgramType(responseDto.getProgramType()));

                        if(RTEProgramType.CASHBACK.name().equalsIgnoreCase(responseDto.getProgramType())) {
                            cleverTapEvtData.put("target_active_days", String.valueOf(dsMileStoneResponse.getTarget().get(0).getActive_days()));
                            cleverTapEvtData.put("target_transactions", String.valueOf(dsMileStoneResponse.getTarget().get(0).getNo_txn()));
                            executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.RTE_CASHBACK_ENROLL_DONE.name(), cleverTapEvtData, merchant.getMid()));
                        }else{
                            executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.RTE_V3_ENROLL_DONE.name(), cleverTapEvtData, merchant.getMid()));
                        }
                        funnelService.submitEvent(merchant.getId(), null, null,
                                FunnelEnums.StageId.RTE, FunnelEnums.StageEvent.ENROLL, responseDto.getProgramType());
                    }
                }

                funnelService.submitEvent(merchant.getId(), null, null,
                        FunnelEnums.StageId.RTE, FunnelEnums.StageEvent.RTE_SESSION_CREATED, "RTE Session Creation");
                return new ApiResponse<>(true, "200", "OK");
            }
        } catch (Exception e) {
            log.info("exception is", e.getMessage());
        }
        return new ApiResponse<>(false, "400", "eligibility is false");
    }


    public ApiResponse<MileStoneDashboardDetails> dashboardDetails(BasicDetailsDto merchant) {

        MileStoneDashboardDetails mileStoneDashboardDetails = new MileStoneDashboardDetails();
        mileStoneDashboardDetails.setMerchantId(merchant.getId());

        MileStoneEntity entity = mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());

        if (ObjectUtils.isEmpty(entity)) {
            log.info("entry not found for this merchantId");
            return new ApiResponse<>(false, "400", "Entity Not Found");
        }

        int programDuration = entity.getProgramDuration();

        DSMileStoneAchievementResponse achievementResponse = null;
        /*
        String milestoneDashboardCacheKey = RTEConstants.RTE_MILESTONE_DASHBOARD + merchant.getId();
        Object dashboardDetailsCacheKey = lendingCache.get(milestoneDashboardCacheKey);


        if (!ObjectUtils.isEmpty(dashboardDetailsCacheKey)) {
            try {
                log.info("cache present for milestoneDashboard {} for merchant id {}", milestoneDashboardCacheKey, merchant.getId());
                mileStoneDashboardDetails = objectMapper.convertValue(lendingCache.get(milestoneDashboardCacheKey), MileStoneDashboardDetails.class);
                return new ApiResponse<>(mileStoneDashboardDetails);
            } catch (Exception e) {
                log.info("exception in reading value from cache for RTE for merchant id {}", merchant.getId());
            }

        }
         */

        log.info("Fetching Dashboard details from DE for merchantId: {} ", merchant.getId());
        achievementResponse = mileStoneHelperService.getAchievementData(dsHandler, entity);
        if (achievementResponse == null) {
            return new ApiResponse<>(false, "400", "Achievement response is null");
        }

        log.info("achievementResponse:{} for merchant id {} ",merchant.getId(),achievementResponse);

        dsHandler.pushMilestoneAchievementData(merchant.getId(),achievementResponse);

        DSMileStoneResponse mileStoneResponse = mileStoneHelperService.fetchTarget(entity);

        log.info("DSMileStoneResponse: {} for merchant id {}",merchant.getId(),mileStoneResponse);
        List<MileStoneDashboardData> mapList = new ArrayList<>();

        Map<Integer, Target> targetMileStoneNoMap = mileStoneResponse.getTarget().stream().
                collect(Collectors.toMap(Target::getMilestone_no, Function.identity()));
        log.info("targetMileStoneNoMap{} for merchant id {}",merchant.getId(),targetMileStoneNoMap);
 // WEEKLY ->>>MONTHLY Handling

        int milestoneCount = 0, weekDays = 0, days =0;
        String sessionStatus=null;
        log.info("Entity for merchant: {} id is  {}",merchant.getId(),entity);
        if (!ObjectUtils.isEmpty(entity)) {
            sessionStatus  = entity.getSessionStatus();
            days=entity.getProgramDuration();
        }

        boolean isWeeklyFlowUser=(days%28==0)?true:false;

        if (achievementResponse.achievement.isEmpty()) {

            if (days%28==0 && "IN_PROGRESS".equalsIgnoreCase(sessionStatus)){//existing user handling
                if (days == 28) {
                    weekDays = 7;
                    milestoneCount = days / weekDays;
                } else {
                    weekDays = 14;
                    milestoneCount = days / weekDays;
                }
            }
            else {//new user handling
                weekDays = 30;
                milestoneCount = days / weekDays;
            }
            for (int mileStoneNo = 1; mileStoneNo <= milestoneCount; mileStoneNo++) {
                Target target = targetMileStoneNoMap.get(mileStoneNo);

                List<Object> uniquePayer = new ArrayList<>();
                for (int uniquePayerData = (mileStoneNo - 1) * weekDays; uniquePayerData <= (mileStoneNo - 1) * weekDays + weekDays; uniquePayerData++) {
                    HashMap<Object, Object> uniquePayerDaily = new HashMap<>();
                    uniquePayerDaily.put("date", DateUtils.addDays(entity.getProgramStartDate(), uniquePayerData));
                    uniquePayerDaily.put("unq_payer", 0);
                    uniquePayer.add(uniquePayerDaily);
                }

                List<Object> activeDays = new ArrayList<>();
                for (int activeDaysData = (mileStoneNo - 1) * weekDays; activeDaysData <= (mileStoneNo - 1) * weekDays + weekDays; activeDaysData++) {
                    HashMap<Object, Object> activeDaysDaily = new HashMap<>();
                    activeDaysDaily.put("date", DateUtils.addDays(entity.getProgramStartDate(), activeDaysData));
                    activeDaysDaily.put("active", 0);
                    activeDays.add(activeDaysDaily);
                }

                log.info("total milestone count :{}", milestoneCount);
                log.info("making data for milestone no :{}", mileStoneNo);
                log.info("target data for it no :{}", target);

                MileStoneDashboardData data = MileStoneDashboardData.builder().
                        AchieveMilestone(mileStoneNo)
                        .TargetMileStone(Optional.ofNullable(target).map(Target::getMilestone_no).orElse(0))
                        .AchieveMileStoneActiveDays(0)
                        .TargetActiveDays(Optional.ofNullable(target).map(Target::getActive_days).orElse(0))
                        .AchieveMileStoneUniquePayer(0)
                        .TargetUniquePayer(Optional.ofNullable(target).map(Target::getUnq_payer).orElse(0))
                        .NoOftxn(Optional.ofNullable(target).map(Target::getNo_txn).orElse(0))
                        .PerTxnValue(Optional.ofNullable(target).map(Target::getPer_txn_value).orElse(0))
                        .milestone_start_time(DateUtils.addDaysWithTime(entity.getProgramStartDate(), ((mileStoneNo - 1) * weekDays)))
                        .milestone_end_time(DateUtils.addDaysWithTime(entity.getProgramStartDate(), mileStoneNo * weekDays))
                        .active_days_daily(activeDays)
                        .unq_payer_daily(uniquePayer)
                        .cashback(Optional.ofNullable(target).map(Target::getCashback).orElse(0))
                        .build();
                mapList.add(data);
            }

            mileStoneDashboardDetails.setMapList(mapList);
            mileStoneDashboardDetails.setMileStoneCreatedAt(entity.getCreatedAt());
            mileStoneDashboardDetails.setAchievementActiveDays(0);
            mileStoneDashboardDetails.setAchievementUniquePayer(0);
            mileStoneDashboardDetails.setTargetActiveDays(0);
            mileStoneDashboardDetails.setTargetUniquePayer(0);
            mileStoneDashboardDetails.setWeeklyFlowUser(isWeeklyFlowUser);

        } else {
            for (DSMileStoneAchievementResponse.Achievement achievement : achievementResponse.achievement) {
                Target target = targetMileStoneNoMap.get(achievement.getMilestone_no());
                log.info("total milestone count :{}", milestoneCount);
                log.info("target data for it no :{}", target);

                MileStoneDashboardData data = MileStoneDashboardData.builder().
                        AchieveMilestone(achievement.getMilestone_no())
                        .TargetMileStone(Optional.ofNullable(target).map(Target::getMilestone_no).orElse(0))
                        .AchieveMileStoneActiveDays(achievement.active_days)
                        .TargetActiveDays(Optional.ofNullable(target).map(Target::getActive_days).orElse(0))
                        .AchieveMileStoneUniquePayer(achievement.getUnq_payer())
                        .TargetUniquePayer(Optional.ofNullable(target).map(Target::getUnq_payer).orElse(0))
                        .NoOftxn(Optional.ofNullable(target).map(Target::getNo_txn).orElse(0))
                        .PerTxnValue(Optional.ofNullable(target).map(Target::getPer_txn_value).orElse(0))
                        .milestone_start_time(achievement.getMilestone_start_time())
                        .milestone_end_time(achievement.getMilestone_end_time())
                        .active_days_daily(achievement.getActive_days_daily())
                        .unq_payer_daily(achievement.getUnq_payer_daily())
                        .cashback(Optional.ofNullable(target).map(Target::getCashback).orElse(0))
                        .build();
                mapList.add(data);
            }
            mileStoneDashboardDetails.setMapList(mapList);
            mileStoneDashboardDetails.setMileStoneCreatedAt(entity.getCreatedAt());
            mileStoneDashboardDetails.setAchievementActiveDays(achievementResponse.getTotal().getActive_days());
            mileStoneDashboardDetails.setAchievementUniquePayer(achievementResponse.getTotal().getUnq_payer());
            mileStoneDashboardDetails.setTargetActiveDays(mileStoneResponse.getTotal_target().getActive_days());
            mileStoneDashboardDetails.setTargetUniquePayer(mileStoneResponse.getTotal_target().getUnq_payer());
            mileStoneDashboardDetails.setWeeklyFlowUser(isWeeklyFlowUser);
        }

        if (isRtev3Enabled && easyLoanUtil.percentScaleUp(merchant.getId(), rtev3RolloutPercent)) {
            if (!ObjectUtils.isEmpty(mileStoneResponse.getProgram_type())) {
                LocalDate enrollDate = entity.getCreatedAt().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                LocalDate currentDate = LocalDate.now();
                long daysAfterEnroll = ChronoUnit.DAYS.between(enrollDate, currentDate);
                mileStoneDashboardDetails.setDaysAfterEnroll(daysAfterEnroll);

                Map<String, String> cleverTapEvtData = new HashMap<>();
                cleverTapEvtData.put("program_type", mileStoneHelperServicev3.getProgramType(mileStoneResponse.getProgram_type()));

                if(RTEProgramType.CASHBACK.name().equals(mileStoneResponse.getProgram_type())) {
                    cashbackEvents(merchant, daysAfterEnroll, mileStoneResponse, achievementResponse, cleverTapEvtData);
                }else {
                    log.info("Program Duration is :{}", programDuration);
                    cleverTapEvtData.putIfAbsent("target_achieved_days", String.valueOf(achievementResponse.getTotal() != null ? achievementResponse.getTotal().getActive_days() : 0));
                    cleverTapEvtData.putIfAbsent("program_duration_enrol", String.valueOf(programDuration));
                    cleverTapEvtData.putIfAbsent("total_target_days", String.valueOf(mileStoneResponse.getTotal_target() != null ? mileStoneResponse.getTotal_target().getActive_days() : 0));


                    if (daysAfterEnroll == 7) {
                        pushEventToFunnelService(CleverTapEvents.RTE_V3_ACTIVE_7DAYS.name(), FunnelEnums.StageEvent.ENROLL_7_DAYS, merchant, cleverTapEvtData, mileStoneResponse);
                    }

                    if (daysAfterEnroll == 12) {
                        pushEventToFunnelService(CleverTapEvents.RTE_V3_ACTIVE_12DAYS.name(), FunnelEnums.StageEvent.ENROLL_12_DAYS, merchant, cleverTapEvtData, mileStoneResponse);
                    }

                    if (programDuration == 60 && daysAfterEnroll == 30) {
                        log.info("Program Duration is 60 and days after enroll is 30 ...");
                        pushEventToFunnelService(CleverTapEvents.RTE_V3_ACTIVE_30DAYS.name(), FunnelEnums.StageEvent.ENROLL_30_DAYS, merchant, cleverTapEvtData, mileStoneResponse);
                    } else if (programDuration == 90) {
                        log.info("Program Duration is 90 days....");
                        if (daysAfterEnroll == 30) {
                            log.info("Program Duration is 90 days and daysAfterEnroll is 30....");
                            pushEventToFunnelService(CleverTapEvents.RTE_V3_ACTIVE_30DAYS.name(), FunnelEnums.StageEvent.ENROLL_30_DAYS, merchant, cleverTapEvtData, mileStoneResponse);
                        } else if (daysAfterEnroll == 60) {
                            log.info("Program Duration is 90 days and daysAfterEnroll is 60....");
                            pushEventToFunnelService(CleverTapEvents.RTE_V3_ACTIVE_60DAYS.name(), FunnelEnums.StageEvent.ENROLL_60_DAYS, merchant, cleverTapEvtData, mileStoneResponse);
                        }
                    }
                }
            }
        }

        log.info("achievement response added in cache");
        /*
        AddCacheDto addCacheDto = new AddCacheDto();
        addCacheDto.setKey(milestoneDashboardCacheKey);
        addCacheDto.setValue(mileStoneDashboardDetails);
        addCacheDto.setTtl(achievementCacheTtl);
        lendingCache.add(addCacheDto, TimeUnit.SECONDS);
        log.info("added key in cache");
         */

        return new ApiResponse<>(mileStoneDashboardDetails);
    }

    private void cashbackEvents(BasicDetailsDto merchant, long daysAfterEnroll, DSMileStoneResponse mileStoneResponse, DSMileStoneAchievementResponse achievementResponse, Map<String, String> cleverTapEvtData) {
        cleverTapEvtData.put("program_type", RTEProgramType.CASHBACK.name());
        int completedWeek = (int) (daysAfterEnroll/7);
        String cashbackEventName = String.format(RTEConstants.CASHBACK_ACTIVEDAYS_EVENT, daysAfterEnroll);
        if (completedWeek >= 1 && completedWeek <= 4) { // Ensure valid week range
            log.info("Triggering events for cashback of week {}", completedWeek);
            if(!ObjectUtils.isEmpty(achievementResponse) && !ObjectUtils.isEmpty(achievementResponse.getAchievement())) {
                DSMileStoneAchievementResponse.Achievement weekAchievements = achievementResponse.getAchievement().get(completedWeek - 1); // 0-based index
                cleverTapEvtData.put("active_days_maintained", String.valueOf(weekAchievements.getActive_days()));
                cleverTapEvtData.put("transactions_done", String.valueOf(weekAchievements.getTxn_cnt()));

                if(!ObjectUtils.isEmpty(mileStoneResponse.getTarget())) {
                    Target weeklyTargets = mileStoneResponse.getTarget().get(weekAchievements.getMilestone_no());
                    if(eligibleForWeeklyCashback(merchant, weekAchievements, weeklyTargets)) {
                        cleverTapEvtData.put("cashback_status", Constants.YES);
                        cleverTapEvtData.put("cashback_value", String.valueOf(weeklyTargets.getCashback()));
                    }else{
                        cleverTapEvtData.put("cashback_status", Constants.NO);
                    }
                }
            }
            Target nextWeekTargets = completedWeek < 4 ? mileStoneResponse.getTarget().get(completedWeek) : null;
            if(!ObjectUtils.isEmpty(nextWeekTargets)) {
                log.info("events for next week of {}", merchant.getId());
                cleverTapEvtData.put("target_active_days", String.valueOf(nextWeekTargets.getActive_days()));
                cleverTapEvtData.put("target_transactions", String.valueOf(nextWeekTargets.getNo_txn()));
            }
            pushEventToFunnelService(
                    cashbackEventName,
                    FunnelEnums.StageEvent.RTE_CASHBACK,
                    merchant, cleverTapEvtData, mileStoneResponse
            );
        }
    }

    private boolean eligibleForWeeklyCashback(BasicDetailsDto merchant, DSMileStoneAchievementResponse.Achievement weekAchievements, Target weeklyTargets) {
        if(ObjectUtils.isEmpty(weeklyTargets)) {
            log.info("Weekly targets empty for {}", merchant.getId());
            return false;
        }
        if(weekAchievements.getActive_days() == weeklyTargets.getActive_days() && weekAchievements.getUnq_payer() == weeklyTargets.getUnq_payer()) {
            log.info("Merchant: {} eligible for weekly cashback: {}", merchant.getId(), weeklyTargets.getCashback());
            return true;
        }
        return false;
    }

    public ApiResponse<?> milestoneOffer(BasicDetailsDto merchant, MileStoneOfferRequest request) {

        String mileStoneProgramCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();
        Object mileStoneProgCacheResponse = lendingCache.get(mileStoneProgramCacheKey);
        if (!ObjectUtils.isEmpty(mileStoneProgCacheResponse)) {
            lendingCache.delete(mileStoneProgramCacheKey);
        }
        MileStoneEntity entity = mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());

        if (ObjectUtils.isEmpty(entity)) {
            return new ApiResponse<>(false, "400", "entity not found");
        }
        if (!entity.getMilestoneOffer()) {
            boolean flag = mileStoneHelperService.updateEntity(request, entity);
            if (flag) {
                return new ApiResponse<>(true, "200", "entity  updated in db");
            }
        }
        return new ApiResponse<>(false, "400", "entity not updated in db");

    }

    public ApiResponse<?> claimReward(BasicDetailsDto merchant, String rewardName, Boolean rewardStatus) {
        MileStoneRewardSlave mileStoneReward = mileStoneRewardDaoSlave.findTop1ByMerchantId(merchant.getId());

        log.info("mileStone Reward entity is {} for merchant id {}", mileStoneReward, merchant.getId());
        if (!ObjectUtils.isEmpty(mileStoneReward)) {
            return new ApiResponse<>(false, "400", "Data already exist");
        }
        MileStoneSlave entity = mileStoneDaoSlave.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        log.info("mileStone Entity is {}", entity);

        Boolean rewardFlag = mileStoneHelperService.rewardClaim(merchant.getId(), entity, rewardName, rewardStatus, mileStoneRewardDao);
        if (Boolean.TRUE.equals(rewardFlag)) {
            return new ApiResponse<>(true, "200", "entity save in database");
        }

        return new ApiResponse<>(false, "400", "merchant data not found");
    }


    private void cacheLoanDetailsData(RTEProgramDetailsDto rteProgramDetailsDto, Long merchantId) {
        try {
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchantId);
            addCacheDto.setValue(objectMapper.writeValueAsString(rteProgramDetailsDto));
            addCacheDto.setTtl(15);
            lendingCache.add(addCacheDto, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("exception occurred while caching rte program details for {} !!", RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchantId);
        }
    }

    public void updateEntity(BasicDetailsDto merchant) {
        MileStoneEntity mileStoneEntity =
                mileStoneDao.findTop1ByMerchantIdAndSessionStatus(merchant.getId(), "IN_PROGRESS");
        if (!ObjectUtils.isEmpty(mileStoneEntity)) {
            mileStoneEntity.setMilestoneOffer(true);
            mileStoneEntity.setSessionStatus("CLOSED");
            mileStoneEntity.setComment("Due to Loan Eligibility, closing program");
            mileStoneDao.save(mileStoneEntity);
            log.info("milestone entity saved {}",mileStoneEntity);
        }
    }

    public void checkEligibility(RTEProgramDetailsDto rteProgramDetailsDto, BasicDetailsDto merchant) {
        log.info("checking eligibility for  RTE program{}", merchant.getId());
        MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchant.getId());
        if (ObjectUtils.isEmpty(merchantResponseDTO)) {
            throw new MerchantSummaryExceptionHandler(merchant.getId().toString());
        }

        Experian experian = experianDao.getByMerchantId(merchant.getId());
        if (ObjectUtils.isEmpty(experian)) {
            log.info("In RTE flow,no experian record for merchantId:{},returning empty records", merchant.getId());
            return;
        }

        String preApprovedTag = loanDashboardService.getPreApprovedTag(merchant.getId());
        if (Objects.nonNull(preApprovedTag)) {
            funnelService.submitEvent(merchant.getId(), null, null,
                    FunnelEnums.StageId.LOAN_DASHBOARD, FunnelEnums.StageEvent.PREAPPROVED, preApprovedTag);
        }

        LendingEligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdAndLoanTypeNotTopup(merchant.getId());
        String bureauConsentKey = LendingConstants.BUREAU_CONSENT_KEY_PREFIX + merchant.getId();
        if (Objects.nonNull(lendingCache.get(bureauConsentKey))) {
            eligibilityRefreshWindow = 0;
            lendingCache.delete(bureauConsentKey);
        }
        Date dateWindow = dateTimeUtil.getDatePlusDays(dateTimeUtil.getCurrentDate(), -24 * eligibilityRefreshWindow);

        Eligibility eligibility = null;

        log.info("eligibility check begins !!! {}", merchant.getId());
        if (!ObjectUtils.isEmpty(eligibleLoan) && eligibleLoan.getCreatedAt().after(dateWindow)) {
            log.info("Eligible offers exist for merchant:{}", merchant.getId());
            eligibility = loanDashboardService.createEligibility(merchant.getId(), eligibleLoan);
            if (eligibility != null) {
                log.info("eligibility is not null for merchant: {}", merchant.getId());
                rteProgramDetailsDto.setLoanEligibility(true);
                rteProgramDetailsDto.setLoanAmount(eligibility.getLoanAmount());
                return;
            } else {
                log.info("eligibility is null for merchant: {}", merchant.getId());
            }
        } else {
            log.info("after the date window for merchant: {}", merchant.getId());
        }
        MutableBoolean isDerog = new MutableBoolean(false);
        GlobalLimitResponse globalLimitResponse = new GlobalLimitResponse();
        try {
            globalLimitResponse = apiGatewayService.getGlobalLimit(merchant.getId(),
                    loanDashboardService.isClubV2Member(merchant.getId()), EligibilityRequestSource.RTE);
        } catch (BureauCallMaskedApiException e) {
            log.error("Exception occurred for merchantId:{},execption:{}", merchant.getId(), e);
        }
        Double eligibleAmount = 0D;
        if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
            log.info("Global limit for merchant:{} is {}", merchant.getId(), globalLimitResponse.getData().getGlobalLimit());
            eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
            isDerog.setValue(globalLimitResponse.getData().isDerog());
        }
        if (eligibleAmount > 0D) {
            log.info("Eligibility found for merchant:{}", merchant.getId());
            eligibleLoan = loanDashboardService.recomputeEligibleLoan(globalLimitResponse, null, merchant.getId());
            if (!ObjectUtils.isEmpty(eligibleLoan)) {
                eligibility = loanDashboardService.createEligibility(merchant.getId(), eligibleLoan);
            }
        }
        if (eligibility != null) {
            log.info("Eligibility found for merchant in RTE program {}", merchant.getId());
            rteProgramDetailsDto.setLoanEligibility(true);
            rteProgramDetailsDto.setLoanAmount(eligibility.getLoanAmount());
            return;
        }
        log.info("Eligibility not found for merchant in RTE program:{}", merchant.getId());
        rteProgramDetailsDto.setIneligible(loanDashboardService.getIneligibleReason(merchant.getId(), isDerog, experian.getPincode(), globalLimitResponse));
        rteProgramDetailsDto.setLoanEligibility(false);
        rteProgramDetailsDto.setLoanAmount(0);
    }


    public ApiResponse<Object> programDetails(BasicDetailsDto merchant) {
        String mileStoneCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();
        Object mileStoneCacheResponse = lendingCache.get(mileStoneCacheKey);
        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();

        if (!ObjectUtils.isEmpty(mileStoneCacheResponse)) {
            try {
                log.info("returning rte details response from cache for {}", merchant.getId());
                rteProgramDetailsDto = objectMapper.readValue((String) mileStoneCacheResponse, RTEProgramDetailsDto.class);
                if (!ObjectUtils.isEmpty(rteProgramDetailsDto.getRouteToEligibilityData())) {
                    return new ApiResponse<>(rteProgramDetailsDto);
                }
            } catch (Exception e) {
                log.info("exception while fetching response is: {}", e.getMessage());
            }
        }

        MileStoneEligibilityResponseDto responseDto = isRtev3Enabled && easyLoanUtil.percentScaleUp(merchant.getId(), rtev3RolloutPercent)
                ? mileStoneHelperServicev3.calculateEligibility(merchant, !ObjectUtils.isEmpty(lendingCache.get(RTEConstants.RTE_V3_AMOUNT + merchant.getId())))
                : mileStoneHelperService.calculateEligibility(merchant);

        log.info("response dto is--->{}", responseDto);
        rteProgramDetailsDto.setRouteToEligibilityData(responseDto);
        checkEligibility(rteProgramDetailsDto, merchant);
//        if (Boolean.FALSE.equals(responseDto.getMilStoneEligibility())) {
//            return new ApiResponse<>(rteProgramDetailsDto);
//        }

        KycStatus doc = kycHandler.getPanStatus(merchant.getId());
        rteProgramDetailsDto.setKycStatus(doc);
        MileStoneEntity entity = mileStoneDao.findTop1ByMerchantIdAndSessionStatus(merchant.getId(),"IN_PROGRESS");
        log.info("entity is {} for merchant id {}",entity,merchant.getId());
        log.info("loanEligibility {} of a merchant is {}",rteProgramDetailsDto.getLoanEligibility(),merchant.getId());

        DSMileStoneResponse mileStoneResponse = mileStoneHelperService.fetchTarget(entity);
        if (!ObjectUtils.isEmpty(rteProgramDetailsDto.getLoanEligibility()) &&
                rteProgramDetailsDto.getLoanEligibility().equals(Boolean.TRUE) &&
                 !ObjectUtils.isEmpty(entity)
            && "IN_PROGRESS".equalsIgnoreCase(entity.getSessionStatus())) {
            if(!RTEProgramType.SLIDER.name().equals(mileStoneResponse.getProgram_type())){
                //responseDto.setShowRTELoansFlow(false);
                responseDto.setSessionStatus(RTESessionStatus.CLOSED.name());
                responseDto.setEnrollState(false);
                rteProgramDetailsDto.setRouteToEligibilityData(responseDto);
                updateEntity(merchant);
            } else {
                rteProgramDetailsDto.setTargetLoanAmount(Double.parseDouble(mileStoneResponse.getLoan_amount()));
                LocalDate expiryDate = entity.getExpiryDate().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                LocalDate currentDate = LocalDate.now();
                long daysLeft = ChronoUnit.DAYS.between(currentDate, expiryDate);
                if (daysLeft < 0) {
                    daysLeft = 0;
                }
                rteProgramDetailsDto.setDaysUntilExpiration(daysLeft);
            }
        }

        cacheLoanDetailsData(rteProgramDetailsDto, merchant.getId());
        return new ApiResponse<>(rteProgramDetailsDto);
    }

    private void pushEventToFunnelService(String clearTapEvent, FunnelEnums.StageEvent stageEvent, BasicDetailsDto merchant, Map<String, String> cleverTapEvtData , DSMileStoneResponse mileStoneResponse ) {
        executorService.execute(() -> cleverTapEventService.sendClevertapEvent(clearTapEvent, cleverTapEvtData, merchant.getMid()));
        funnelService.submitEvent(merchant.getId(), null, null,
                FunnelEnums.StageId.RTE, stageEvent, mileStoneResponse.getProgram_type());
    }

    public ApiResponse<Object> checkRteEligibility(Long merchantId) {
        // Initialize response object
        CheckRteEligibilityDTO checkRteEligibilityDTO = new CheckRteEligibilityDTO();
        try {
            Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchantId);
            if (!basicDetailsDto.isPresent()) {
                log.error("Merchant details not found for  {}", merchantId);
                return new ApiResponse<>(checkRteEligibilityDTO);
            }
            //Call programDetails with merchant info
            MileStoneEligibilityResponseDto res = mileStoneHelperServicev3.calculateEligibility(basicDetailsDto.get(), !ObjectUtils.isEmpty(lendingCache.get(RTEConstants.RTE_V3_AMOUNT + basicDetailsDto.get().getId())));
            log.info("Received rte eligibility response: {}", res);

            checkRteEligibilityDTO.setRteEligible(res.getMilStoneEligibility());
            checkRteEligibilityDTO.setRteEnrolled(res.getEnrollState());
            return new ApiResponse<>(checkRteEligibilityDTO);
        } catch (Exception e) {
            log.error("An unexpected error occurred during RTE eligibility check: {}", e.getMessage());
            return new ApiResponse<>(checkRteEligibilityDTO);
        }
    }

    public ApiResponse<?> updatePageViewData(Long merchantId, String cashbackEarned) {
        MileStoneEntity entity = mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
        if (ObjectUtils.isEmpty(entity)) {
            return new ApiResponse<>(false, "400", "entity not found");
        }
        entity.setShowSummaryPage(false);
        entity.setEarnedCashback(!ObjectUtils.isEmpty(cashbackEarned) ? Integer.parseInt(cashbackEarned) : 0);
        mileStoneDao.save(entity);
        log.info("Updated the entity {}", entity);
        return new ApiResponse<>(false, "200", "entity updated in db");
    }
}
