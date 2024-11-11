package com.bharatpe.lending.loanV3.services.associationsV2.smfg.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.loanV3.config.SmfgConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.smfg.SmfgAppPushRequest;
import com.bharatpe.lending.loanV3.dto.response.smfg.SmfgAppPushResponseDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
public class SmfgNachMandateServiceTest {

    @InjectMocks
    private SmfgNachMandateService smfgNachMandateServiceUnderTest;

    @Mock
    private CommonService mockCommonService;
    @Mock
    private ILenderAPIGateway mockLenderAPIGateway;
    @Mock
    private ObjectMapper mockObjectMapper;
    @Mock
    private EnachHandler mockEnachHandler;
    @Mock
    private LendingApplicationKycDetailsDao mockLendingApplicationKycDetailsDao;
    @Mock
    private SmfgConfig mockSmfgConfig;
    @Mock
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Before
    public void setUp() {
    }

    @Test
    public void testInvokeNachMandate_Success() throws Exception {

        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequest.setMerchantId(12345L);
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setMerchantId(12445L);
        lendingApplication.setId(12345L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setPennyDropAccountNumber("123");
        lenderAssociationDetailsRequest.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequest.setLendingApplication(lendingApplication);

        final LendingApplicationKycDetails lendingApplicationKycDetails = new LendingApplicationKycDetails();
        lendingApplicationKycDetails.setApplicationId(0L);
        lendingApplicationKycDetails.setMerchantId(0L);
        lendingApplicationKycDetails.setLender("lender");
        lendingApplicationKycDetails.setFatherName("fathersName");
        lendingApplicationKycDetails.setEmail("emailaddress");
        when(mockLendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(anyLong(), anyString()))
                .thenReturn(lendingApplicationKycDetails);

        final MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = new MerchantNachDetailsResponseDTO();
        merchantNachDetailsResponseDTO.setBeneficiaryName("accountholdername");
        merchantNachDetailsResponseDTO.setBankName("bankName");
        merchantNachDetailsResponseDTO.setAccountNumber("123");
        merchantNachDetailsResponseDTO.setIfscCode("ifsccode");
        merchantNachDetailsResponseDTO.setAccountType("accountType");
        merchantNachDetailsResponseDTO.setStartDate(new GregorianCalendar(2020, Calendar.JANUARY, 1).getTime());
        merchantNachDetailsResponseDTO.setProviderUmrn("mandatereferenceno");
        when(mockEnachHandler.findByMerchantIdAndApplicationIdAndLender(anyLong(), anyLong(), anyString()))
                .thenReturn(merchantNachDetailsResponseDTO);

        when(mockSmfgConfig.getPartnerId()).thenReturn("partnerid");
        when(mockSmfgConfig.getDataPushApiAction()).thenReturn("apiaction");
        when(mockSmfgConfig.getCurrentAccountType()).thenReturn("result");
        when(mockSmfgConfig.getSavingAccountType()).thenReturn("result");
        when(mockSmfgConfig.getNachPlusDays()).thenReturn(0);
        when(mockSmfgConfig.getPositiveMandateFlag()).thenReturn("mandateregflag");
        when(mockSmfgConfig.getDailyInstallmentFrequency()).thenReturn("emifrequency");

        final NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO<>(true, "applicationId", "productName", "lender",
                "data", "error");
        SmfgAppPushRequest smfgAppPushRequest = new SmfgAppPushRequest();
        SmfgAppPushRequest.RepaymentDisbBankDetails repaymentDisbBankDetails = new SmfgAppPushRequest.RepaymentDisbBankDetails();
        repaymentDisbBankDetails.setAccountno("123456");
        smfgAppPushRequest.setRepaymentdisbbankdetails(repaymentDisbBankDetails);
        nbfcResponseDTO.setData(smfgAppPushRequest);

        LendingApplicationDetails lendingApplicationDetails = new LendingApplicationDetails();
        lendingApplicationDetails.setStage("PENDING");

        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(anyLong())).thenReturn(lendingApplicationDetails);

        when(mockLenderAPIGateway.invokeStage(
                any(NBFCRequestDTO.class), eq(LenderAssociationStages.NACH_MANDATE)))
                .thenReturn(nbfcResponseDTO);

        when(mockObjectMapper.writeValueAsString(any(Object.class))).thenReturn("content");

        final SmfgAppPushResponseDto smfgAppPushResponseDto = new SmfgAppPushResponseDto("SUCCESS",
                new SmfgAppPushResponseDto.Data("applicationid"), "partnerapplicationid", "status_code");
        when(mockObjectMapper.readValue("content", SmfgAppPushResponseDto.class)).thenReturn(smfgAppPushResponseDto);

        final Boolean result = smfgNachMandateServiceUnderTest.invokeNachMandate(lenderAssociationDetailsRequest);

        assertTrue(result);
        verify(mockCommonService).manageApplicationState(any(LenderAssociationDetailsRequestDto.class));
        verify(mockCommonService, never()).manageApplicationStateAndRejectApplication(any(LenderAssociationDetailsRequestDto.class));
    }

    @Test
    public void testInvokeNachMandate_AccountNoFailure() throws Exception {

        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequest.setMerchantId(12345L);
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setMerchantId(12445L);
        lendingApplication.setId(12345L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setPennyDropAccountNumber("123");
        lenderAssociationDetailsRequest.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequest.setLendingApplication(lendingApplication);

        final LendingApplicationKycDetails lendingApplicationKycDetails = new LendingApplicationKycDetails();
        lendingApplicationKycDetails.setApplicationId(0L);
        lendingApplicationKycDetails.setMerchantId(0L);
        lendingApplicationKycDetails.setLender("lender");
        lendingApplicationKycDetails.setFatherName("fathersName");
        lendingApplicationKycDetails.setEmail("emailaddress");
        when(mockLendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(anyLong(), anyString()))
                .thenReturn(lendingApplicationKycDetails);

        final MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = new MerchantNachDetailsResponseDTO();
        merchantNachDetailsResponseDTO.setBeneficiaryName("accountholdername");
        merchantNachDetailsResponseDTO.setBankName("bankName");
        merchantNachDetailsResponseDTO.setAccountNumber("accountno");
        merchantNachDetailsResponseDTO.setIfscCode("ifsccode");
        merchantNachDetailsResponseDTO.setAccountType("accountType");
        merchantNachDetailsResponseDTO.setStartDate(new GregorianCalendar(2020, Calendar.JANUARY, 1).getTime());
        merchantNachDetailsResponseDTO.setProviderUmrn("mandatereferenceno");
        when(mockEnachHandler.findByMerchantIdAndApplicationIdAndLender(anyLong(), anyLong(), anyString()))
                .thenReturn(merchantNachDetailsResponseDTO);

        when(mockSmfgConfig.getPartnerId()).thenReturn("partnerid");
        when(mockSmfgConfig.getDataPushApiAction()).thenReturn("apiaction");
        when(mockSmfgConfig.getCurrentAccountType()).thenReturn("result");
        when(mockSmfgConfig.getSavingAccountType()).thenReturn("result");
        when(mockSmfgConfig.getNachPlusDays()).thenReturn(0);
        when(mockSmfgConfig.getPositiveMandateFlag()).thenReturn("mandateregflag");
        when(mockSmfgConfig.getDailyInstallmentFrequency()).thenReturn("emifrequency");

        LendingApplicationDetails lendingApplicationDetails = new LendingApplicationDetails();
        lendingApplicationDetails.setStage("PENDING");

        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(anyLong())).thenReturn(lendingApplicationDetails);

        final NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO<>(false, "applicationId", "productName", "lender",
                "data", "error");
        SmfgAppPushRequest smfgAppPushRequest = new SmfgAppPushRequest();
        SmfgAppPushRequest.RepaymentDisbBankDetails repaymentDisbBankDetails = new SmfgAppPushRequest.RepaymentDisbBankDetails();
        repaymentDisbBankDetails.setAccountno("123");
        smfgAppPushRequest.setRepaymentdisbbankdetails(repaymentDisbBankDetails);
        nbfcResponseDTO.setData(smfgAppPushRequest);

        when(mockLenderAPIGateway.invokeStage(
                any(NBFCRequestDTO.class), eq(LenderAssociationStages.NACH_MANDATE)))
                .thenReturn(nbfcResponseDTO);

        when(mockObjectMapper.writeValueAsString(any(Object.class))).thenReturn("content");

        final SmfgAppPushResponseDto smfgAppPushResponseDto = new SmfgAppPushResponseDto("status",
                new SmfgAppPushResponseDto.Data("applicationid"), "partnerapplicationid", "status_code");
        when(mockObjectMapper.readValue("content", SmfgAppPushResponseDto.class)).thenReturn(smfgAppPushResponseDto);

        final Boolean result = smfgNachMandateServiceUnderTest.invokeNachMandate(lenderAssociationDetailsRequest);
        assertFalse(result);
    }

    @Test
    public void testInvokeNachMandate_Failure() throws Exception {
        final LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                .applicationId(0L)
                .merchantId(0L)
                .lendingApplication(new LendingApplication())
                .lendingApplicationLenderDetails(new LendingApplicationLenderDetails())
                .build();

        final LendingApplicationKycDetails lendingApplicationKycDetails = new LendingApplicationKycDetails();
        lendingApplicationKycDetails.setApplicationId(0L);
        lendingApplicationKycDetails.setMerchantId(0L);
        lendingApplicationKycDetails.setLender("lender");
        lendingApplicationKycDetails.setFatherName("fathersName");
        lendingApplicationKycDetails.setEmail("emailaddress");
        when(mockLendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(anyLong(), anyString()))
                .thenReturn(lendingApplicationKycDetails);

        final MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = new MerchantNachDetailsResponseDTO();
        merchantNachDetailsResponseDTO.setBeneficiaryName("accountholdername");
        merchantNachDetailsResponseDTO.setBankName("bankName");
        merchantNachDetailsResponseDTO.setAccountNumber("accountno");
        merchantNachDetailsResponseDTO.setIfscCode("ifsccode");
        merchantNachDetailsResponseDTO.setAccountType("accountType");
        merchantNachDetailsResponseDTO.setStartDate(new GregorianCalendar(2020, Calendar.JANUARY, 1).getTime());
        merchantNachDetailsResponseDTO.setProviderUmrn("mandatereferenceno");
        when(mockEnachHandler.findByMerchantIdAndApplicationIdAndLender(0L, 0L, "lender"))
                .thenReturn(merchantNachDetailsResponseDTO);

        when(mockSmfgConfig.getPartnerId()).thenReturn("partnerid");
        when(mockSmfgConfig.getDataPushApiAction()).thenReturn("apiaction");
        when(mockSmfgConfig.getCurrentAccountType()).thenReturn("result");
        when(mockSmfgConfig.getSavingAccountType()).thenReturn("result");
        when(mockSmfgConfig.getNachPlusDays()).thenReturn(0);
        when(mockSmfgConfig.getPositiveMandateFlag()).thenReturn("mandateregflag");
        when(mockSmfgConfig.getDailyInstallmentFrequency()).thenReturn("emifrequency");

        final NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO<>(false, "applicationId", "productName", "lender",
                "data", "error");
        when(mockLenderAPIGateway.invokeStage(
                any(NBFCRequestDTO.class), eq(LenderAssociationStages.NACH_MANDATE)))
                .thenReturn(nbfcResponseDTO);

        when(mockObjectMapper.writeValueAsString(any(Object.class))).thenReturn("content");

        final SmfgAppPushResponseDto smfgAppPushResponseDto = new SmfgAppPushResponseDto("status",
                new SmfgAppPushResponseDto.Data("applicationid"), "partnerapplicationid", "status_code");
        when(mockObjectMapper.readValue("content", SmfgAppPushResponseDto.class)).thenReturn(smfgAppPushResponseDto);

        final Boolean result = smfgNachMandateServiceUnderTest.invokeNachMandate(lenderAssociationDetailsRequest);
        assertFalse(result);
    }

    @Test
    public void testInvokeNachMandate_Exception() throws Exception {

        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequest.setMerchantId(12345L);
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setMerchantId(12445L);
        lendingApplication.setId(12345L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setPennyDropAccountNumber("123");
        lenderAssociationDetailsRequest.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequest.setLendingApplication(lendingApplication);

        final LendingApplicationKycDetails lendingApplicationKycDetails = new LendingApplicationKycDetails();
        lendingApplicationKycDetails.setApplicationId(0L);
        lendingApplicationKycDetails.setMerchantId(0L);
        lendingApplicationKycDetails.setLender("lender");
        lendingApplicationKycDetails.setFatherName("fathersName");
        lendingApplicationKycDetails.setEmail("emailaddress");
        when(mockLendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(anyLong(), anyString()))
                .thenThrow(new RuntimeException());

        final MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = new MerchantNachDetailsResponseDTO();
        merchantNachDetailsResponseDTO.setBeneficiaryName("accountholdername");
        merchantNachDetailsResponseDTO.setBankName("bankName");
        merchantNachDetailsResponseDTO.setAccountNumber("123");
        merchantNachDetailsResponseDTO.setIfscCode("ifsccode");
        merchantNachDetailsResponseDTO.setAccountType("accountType");
        merchantNachDetailsResponseDTO.setStartDate(new GregorianCalendar(2020, Calendar.JANUARY, 1).getTime());
        merchantNachDetailsResponseDTO.setProviderUmrn("mandatereferenceno");
        when(mockEnachHandler.findByMerchantIdAndApplicationIdAndLender(123L, 45L, "lender"))
                .thenReturn(merchantNachDetailsResponseDTO);

        when(mockSmfgConfig.getPartnerId()).thenReturn("partnerid");
        when(mockSmfgConfig.getDataPushApiAction()).thenReturn("apiaction");
        when(mockSmfgConfig.getCurrentAccountType()).thenReturn("result");
        when(mockSmfgConfig.getSavingAccountType()).thenReturn("result");
        when(mockSmfgConfig.getNachPlusDays()).thenReturn(0);
        when(mockSmfgConfig.getPositiveMandateFlag()).thenReturn("mandateregflag");
        when(mockSmfgConfig.getDailyInstallmentFrequency()).thenReturn("emifrequency");

        final NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO<>(true, "applicationId", "productName", "lender",
                "data", "error");
        SmfgAppPushRequest smfgAppPushRequest = new SmfgAppPushRequest();
        SmfgAppPushRequest.RepaymentDisbBankDetails repaymentDisbBankDetails = new SmfgAppPushRequest.RepaymentDisbBankDetails();
        repaymentDisbBankDetails.setAccountno("123456");
        smfgAppPushRequest.setRepaymentdisbbankdetails(repaymentDisbBankDetails);
        nbfcResponseDTO.setData(smfgAppPushRequest);

        when(mockLenderAPIGateway.invokeStage(
                any(NBFCRequestDTO.class), eq(LenderAssociationStages.NACH_MANDATE)))
                .thenReturn(nbfcResponseDTO);

        when(mockObjectMapper.writeValueAsString(any(Object.class))).thenReturn("content");
        final SmfgAppPushResponseDto smfgAppPushResponseDto = new SmfgAppPushResponseDto("SUCCESS",
                new SmfgAppPushResponseDto.Data("applicationid"), "partnerapplicationid", "status_code");
        when(mockObjectMapper.readValue("content", SmfgAppPushResponseDto.class)).thenReturn(smfgAppPushResponseDto);

        final Boolean result = smfgNachMandateServiceUnderTest.invokeNachMandate(lenderAssociationDetailsRequest);
        assertFalse(result);
    }
}