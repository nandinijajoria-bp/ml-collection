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
import com.bharatpe.lending.common.util.MapperUtil;
import com.bharatpe.lending.dao.MileStoneDao;
import com.bharatpe.lending.dao.MileStoneRewardDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.MileStoneEntity;
import com.bharatpe.lending.entity.MileStoneReward;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.BureauResponseDTO;
import com.bharatpe.lending.loanV2.handlers.BureauHandler;
import com.bharatpe.lending.loanV3.revamp.constants.RTEConstants;
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
    private Boolean showRTEProgram = true;

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

    @Value("${merchant.milestone.target.user:100}")
    Integer milestoneTargetUser;

    @Autowired
    KycHandler kycHandler;


    public BureauResponseDTO calculateBureauScore(String panNumber, BasicDetailsDto merchant) {
        return bureauHandler.getBureauData(panNumber, merchant.getId(), merchant.getMobile(),
                bureauScorePullDays, "RTE");
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
        entity.setComment(null);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(entity.getCreatedAt());
        if (days == 30) {
            calendar.add(Calendar.DATE, 30);
        } else {
            calendar.add(Calendar.DATE, 60);
        }
        entity.setExpiryDate(calendar.getTime());
        mileStoneDao.save(entity);
        return entity;
    }

    public MileStoneEligibilityResponseDto.ProgramActiveData setETCProgramActiveData(Double graphData, String weekCount, String programDuration) {
        MileStoneEligibilityResponseDto.ProgramActiveData programActiveData = new MileStoneEligibilityResponseDto.ProgramActiveData();
        String minorHeading = "Get Set Loan in " + programDuration + " days";
        programActiveData.setStripHeading(weekCount);
        programActiveData.setMinorHeading(minorHeading);
        programActiveData.setMajorHeading("Complete your  target!");
        programActiveData.setSubHeading("you are on the right track. Join the program to become eligible");
        programActiveData.setButtonText("Know More");
        programActiveData.setProgressText("ACHIEVED");
        programActiveData.setProgressPercentage(String.valueOf(graphData));
        programActiveData.setButtonActionDeeplink(activeButtonActionDeepLink);
        return programActiveData;
    }

    public MileStoneEligibilityResponseDto.ProgramActiveData setNTCProgramActiveData(Double graphData, String weekCount,String programDuration) {
        MileStoneEligibilityResponseDto.ProgramActiveData programActiveData = new MileStoneEligibilityResponseDto.ProgramActiveData();
        String minorHeading = "Get Set Loan in " + programDuration + " days";
        programActiveData.setStripHeading(weekCount);
        programActiveData.setMinorHeading(minorHeading);
        programActiveData.setMajorHeading("Complete your  target!");
        programActiveData.setSubHeading("you are on the right track. Join the program to become eligible");
        programActiveData.setButtonText("Know More");
        programActiveData.setProgressText("ACHIEVED");
        programActiveData.setProgressPercentage(String.valueOf(graphData));
        programActiveData.setButtonActionDeeplink(activeButtonActionDeepLink);
        return programActiveData;

    }

    public MileStoneEligibilityResponseDto.ProgramEligibleData setETCProgramEligibleData() {
        MileStoneEligibilityResponseDto.ProgramEligibleData programEligibleData = new MileStoneEligibilityResponseDto.ProgramEligibleData();
        programEligibleData.setStripHeading("Join Program");
        programEligibleData.setHeading("Get Set Loan");
        programEligibleData.setSubHeading("Complete  targets to become eligible for loan");
        programEligibleData.setButtonText("START NOW");
        programEligibleData.setBannerImage("https://d30gqtvesfc1d5.cloudfront.net/hubble/r2e/home-joinprogram-lottie-1696315201206.json");
        programEligibleData.setButtonActionDeeplink(eligibleButtonActionDeepLink);
        return programEligibleData;
    }
    public MileStoneEligibilityResponseDto.ProgramEligibleData setNTCProgramEligibleData() {
        MileStoneEligibilityResponseDto.ProgramEligibleData programEligibleData = new MileStoneEligibilityResponseDto.ProgramEligibleData();
        programEligibleData.setStripHeading("Join Program");
        programEligibleData.setHeading("Get Set Loan");
        programEligibleData.setSubHeading("Complete  targets to become eligible for loan");
        programEligibleData.setButtonText("START NOW");
        programEligibleData.setBannerImage("https://d30gqtvesfc1d5.cloudfront.net/hubble/r2e/home-joinprogram-lottie-1696315201206.json");
        programEligibleData.setButtonActionDeeplink(eligibleButtonActionDeepLink);
        return programEligibleData;
    }
    public MileStoneEligibilityResponseDto calculateEligibility(BasicDetailsDto merchant) {
        MileStoneEligibilityResponseDto responseDto = new MileStoneEligibilityResponseDto();
        responseDto.setShowHomeWidgets(milestoneWidgetVisible);
        responseDto.setShowSplashBanner(milestoneSplashVisible);
        responseDto.setShowRTELoansFlow(milestoneEasyLoanVisible);

        try {
        List<Long> rteEligibleMerchants = loanUtil.rteEligibleMerchant();
        if (!easyLoanUtil.percentScaleUp(merchant.getId(), milestoneTargetUser)
                && !rteEligibleMerchants.contains(merchant.getId())) {
            log.info("ineligible due to not present in percent scale up or not in list for merchant id {}", merchant.getId());
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
            return responseDto;
        }

        List<MileStoneEntity> entityList = mileStoneDao.findByMerchantIdAndSessionStatus(merchant.getId(), "COMPLETED");
        log.info("entityList is {}", entityList.size());

        /*
        if (entityList.size() >= 3) {
            log.info("merchant has been enrolled in this program for 3 times");
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
            responseDto.setIsEligibleForReapply(false);
            return responseDto;
        }

         */

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

        String mileStoneOfferCacheKey = RTEConstants.RTE_MILESTONE_OFFER_KEY + merchant.getId();
        Object mileStoneOfferResponse = lendingCache.get(mileStoneOfferCacheKey);
        if (Objects.nonNull(mileStoneOfferResponse) &&
                !ObjectUtils.isEmpty(mileStoneOfferResponse)) {
            try {
                log.info("returning milestone offer response from cache for {}", merchant.getId());
                responseDto = objectMapper.readValue((String) mileStoneOfferResponse, MileStoneEligibilityResponseDto.class);
                return responseDto;
            } catch (Exception e) {
                log.info("exception while fetching response is: {}", e.getMessage());
            }
        }

        if (ObjectUtils.isEmpty(mileStoneOfferResponse)
                && !ObjectUtils.isEmpty(entity)
                && Boolean.TRUE.equals(entity.getMilestoneOffer())) {
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

        String mileStoneCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();
        Object mileStoneCacheResponse = lendingCache.get(mileStoneCacheKey);

        RTEProgramDetailsDto rteProgramDetailsDto = null;
        if (!ObjectUtils.isEmpty(mileStoneCacheResponse)) {
            try {
                log.info("returning rte details response from cache for {}", merchant.getId());
                rteProgramDetailsDto = objectMapper.readValue((String) mileStoneCacheResponse, RTEProgramDetailsDto.class);
                if (!ObjectUtils.isEmpty(rteProgramDetailsDto.getRouteToEligibilityData())) {
                    return rteProgramDetailsDto.getRouteToEligibilityData();
                }
            } catch (Exception e) {
                log.info("exception while fetching response is: {}", e.getMessage());
            }
        }

        boolean lessThan30 = false;
        Calendar calendar = Calendar.getInstance();
        /*Date date1=null;
        try {
            date1 = new SimpleDateFormat("YYYY-MM-DD").parse("2023-09-11");
        } catch (ParseException e) {
            log.info("error is {}",Arrays.asList(e.getStackTrace()));
        }
        merchant.setCreatedAt(date1);
        merchant.setCreated_at(date1);*/
       /* merchant.setCreated_at(new Date());
        merchant.setCreatedAt(new Date());*/
        calendar.setTime(merchant.getCreatedAt());
        calendar.add(Calendar.MONTH, 1);
        if (new Date().compareTo(calendar.getTime()) <= 0) {
            lessThan30 = true;
        }


        if (!lessThan30 && (ObjectUtils.isEmpty(entity)) ) {
            log.info("less Than 30 flag is {} for merchantId {} with no session present ", lessThan30,merchant.getId());
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
            return responseDto;
        }


        String pinCodeColor = null;
        String kycPancard = null;
        int pincode = 0;
        Experian experian = experianDao.getByMerchantId(merchant.getId());
        log.info("Experian data for merchant id {} is {} ",merchant.getId(),experian);
        kycPancard = kycHandler.getPanNumber(merchant.getId());
        log.info("kycPancard data for merchant id {} is {} ",merchant.getId(),kycPancard);
        if (ObjectUtils.isEmpty(entity) || !("IN_PROGRESS".equalsIgnoreCase(entity.getSessionStatus()))) {
            if (ObjectUtils.isEmpty(kycPancard)) {
                log.info("panCard is empty for merchant", merchant.getId());
                responseDto.setEnrollState(false);
                responseDto.setMilStoneEligibility(true);
                responseDto.setGraphData(null);
                responseDto.setWeekCount(null);
                responseDto.setProgramEligibleData(setNTCProgramEligibleData());
                responseDto.setProgramActiveData(setNTCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "60"));
                responseDto.setIsMileStoneExpiry(false);
                responseDto.setDeepLinkUrl(deepLink);
                responseDto.setPanCard(null);
                responseDto.setPinCode(pincode);
                responseDto.setIsEligibleForReapply(true);
                return responseDto;
            }

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
            if (lessThan30 && ObjectUtils.isEmpty(experian)) {
                log.info("experian is {} for merchant id {} ", experian, merchant.getId());
                responseDto.setEnrollState(false);
                responseDto.setMilStoneEligibility(true);
                responseDto.setGraphData(null);
                responseDto.setWeekCount(null);
                responseDto.setProgramEligibleData(setNTCProgramEligibleData());
                responseDto.setProgramActiveData(setNTCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "60"));
                responseDto.setIsMileStoneExpiry(false);
                responseDto.setDeepLinkUrl(deepLink);
                responseDto.setPinCode(pincode);
                responseDto.setPanCard(kycPancard);
                responseDto.setIsEligibleForReapply(true);
                return responseDto;
            }

            if (lessThan30 && !ObjectUtils.isEmpty(experian) && entityList.size()==0) {
                if (experian.getPincode() != null) {
                    pincode = experian.getPincode();
                    responseDto.setPinCode(pincode);
                    LendingPincodes lendingPincodes = lendingPincodesDao.findByPincode(experian.getPincode());
                    if (!ObjectUtils.isEmpty(lendingPincodes)) {
                        log.info("pincode entity is {}", lendingPincodes);
                        pinCodeColor = lendingPincodes.getColor().getValue();
                    }
                }

                if (ObjectUtils.isEmpty(experian.getPincode())
                        && !ObjectUtils.isEmpty(experian.getLatitude()) &&
                        !ObjectUtils.isEmpty(experian.getLongitude())) {
                    log.info("pincode is empty for merchant id {}",merchant.getId());
                    DEPinCode pinCodeResponse = dsHandler.getInferredPinCode(merchant.getId(), experian.getLatitude(), experian.getLongitude());
                    log.info("pincodeResponse is {}",pinCodeResponse);
                    if ((lessThan30 && (pinCodeResponse.getPincode() == -1 || ObjectUtils.isEmpty(pinCodeResponse)))) {
                        log.info("pincode is -1 from de response for merchant {}", merchant.getId(), merchant.getId());
                        responseDto.setEnrollState(false);
                        responseDto.setMilStoneEligibility(true);
                        responseDto.setGraphData(null);
                        responseDto.setWeekCount(null);
                        responseDto.setProgramEligibleData(setETCProgramEligibleData());
                        responseDto.setProgramActiveData(setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "30"));
                        responseDto.setIsMileStoneExpiry(false);
                        responseDto.setDeepLinkUrl(deepLink);
                        responseDto.setPinCode(pincode);
                        responseDto.setPanCard(kycPancard);
                        responseDto.setIsEligibleForReapply(true);
                        return responseDto;
                    }
                    if (lessThan30 && !ObjectUtils.isEmpty(pinCodeResponse) && pinCodeResponse.getPincode() != -1) {
                        log.info("pincode is not -1");
                        pincode = pinCodeResponse.getPincode();
                        responseDto.setPinCode(pincode);
                        experian.setPincode(pincode);
                        experianDao.save(experian);
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

        }

        BureauResponseDTO bureauResponseDTO=null;
//        MileStoneEntity inProgressEntity = mileStoneDao.findByMerchantIdAndSessionStatus(merchant.getId(), "IN_PROGRESS");
        String sessionStatus=null;
        if (!ObjectUtils.isEmpty(entity)) {
        sessionStatus  = entity.getSessionStatus();
        }
        if (ObjectUtils.isEmpty(entity) ||!"IN_PROGRESS".equalsIgnoreCase(sessionStatus))
        {
            bureauResponseDTO = calculateBureauScore(kycPancard, merchant);

            if ((ObjectUtils.isEmpty(bureauResponseDTO)
                    || ObjectUtils.isEmpty(bureauResponseDTO.getVariables()))
                    && bureauResponseDTO.getIsNTC() != Boolean.TRUE) {
                log.info("bureau response {} for merchant id {}", bureauResponseDTO, merchant.getId());
                responseDto.setMilStoneEligibility(false);
                responseDto.setEnrollState(false);
                return responseDto;
            }
        }


        log.info("bureauResponse {} for merchant {}", bureauResponseDTO, merchant.getId());


        if (!"IN_PROGRESS".equalsIgnoreCase(sessionStatus) && !ObjectUtils.isEmpty(bureauResponseDTO)) {
            if (bureauResponseDTO.getIsNTC() == Boolean.TRUE) {
                DSMileStoneResponse fetchMileStoneData;
                BureauResponseDTO.BureauVariables variables = new BureauResponseDTO.BureauVariables();
                variables.setBbs(0D);
                variables.setBureauScore(0D);
                fetchMileStoneData = dsHandler.fetchMileStoneData(merchant.getId(), variables.getBureauScore(), variables.getBbs(), pinCodeColor);
                log.info("milestone data {} for merchant id {}", fetchMileStoneData, merchant.getId());

                if (fetchMileStoneData == null) {

                    if (!ObjectUtils.isEmpty(mileStoneCacheResponse)) {
                        lendingCache.delete(mileStoneCacheKey);
                    }

//                    if (ObjectUtils.isEmpty(inProgressEntity)) {
                    if (ObjectUtils.isEmpty(!"IN_PROGRESS".equalsIgnoreCase(sessionStatus)))
                    {
                        responseDto.setMilStoneEligibility(false);
                        responseDto.setEnrollState(false);
                        return responseDto;
                    } else {
                        log.info("milestone data is null for merchant id {}", merchant.getId());
                        responseDto.setMilStoneEligibility(true);
                        responseDto.setEnrollState(true);
                        responseDto.setDsErrorMessage("error in target ds api");
                        responseDto.setProgramEligibleData(setNTCProgramEligibleData());
                        responseDto.setGraphData(null);
                        responseDto.setWeekCount(null);
                        responseDto.setPinCode(experian.getPincode());
                        responseDto.setPanCard(kycPancard);
                        responseDto.setProgramActiveData(setNTCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "30"));
                        responseDto.setDeepLinkUrl(deepLink);
                        responseDto.setIsEligibleForReapply(true);
                        return responseDto;
                    }

                }

                log.info("milestone response for new user merchant id {}", merchant.getId());
                if (fetchMileStoneData.getTarget().isEmpty()) {
                    log.info("targets are null for merchantId {}", merchant.getId());
                    responseDto.setMilStoneEligibility(false);
                    responseDto.setEnrollState(false);
                    return responseDto;
                }
            }
            if (bureauResponseDTO.getIsNTC() != Boolean.TRUE
                    && !ObjectUtils.isEmpty(bureauResponseDTO.getVariables()) &&
                    (bureauResponseDTO.getVariables().getBureauScore() != null
                            && bureauResponseDTO.getVariables().getBbs() != null)) {

                DSMileStoneResponse fetchMileStoneData = dsHandler.fetchMileStoneData(merchant.getId(), bureauResponseDTO.getVariables().getBureauScore(),
                        bureauResponseDTO.getVariables().getBbs(), pinCodeColor);

                log.info("milestone data {} for merchant id {}", fetchMileStoneData, merchant.getId());

                if (fetchMileStoneData == null) {
                    if (!ObjectUtils.isEmpty(mileStoneCacheResponse)) {
                        lendingCache.delete(mileStoneCacheKey);
                    }
//                    if (ObjectUtils.isEmpty(inProgressEntity)) {
                    if (ObjectUtils.isEmpty(!"IN_PROGRESS".equalsIgnoreCase(sessionStatus)))
                    {
                        responseDto.setMilStoneEligibility(false);
                        responseDto.setEnrollState(false);
                        return responseDto;

                    } else {
                        log.info("milestone data is null for merchant id {}", merchant.getId());
                        responseDto.setMilStoneEligibility(true);
                        responseDto.setEnrollState(true);
                        responseDto.setDsErrorMessage("error in target ds api");
                        responseDto.setProgramEligibleData(setETCProgramEligibleData());
                        responseDto.setGraphData(null);
                        responseDto.setWeekCount(null);
                        responseDto.setPinCode(experian.getPincode());
                        responseDto.setPanCard(kycPancard);
                        responseDto.setProgramActiveData(setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "30"));
                        responseDto.setDeepLinkUrl(deepLink);
                        responseDto.setIsEligibleForReapply(true);
                        return responseDto;
                    }

                }

                log.info("milestone response for old user merchant id {}", merchant.getId());
                if (fetchMileStoneData.getTarget().isEmpty()) {
                    log.info("targets are null for merchantId {}", merchant.getId());
                    responseDto.setMilStoneEligibility(false);
                    responseDto.setEnrollState(false);
                    return responseDto;
                }
            }
        }


        List inclusionReasonMilestoneList = Arrays.asList
                ("LIMIT BLOCKED: Offer set 0",
                        "LIMIT BLOCKED: Less than 10K offer",
                        "NTC",
                        "Risk Segment Exclusion: NTB vintage less than 30",
                        "Thin File ETC");


        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchant.getId());
        log.info("lending risk variable {} for merchantId {}", lendingRiskVariables, merchant.getId());


        if ((ObjectUtils.isEmpty(entity)
        || !ObjectUtils.isEmpty(entity) && !"CLOSED".equalsIgnoreCase(entity.getSessionStatus()))
                && !ObjectUtils.isEmpty(lendingRiskVariables)
                && (ObjectUtils.isEmpty(lendingRiskVariables.getExperianRejection())
                || inclusionReasonMilestoneList.contains(lendingRiskVariables.getExperianRejection()))) {
            responseDto.setMilStoneEligibility(true);
            log.info("entity for milestone {}", entity);
            if ((ObjectUtils.isEmpty(entity)) ||
                    (!ObjectUtils.isEmpty(entity) && "COMPLETED".equalsIgnoreCase(entity.getSessionStatus()))) {
                log.info("------test----{}",responseDto);
                responseDto.setEnrollState(false);
                responseDto.setGraphData(null);
                responseDto.setWeekCount(null);
                responseDto.setPinCode(experian.getPincode());
                responseDto.setPanCard(kycPancard);
                responseDto.setIsEligibleForReapply(true);
                responseDto.setDeepLinkUrl(deepLink);
                if (bureauResponseDTO!=null){
                    responseDto.setProgramEligibleData(bureauResponseDTO.getIsNTC() == Boolean.TRUE?setNTCProgramEligibleData():setETCProgramEligibleData());
                    responseDto.setProgramActiveData(bureauResponseDTO.getIsNTC() == Boolean.TRUE?
                            setNTCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "60"):
                            setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "30"));
                }else {
                    responseDto.setProgramEligibleData(setETCProgramEligibleData());
                    responseDto.setProgramActiveData(setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "30"));
                }


                if (!ObjectUtils.isEmpty(entity) && "COMPLETED".equalsIgnoreCase(entity.getSessionStatus())) {
                    responseDto.setIsMileStoneExpiry(true);
                    responseDto.setGraphData(entity.getGraphData());
                } else {
                    responseDto.setIsMileStoneExpiry(false);
                }
                log.info("------test1----{}",responseDto);
            }
            else {

                // Old Session Flow->>>> expiry management
                DSMileStoneResponse response = fetchTarget(entity);
                log.info("fetch Target data {} for merchant {}", response, merchant.getId());

                DSMileStoneAchievementResponse achievementResponse;

                achievementResponse = dsHandler.fetchMilestoneAchievements(entity.getMerchantId(), entity.getSessionId());
                log.info("achievementResponse is {}", achievementResponse);

                if (achievementResponse == null && (!ObjectUtils.isEmpty(rteProgramDetailsDto))) {
                    try {
                        log.info("returning response from cache for when achievements are null for merchant id {}", merchant.getId());
                        rteProgramDetailsDto = objectMapper.readValue((String) mileStoneCacheResponse, RTEProgramDetailsDto.class);
                        return rteProgramDetailsDto.getRouteToEligibilityData();
                    } catch (Exception e) {
                        log.info("exception while fetching response is: {}", e.getMessage());
                    }
                }

                if (achievementResponse == null && (ObjectUtils.isEmpty(rteProgramDetailsDto))) {
                    log.info("achievement response is null for merchant id {}", merchant.getId());
                    responseDto.setDsErrorMessage("achievement response is null");
                    responseDto.setMilStoneEligibility(true);
                    responseDto.setEnrollState(true);
                    responseDto.setGraphData(null);
                    responseDto.setWeekCount(null);
                    responseDto.setPinCode(experian.getPincode());
                    responseDto.setPanCard(kycPancard);
                    if (bureauResponseDTO!=null){
                        responseDto.setProgramEligibleData(bureauResponseDTO.getIsNTC() == Boolean.TRUE?setNTCProgramEligibleData():setETCProgramEligibleData());
                        responseDto.setProgramActiveData(bureauResponseDTO.getIsNTC() == Boolean.TRUE?
                                setNTCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "60"):
                                setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "30"));
                    }else {
                        responseDto.setProgramEligibleData(setETCProgramEligibleData());
                        responseDto.setProgramActiveData(setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "30"));
                    }
                    responseDto.setDeepLinkUrl(deepLink);
                    responseDto.setIsEligibleForReapply(true);
                    return responseDto;
                }


                //TODO
                int value = 100 / response.getTarget().size();
                double graph = 0d;
                String daysCount = null;
                HashMap<Integer, Target> targetMileStoneNoMap = new HashMap<>();

                for (Target target : response.target)
                    targetMileStoneNoMap.put(target.getMilestone_no(), target);

                int days = response.getTarget_duration_days();
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
                    if (isMileStoneExpiry && "IN_PROGRESS".equalsIgnoreCase(entity.getSessionStatus())) {
                        entity.setSessionStatus("COMPLETED");
                        entity.setGraphData(graph);
                        mileStoneDao.save(entity);
                        /*
                        List<MileStoneEntity> completedEntity = mileStoneDao.findByMerchantIdAndSessionStatus(merchant.getId(), "COMPLETED");

                        if (completedEntity.size() == 3) {
                            responseDto.setMilStoneEligibility(false);
                            responseDto.setEnrollState(false);
                            responseDto.setIsEligibleForReapply(false);
                            return responseDto;
                        }

                         */

                        responseDto.setIsMileStoneExpiry(true);
                        responseDto.setEnrollState(false);
                        responseDto.setGraphData(graph);
                        responseDto.setWeekCount(daysCount);
                        responseDto.setPinCode(experian.getPincode());
                        responseDto.setPanCard(kycPancard);
                        if (bureauResponseDTO!=null){
                            responseDto.setProgramEligibleData(bureauResponseDTO.getIsNTC() == Boolean.TRUE?setNTCProgramEligibleData():setETCProgramEligibleData());
                            responseDto.setProgramActiveData(bureauResponseDTO.getIsNTC() == Boolean.TRUE?
                                    setNTCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "60"):
                                    setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "30"));
                        }else {
                            responseDto.setProgramEligibleData(setETCProgramEligibleData());
                            responseDto.setProgramActiveData(setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "30"));
                        }
                        responseDto.setDeepLinkUrl(deepLink);
                        responseDto.setIsEligibleForReapply(true);
                        responseDto.setMilStoneEligibility(responseDto.getIsEligibleForReapply());
                        return responseDto;
                    }
                }

                responseDto.setIsMileStoneExpiry(isMileStoneExpiry);
                responseDto.setWeekCount(daysCount);
                // To replace this with graph data
                responseDto.setGraphData(graph);
                if (bureauResponseDTO!=null){
                responseDto.setProgramEligibleData(bureauResponseDTO.getIsNTC() == Boolean.TRUE?setNTCProgramEligibleData():setETCProgramEligibleData());
                responseDto.setProgramActiveData(bureauResponseDTO.getIsNTC() == Boolean.TRUE?
                        setNTCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "60"):
                        setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "30"));
                }else {
                    responseDto.setProgramEligibleData(setETCProgramEligibleData());
                    responseDto.setProgramActiveData(setETCProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount(), "30"));
                }

                responseDto.setDeepLinkUrl(deepLink);
                responseDto.setIsEligibleForReapply(true);

            }
            log.info("responseDto--->{}",responseDto);
            return responseDto;

            }
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
            return responseDto;

        }
        catch (Exception e)
        {
            log.error("exception in calculate Eligibility flow for merchant id: {} and exception is {} ",merchant.getId(),Arrays.asList(e.getStackTrace()));
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
            responseDto.setShowHomeWidgets(milestoneWidgetVisible);
            responseDto.setShowSplashBanner(milestoneSplashVisible);
            responseDto.setShowRTELoansFlow(milestoneEasyLoanVisible);
            return responseDto;
        }

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
