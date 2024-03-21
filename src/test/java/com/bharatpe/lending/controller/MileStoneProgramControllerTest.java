package com.bharatpe.lending.controller;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.DSMileStoneResponse;
import com.bharatpe.lending.dto.MileStoneOfferRequest;
import com.bharatpe.lending.service.MileStoneProgramService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ContextConfiguration(classes = {MileStoneProgramController.class})
@ExtendWith(SpringExtension.class)
class MileStoneProgramControllerTest {
    @Autowired
    private MileStoneProgramController mileStoneProgramController;

    @MockBean
    private MileStoneProgramService mileStoneProgramService;

    /**
     * Method under test: {@link MileStoneProgramController#checkLoanOffer(BasicDetailsDto, MileStoneOfferRequest)}
     */
    @Test
    void testCheckLoanOffer() throws Exception {
        MileStoneOfferRequest mileStoneOfferRequest = new MileStoneOfferRequest();
        mileStoneOfferRequest.setIsOfferAchieved(true);
        String content = (new ObjectMapper()).writeValueAsString(mileStoneOfferRequest);
        MockHttpServletRequestBuilder postResult = MockMvcRequestBuilders.post("/milestone/loan/Offer");
        MockHttpServletRequestBuilder requestBuilder = postResult.param("merchant", String.valueOf(new BasicDetailsDto()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);
        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(mileStoneProgramController)
                .build()
                .perform(requestBuilder);
        actualPerformResult.andExpect(MockMvcResultMatchers.status().is(400));
    }

    /**
     * Method under test: {@link MileStoneProgramController#evictCache(Long)}
     */
    @Test
    void testEvictCache() throws Exception {
        doNothing().when(mileStoneProgramService).evictCache((Long) any());
        MockHttpServletRequestBuilder getResult = MockMvcRequestBuilders.get("/milestone/evictCache");
        MockHttpServletRequestBuilder requestBuilder = getResult.param("merchantId", String.valueOf(1L));
        MockMvcBuilders.standaloneSetup(mileStoneProgramController)
                .build()
                .perform(requestBuilder)
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    /**
     * Method under test: {@link MileStoneProgramController#evictCache(Long)}
     */
    @Test
    void testEvictCache2() throws Exception {
        doNothing().when(mileStoneProgramService).evictCache((Long) any());
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.get("/milestone/evictCache")
                .param("merchantId", "");
        MockMvcBuilders.standaloneSetup(mileStoneProgramController)
                .build()
                .perform(requestBuilder)
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    /**
     * Method under test: {@link MileStoneProgramController#checkLoanOffer(BasicDetailsDto, MileStoneOfferRequest)}
     */
    @Test
    void testCheckLoanOffer2() throws Exception {
        MileStoneOfferRequest mileStoneOfferRequest = new MileStoneOfferRequest();
        mileStoneOfferRequest.setIsOfferAchieved(true);
        String content = (new ObjectMapper()).writeValueAsString(mileStoneOfferRequest);
        MockHttpServletRequestBuilder postResult = MockMvcRequestBuilders.post("/milestone/loan/Offer", "Uri Vars");
        MockHttpServletRequestBuilder requestBuilder = postResult.param("merchant", String.valueOf(new BasicDetailsDto()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);
        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(mileStoneProgramController)
                .build()
                .perform(requestBuilder);
        actualPerformResult.andExpect(MockMvcResultMatchers.status().is(400));
    }

    /**
     * Method under test: {@link MileStoneProgramController#checkProgramSummary(BasicDetailsDto)}
     */
    @Test
    void testCheckProgramSummary() throws Exception {
        MockHttpServletRequestBuilder getResult = MockMvcRequestBuilders.get("/milestone/get/program-summary");
        MockHttpServletRequestBuilder requestBuilder = getResult.param("merchant", String.valueOf(new BasicDetailsDto()));
        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(mileStoneProgramController)
                .build()
                .perform(requestBuilder);
        actualPerformResult.andExpect(MockMvcResultMatchers.status().is(400));
    }

    /**
     * Method under test: {@link MileStoneProgramController#claimReward(BasicDetailsDto, String, Boolean)}
     */
    @Test
    void testClaimReward() throws Exception {
        MockHttpServletRequestBuilder postResult = MockMvcRequestBuilders.post("/milestone/claim/reward");
        MockHttpServletRequestBuilder paramResult = postResult.param("merchant", String.valueOf(new BasicDetailsDto()));
        MockHttpServletRequestBuilder requestBuilder = paramResult.param("rewardClaimedStatus", String.valueOf(true))
                .param("rewardName", "foo");
        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(mileStoneProgramController)
                .build()
                .perform(requestBuilder);
        actualPerformResult.andExpect(MockMvcResultMatchers.status().is(400));
    }

    /**
     * Method under test: {@link MileStoneProgramController#computeEligibility(BasicDetailsDto)}
     */
    @Test
    void testComputeEligibility() throws Exception {
        MockHttpServletRequestBuilder getResult = MockMvcRequestBuilders.get("/milestone/eligibility");
        MockHttpServletRequestBuilder requestBuilder = getResult.param("merchant", String.valueOf(new BasicDetailsDto()));
        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(mileStoneProgramController)
                .build()
                .perform(requestBuilder);
        actualPerformResult.andExpect(MockMvcResultMatchers.status().is(400));
    }

    /**
     * Method under test: {@link MileStoneProgramController#createMileStoneSession(BasicDetailsDto, DSMileStoneResponse)}
     */
    @Test
    void testCreateMileStoneSession() throws Exception {
        DSMileStoneResponse.total_target total_target = new DSMileStoneResponse.total_target();
        total_target.setActive_days(1);
        total_target.setNo_txn(1);
        total_target.setTotal_tpv(1);
        total_target.setUnq_payer(1);

        DSMileStoneResponse dsMileStoneResponse = new DSMileStoneResponse();
        dsMileStoneResponse.setCashback(1);
        dsMileStoneResponse.setTarget(new ArrayList<>());
        dsMileStoneResponse.setTarget_duration_days(1);
        dsMileStoneResponse.setTotal_target(total_target);
        String content = (new ObjectMapper()).writeValueAsString(dsMileStoneResponse);
        MockHttpServletRequestBuilder postResult = MockMvcRequestBuilders.post("/milestone/create-session");
        MockHttpServletRequestBuilder requestBuilder = postResult.param("merchant", String.valueOf(new BasicDetailsDto()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);
        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(mileStoneProgramController)
                .build()
                .perform(requestBuilder);
        actualPerformResult.andExpect(MockMvcResultMatchers.status().is(400));
    }

    /**
     * Method under test: {@link MileStoneProgramController#dashboardDetails(BasicDetailsDto)}
     */
    @Test
    void testDashboardDetails() throws Exception {
        MockHttpServletRequestBuilder getResult = MockMvcRequestBuilders.get("/milestone/dashboardDetails");
        MockHttpServletRequestBuilder requestBuilder = getResult.param("merchant", String.valueOf(new BasicDetailsDto()));
        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(mileStoneProgramController)
                .build()
                .perform(requestBuilder);
        actualPerformResult.andExpect(MockMvcResultMatchers.status().is(400));
    }

    /**
     * Method under test: {@link MileStoneProgramController#programAssignment(BasicDetailsDto)}
     */
    @Test
    void testProgramAssignment() throws Exception {
        MockHttpServletRequestBuilder getResult = MockMvcRequestBuilders.get("/milestone/program-details");
        MockHttpServletRequestBuilder requestBuilder = getResult.param("merchant", String.valueOf(new BasicDetailsDto()));
        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(mileStoneProgramController)
                .build()
                .perform(requestBuilder);
        actualPerformResult.andExpect(MockMvcResultMatchers.status().is(400));
    }
}

