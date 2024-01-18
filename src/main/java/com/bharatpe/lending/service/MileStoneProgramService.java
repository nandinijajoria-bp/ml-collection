package com.bharatpe.lending.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.EligibleLoan;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.LendingPincodesDao;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.entity.LendingPincodes;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.query.dao.MileStoneDaoSlave;
import com.bharatpe.lending.common.query.dao.MileStoneRewardDaoSlave;
import com.bharatpe.lending.common.query.entity.MileStoneRewardSlave;
import com.bharatpe.lending.common.query.entity.MileStoneSlave;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.MapperUtil;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.MileStoneDao;
import com.bharatpe.lending.dao.MileStoneRewardDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.MileStoneEntity;
import com.bharatpe.lending.exception.BureauCallMaskedApiException;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.loanV3.revamp.constants.RTEConstants;
import com.bharatpe.lending.loanV3.revamp.dto.LoanDashboardResponse;
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
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.BureauResponseDTO;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.bharatpe.lending.loanV3.revamp.constants.RTEConstants;

import java.text.SimpleDateFormat;
import java.util.*;
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
    EligibleLoanDao eligibleLoanDao;

    @Value("${eligibility.refresh.window:1}")
    int eligibilityRefreshWindow;

    @Autowired
    DateTimeUtil dateTimeUtil;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;


    public ApiResponse<MileStoneEligibilityResponseDto> checkEligibility(BasicDetailsDto merchant) {
        log.info("checking milestone eligibility for merchant id {}", merchant.getId());
        MileStoneEligibilityResponseDto mileStoneEligibilityResponseDto;
        mileStoneEligibilityResponseDto = mileStoneHelperService.calculateEligibility(merchant);

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
        BureauResponseDTO responseDTO = mileStoneHelperService.calculateBureauScore(kycPancard, merchant);

        log.info("bureau data {} for merchant id {} is :", responseDTO, merchant.getId());

        if (responseDTO.getIsNTC() == Boolean.TRUE) {
            BureauResponseDTO.BureauVariables variables = new BureauResponseDTO.BureauVariables();
            variables.setBbs(0D);
            variables.setBureauScore(0D);
            response = dsHandler.fetchMileStoneData(merchant.getId(), variables.getBureauScore(),
                    variables.getBbs(), pinCodeColor);

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
            response = dsHandler.fetchMileStoneData(merchant.getId(), responseDTO.getVariables().getBureauScore(),
                    responseDTO.getVariables().getBbs(), pinCodeColor);
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
            MileStoneEligibilityResponseDto responseDto = mileStoneHelperService.calculateEligibility(merchant);
            if (Boolean.TRUE.equals(responseDto.getMilStoneEligibility())) {
                entity = mileStoneHelperService.createMileStoneSession(merchant.getId(), dsMileStoneResponse, dsMileStoneResponse.getTarget_duration_days());
                log.info("created an entry for mileStone Entry {}", entity);

                String mileStoneProgramCache = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();
                Object mileStoneCacheResponse = lendingCache.get(mileStoneProgramCache);
                if (!ObjectUtils.isEmpty(mileStoneCacheResponse)) {
                    lendingCache.delete(mileStoneProgramCache);
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
        MileStoneEntity entity = mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());

        if (ObjectUtils.isEmpty(entity)) {
            log.info("entry not found for this merchantId");
            return new ApiResponse<>(false, "400", "Entity Not Found");
        }

        DSMileStoneAchievementResponse achievementResponse = null;
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

        log.info("Fetching Dashboard details from DE for merchantId: {} ", merchant.getId());
        achievementResponse = mileStoneHelperService.getAchievementData(dsHandler, entity);
        if (achievementResponse == null) {
            return new ApiResponse<>(false, "400", "Achievement response is null");
        }

        dsHandler.pushMilestoneAchievementData(merchant.getId(),achievementResponse);

        DSMileStoneResponse mileStoneResponse = mileStoneHelperService.fetchTarget(entity);

        List<MileStoneDashboardData> mapList = new ArrayList<>();

        Map<Integer, Target> targetMileStoneNoMap = mileStoneResponse.getTarget().stream().
                collect(Collectors.toMap(Target::getMilestone_no, Function.identity()));


        int milestoneCount = 0, weekDays = 0;
        if (achievementResponse.achievement.isEmpty()) {
            int days = entity.getProgramDuration();
            if (days == 28) {
                weekDays = 7;
                milestoneCount = days / weekDays;
            } else {
                weekDays = 14;
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

                MileStoneDashboardData data = MileStoneDashboardData.builder().
                        AchieveMilestone(mileStoneNo)
                        .TargetMileStone(target.getMilestone_no())
                        .AchieveMileStoneActiveDays(0)
                        .TargetActiveDays(target.getActive_days())
                        .AchieveMileStoneUniquePayer(0)
                        .TargetUniquePayer(target.getUnq_payer())
                        .milestone_start_time(DateUtils.addDaysWithTime(entity.getProgramStartDate(), ((mileStoneNo - 1) * weekDays)))
                        .milestone_end_time(DateUtils.addDaysWithTime(entity.getProgramStartDate(), mileStoneNo * weekDays))
                        .active_days_daily(activeDays)
                        .unq_payer_daily(uniquePayer)
                        .build();
                mapList.add(data);
            }

            mileStoneDashboardDetails.setMapList(mapList);
            mileStoneDashboardDetails.setMileStoneCreatedAt(entity.getCreatedAt());
            mileStoneDashboardDetails.setAchievementActiveDays(0);
            mileStoneDashboardDetails.setAchievementUniquePayer(0);
            mileStoneDashboardDetails.setTargetActiveDays(0);
            mileStoneDashboardDetails.setTargetUniquePayer(0);

            log.info("achievement response added in cache");
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(milestoneDashboardCacheKey);
            addCacheDto.setValue(mileStoneDashboardDetails);
            addCacheDto.setTtl(10);
            lendingCache.add(addCacheDto, TimeUnit.MINUTES);
            log.info("added key in cache");
            return new ApiResponse<>(mileStoneDashboardDetails);
        } else {
            for (DSMileStoneAchievementResponse.Achievement achievement : achievementResponse.achievement) {
                Target target = targetMileStoneNoMap.get(achievement.getMilestone_no());

                MileStoneDashboardData data = MileStoneDashboardData.builder().
                        AchieveMilestone(achievement.getMilestone_no())
                        .TargetMileStone(target.getMilestone_no())
                        .AchieveMileStoneActiveDays(achievement.active_days)
                        .TargetActiveDays(target.active_days)
                        .AchieveMileStoneUniquePayer(achievement.getUnq_payer())
                        .TargetUniquePayer(target.getUnq_payer())
                        .milestone_start_time(achievement.getMilestone_start_time())
                        .milestone_end_time(achievement.getMilestone_end_time())
                        .active_days_daily(achievement.getActive_days_daily())
                        .unq_payer_daily(achievement.getUnq_payer_daily())
                        .build();
                mapList.add(data);
            }
            mileStoneDashboardDetails.setMapList(mapList);
            mileStoneDashboardDetails.setMileStoneCreatedAt(entity.getCreatedAt());
            mileStoneDashboardDetails.setAchievementActiveDays(achievementResponse.getTotal().getActive_days());
            mileStoneDashboardDetails.setAchievementUniquePayer(achievementResponse.getTotal().getUnq_payer());
            mileStoneDashboardDetails.setTargetActiveDays(mileStoneResponse.getTotal_target().getActive_days());
            mileStoneDashboardDetails.setTargetUniquePayer(mileStoneResponse.getTotal_target().getUnq_payer());

            log.info("achievement response added in cache");
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(milestoneDashboardCacheKey);
            addCacheDto.setValue(mileStoneDashboardDetails);
            addCacheDto.setTtl(10);
            lendingCache.add(addCacheDto, TimeUnit.MINUTES);
            log.info("added key in cache");

            return new ApiResponse<>(mileStoneDashboardDetails);
        }
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

    private void checkEligibility(RTEProgramDetailsDto rteProgramDetailsDto, BasicDetailsDto merchant) {
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

        EligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdAndLoanTypeNotTopup(merchant.getId());
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
                    loanDashboardService.isClubV2Member(merchant.getId()));
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
        MileStoneEligibilityResponseDto responseDto = mileStoneHelperService.calculateEligibility(merchant);
        log.info("response dto is--->{}", responseDto);
        rteProgramDetailsDto.setRouteToEligibilityData(responseDto);
        if (Boolean.FALSE.equals(responseDto.getMilStoneEligibility())) {
            return new ApiResponse<>(rteProgramDetailsDto);
        }

        KycStatusDTO doc = kycHandler.getKycStatus(merchant.getId());
        rteProgramDetailsDto.setKycStatus(doc.getKycStatus());
        checkEligibility(rteProgramDetailsDto, merchant);
        MileStoneEntity entity = mileStoneDao.findTop1ByMerchantIdAndSessionStatus(merchant.getId(),"IN_PROGRESS");
        log.info("entity is {} for merchant id {}",entity,merchant.getId());
        log.info("loanEligibility {} of a merchant is {}",rteProgramDetailsDto.getLoanEligibility(),merchant.getId());

        if (rteProgramDetailsDto.getLoanEligibility().equals(Boolean.TRUE) &&
                "IN_PROGRESS".equalsIgnoreCase(entity.getSessionStatus())) {
            updateEntity(merchant);
        }
        cacheLoanDetailsData(rteProgramDetailsDto, merchant.getId());
        return new ApiResponse<>(rteProgramDetailsDto);
    }

}
