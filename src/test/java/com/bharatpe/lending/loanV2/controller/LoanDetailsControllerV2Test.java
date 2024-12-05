package com.bharatpe.lending.loanV2.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@TestConfiguration
public class LoanDetailsControllerV2Test {
    @Mock
    private LoanDetailsServiceV2 loanDetailsServiceV2;

    @InjectMocks
    private LoanDetailsControllerV2 loanDetailsControllerV2;


    @Test
    public void testBlDocSkip_withValidMerchantAndDocSkip() throws Exception {
        MockHttpServletRequestBuilder getResult = MockMvcRequestBuilders.get("/lending/merchant/docSkip")
                .param("docSkip", "true");
        MockHttpServletRequestBuilder requestBuilder = getResult.param("merchant", String.valueOf(new BasicDetailsDto()));
        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(loanDetailsControllerV2)
                .build()
                .perform(requestBuilder);
        actualPerformResult.andExpect(MockMvcResultMatchers.status().is(400));
    }

    @Test
    public void testBlDocSkip_withNullMerchant() throws Exception {
        MockHttpServletRequestBuilder getResult = MockMvcRequestBuilders.get("/lending/merchant/docSkip")
                .param("docSkip", "true");

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(loanDetailsControllerV2).build();
        mockMvc.perform(getResult)
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        verify(loanDetailsServiceV2, times(0)).updateDocSkipData(any(), anyBoolean());
    }

}
