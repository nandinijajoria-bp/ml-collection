package com.bharatpe.lending.service;


import com.bharatpe.cache.service.LendingCache;
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
import com.bharatpe.lending.loanV2.dto.BureauResponseDTO;
import com.bharatpe.lending.loanV2.handlers.BureauHandler;
import com.bharatpe.lending.common.util.MapperUtil;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.LoanDashboardResponse;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Service
@Slf4j
public class MileStoneHelperService {
    @Autowired
    private MileStoneDaoSlave mileStoneDaoSlave;

    @Autowired
    BureauHandler bureauHandler;

    @Value("${bureau.score.pull.Days}")
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

    @Autowired
    LendingCache lendingCache;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Value("${merchant.milestone.target.user}")
    Integer milestoneTargetUser;

    public BureauResponseDTO calculateBureauScore(BasicDetailsDto merchant) {
        return bureauHandler.getBureauData(merchant.getPanNumber(), merchant.getId(), merchant.getMobile(),
                bureauScorePullDays);
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

    public MileStoneEligibilityResponseDto calculateEligibility(BasicDetailsDto merchant) {

        MileStoneEligibilityResponseDto responseDto = new MileStoneEligibilityResponseDto();

        List<Long> rteEligibleMerchants = loanUtil.rteEligibleMerchant();

        if (!easyLoanUtil.percentScaleUp(merchant.getId(), milestoneTargetUser)
                && !rteEligibleMerchants.contains(merchant.getId())) {
            log.info("merchant Id {} for ineligibility of milestone Program :", merchant.getId());
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
            return responseDto;
        }

        if (rteEligibleMerchants.contains(merchant.getId())) {
            merchant.setCreatedAt(new Date());
            merchant.setCreated_at(new Date());
        }

        boolean lessThan30 = false;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(merchant.getCreatedAt());
        calendar.add(Calendar.MONTH, 1);
        if (new Date().compareTo(calendar.getTime()) <= 0) {
            lessThan30 = true;
        }


        MileStoneSlave entity = mileStoneDaoSlave.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        log.info("entity for milestone journey {} for merchantId {}", entity, merchant.getId());

        String mileStoneCacheKey = LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX + merchant.getId();
        Object mileStoneCacheResponse = lendingCache.get(mileStoneCacheKey);


        LoanDashboardResponse loanDashboardResponse = null;
        if (!ObjectUtils.isEmpty(mileStoneCacheResponse)) {
            try {
                log.info("returning loan details response from cache for {}", merchant.getId());
                loanDashboardResponse = objectMapper.readValue((String) mileStoneCacheResponse, LoanDashboardResponse.class);
                return loanDashboardResponse.getRouteToEligibilityData();
            } catch (Exception e) {
                log.info("exception while fetching response is: {}", e.getMessage());
            }
        }

        if (!ObjectUtils.isEmpty(entity) && Boolean.TRUE.equals(entity.getMilestoneOffer())) {
            log.info("mileStoneOffer flag {}", entity.getMilestoneOffer());
            responseDto.setMilStoneEligibility(false);
            responseDto.setEnrollState(false);
            return responseDto;
        }

        if (lessThan30) {
            responseDto.setMilStoneEligibility(true);
            log.info("entity is {}", entity);
            if ((ObjectUtils.isEmpty(entity)) ||
                    ("COMPLETED".equalsIgnoreCase(entity.getSessionStatus()))) {
                responseDto.setEnrollState(false);
                responseDto.setGraphData(null);
                responseDto.setWeekCount(null);
                responseDto.setProgramEligibleData(setProgramEligibleData());
                responseDto.setProgramActiveData(setProgramActiveData(responseDto.getGraphData(), responseDto.getWeekCount()));
                responseDto.setDeepLinkUrl(deepLink);
            } else {
                DSMileStoneResponse response = fetchTarget(entity);
                log.info("fetch Target data {} for merchant {}", response, merchant.getId());

                DSMileStoneAchievementResponse achievementResponse;

                achievementResponse = dsHandler.fetchMilestoneAchievements(entity.getMerchantId(), entity.getSessionId());
                log.info("achievementResponse is {}", achievementResponse);

                if (achievementResponse == null && (!ObjectUtils.isEmpty(loanDashboardResponse)))
                {
                    try {
                        log.info("returning loan details response from cache for {}", merchant.getId());
                        loanDashboardResponse = objectMapper.readValue((String) mileStoneCacheResponse, LoanDashboardResponse.class);
                        return loanDashboardResponse.getRouteToEligibilityData();
                    } catch (Exception e) {
                        log.info("exception while fetching response is: {}", e.getMessage());
                    }
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


    public DSMileStoneAchievementResponse getAchievementData(DsHandler dsHandler, MileStoneSlave entity) {
        return dsHandler.fetchMilestoneAchievements(entity.getMerchantId(), entity.getSessionId());
    }

    public DSMileStoneResponse fetchTarget(MileStoneSlave entity) {
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
