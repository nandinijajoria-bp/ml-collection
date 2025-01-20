package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.enums.EnachMode;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.ugro.validations.UgroPayloadValidation;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.util.LoanUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class UgroNachMandateServiceTest {
    @Mock
    CommonService commonService;

    @Mock
    ILenderAPIGateway lenderAPIGateway;

    @Mock
    UgroPayloadValidation payloadValidation;

    @Mock
    EnachHandler enachHandler;

    @Mock
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Mock
    LoanUtil loanUtil;

    @Mock
    UgroConfig ugroConfig;

    @InjectMocks
    UgroNachMandateService ugroNachMandateService;

    @Test
    public void testInvokeNachMandate() throws Exception {
        when(ugroConfig.getNachPlusDays()).thenReturn(1000);
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(123L);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadStatus("");
        lendingApplicationLenderDetails.setSanctionStatus("");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, false);

        LendingApplicationDetails lendingApplicationDetails = new LendingApplicationDetails();
        lendingApplicationDetails.setIsNachSkip(true);
        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(any())).thenReturn(lendingApplicationDetails);

        MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = new MerchantNachDetailsResponseDTO();
        merchantNachDetailsResponseDTO.setAccountNumber("232323232");
        merchantNachDetailsResponseDTO.setIfscCode("2323232");
        merchantNachDetailsResponseDTO.setApplicantName("Akash");
        merchantNachDetailsResponseDTO.setStartDate(new Date());

        when(enachHandler.findByMerchantIdAndApplicationIdAndLender(any(), any(), any())).thenReturn(merchantNachDetailsResponseDTO);

        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", "null", "");
        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDTO);

        ugroNachMandateService.invokeNachMandate(lenderAssociationDetailsRequest);
    }

    @Test
    public void testInvokeNachMandate_ADHAAR() throws Exception {
        when(ugroConfig.getNachPlusDays()).thenReturn(1000);
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(123L);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadStatus("");
        lendingApplicationLenderDetails.setSanctionStatus("");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, false);

        LendingApplicationDetails lendingApplicationDetails = new LendingApplicationDetails();
        lendingApplicationDetails.setIsNachSkip(true);
        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(any())).thenReturn(lendingApplicationDetails);

        MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = new MerchantNachDetailsResponseDTO();
        merchantNachDetailsResponseDTO.setAccountNumber("232323232");
        merchantNachDetailsResponseDTO.setIfscCode("2323232");
        merchantNachDetailsResponseDTO.setApplicantName("Akash");
        merchantNachDetailsResponseDTO.setStartDate(new Date());
        merchantNachDetailsResponseDTO.setMode(EnachMode.ADHAAR.name());

        when(enachHandler.findByMerchantIdAndApplicationIdAndLender(any(), any(), any())).thenReturn(merchantNachDetailsResponseDTO);

        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", "null", "");
        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDTO);

        ugroNachMandateService.invokeNachMandate(lenderAssociationDetailsRequest);
    }

    @Test
    public void testInvokeNachMandate_EmptyPayload() throws Exception {
        when(ugroConfig.getNachPlusDays()).thenReturn(1000);
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(123L);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadStatus("");
        lendingApplicationLenderDetails.setSanctionStatus("");
        lendingApplicationLenderDetails.setLeadId(null);
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, false);

        LendingApplicationDetails lendingApplicationDetails = new LendingApplicationDetails();
        lendingApplicationDetails.setIsNachSkip(true);
        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(any())).thenReturn(lendingApplicationDetails);

        MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = new MerchantNachDetailsResponseDTO();
        merchantNachDetailsResponseDTO.setAccountNumber("232323232");
        merchantNachDetailsResponseDTO.setIfscCode("2323232");
//        merchantNachDetailsResponseDTO.setApplicantName("Akash");
        merchantNachDetailsResponseDTO.setStartDate(new Date());

        when(enachHandler.findByMerchantIdAndApplicationIdAndLender(any(), any(), any())).thenReturn(merchantNachDetailsResponseDTO);

        when(payloadValidation.isInvalidNachMandatePayload(any())).thenReturn(true);

        ugroNachMandateService.invokeNachMandate(lenderAssociationDetailsRequest);
    }

    @Test
    public void testInvokeNachMandate_Exception() throws Exception {
        when(ugroConfig.getNachPlusDays()).thenReturn(1000);
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(123L);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadStatus("");
        lendingApplicationLenderDetails.setSanctionStatus("");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, false);

        LendingApplicationDetails lendingApplicationDetails = new LendingApplicationDetails();
        lendingApplicationDetails.setIsNachSkip(true);
        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(any())).thenReturn(lendingApplicationDetails);

        MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = new MerchantNachDetailsResponseDTO();
        merchantNachDetailsResponseDTO.setAccountNumber("232323232");
        merchantNachDetailsResponseDTO.setIfscCode("2323232");
        merchantNachDetailsResponseDTO.setApplicantName("Akash");
        merchantNachDetailsResponseDTO.setStartDate(new Date());

        when(enachHandler.findByMerchantIdAndApplicationIdAndLender(any(), any(), any())).thenReturn(merchantNachDetailsResponseDTO);

        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", "null", "");
        when(lenderAPIGateway.invokeStage(any(), any())).thenThrow(new RuntimeException("Some exception has occurred!"));

        ugroNachMandateService.invokeNachMandate(lenderAssociationDetailsRequest);
    }

    @Test
    public void testInvokeNachMandate_PayloadException() throws Exception {
        when(ugroConfig.getNachPlusDays()).thenReturn(1000);
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(123L);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadStatus("");
        lendingApplicationLenderDetails.setSanctionStatus("");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, false);

        LendingApplicationDetails lendingApplicationDetails = new LendingApplicationDetails();
        lendingApplicationDetails.setIsNachSkip(true);
        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(any())).thenReturn(lendingApplicationDetails);

        MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = new MerchantNachDetailsResponseDTO();
        merchantNachDetailsResponseDTO.setAccountNumber("232323232");
        merchantNachDetailsResponseDTO.setIfscCode("2323232");
        merchantNachDetailsResponseDTO.setApplicantName("Akash");
        merchantNachDetailsResponseDTO.setStartDate(new Date());

        when(enachHandler.findByMerchantIdAndApplicationIdAndLender(any(), any(), any())).thenThrow(new RuntimeException("Some exception has occurred!"));

        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", "null", "");
        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDTO);

        ugroNachMandateService.invokeNachMandate(lenderAssociationDetailsRequest);
    }

}
























