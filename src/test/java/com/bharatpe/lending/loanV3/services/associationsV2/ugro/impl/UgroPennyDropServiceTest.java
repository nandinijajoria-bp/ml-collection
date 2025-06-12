//package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;
//
//import com.bharatpe.common.entities.LendingApplication;
//import com.bharatpe.lending.common.Handler.EnachHandler;
//import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
//import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
//import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
//import com.bharatpe.lending.common.entity.LendingApplicationDetails;
//import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
//import com.bharatpe.lending.common.enums.LenderAssociationStages;
//import com.bharatpe.lending.common.enums.LenderAssociationStatus;
//import com.bharatpe.lending.common.enums.Status;
//import com.bharatpe.lending.dao.LendingApplicationDao;
//import com.bharatpe.lending.loanV3.config.UgroConfig;
//import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
//import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
//import com.bharatpe.lending.loanV3.dto.response.ugro.UgroGetLeadResponse;
//import com.bharatpe.lending.loanV3.dto.response.ugro.UgroPennyDropResponse;
//import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
//import com.bharatpe.lending.loanV3.services.associationsV2.ugro.validations.UgroPayloadValidation;
//import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
//import com.bharatpe.lending.util.LoanUtil;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.test.context.junit4.SpringRunner;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.util.Optional;
//
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.when;
//
//@RunWith(SpringRunner.class)
//public class UgroPennyDropServiceTest {
//    @Mock
//    UgroConfig ugroConfig;
//
//    @Mock
//    ObjectMapper objectMapper;
//
//    @Mock
//    CommonService commonService;
//
//    @Mock
//    LendingApplicationDao lendingApplicationDao;
//
//    @Mock
//    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
//
//    @Mock
//    ILenderAPIGateway lenderAPIGateway;
//
//    @Mock
//    LendingApplicationDetailsDao lendingApplicationDetailsDao;
//
//    @Mock
//    EnachHandler enachHandler;
//
//    @Mock
//    LoanUtil loanUtil;
//
//    @Mock
//    UgroPayloadValidation payloadValidation;
//
//    @Value("${lender.change.enabled:false}")
//    Boolean enableLenderChange;
//
//    @InjectMocks
//    UgroPennyDropService ugroPennyDropService;
//
//    @Before
//    public void setUp() {
//        ReflectionTestUtils.setField(ugroPennyDropService, "enableLenderChange", false);
//    }
//
//    @Test
//    public void testInvokePennyDrop() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.PENNY_DROP_PENDING.name());
//        lendingApplicationLenderDetails.setLeadId("LD123");
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 200128L, null, lendingApplication, null, lendingApplicationLenderDetails, false, false);
//
//        LendingApplicationDetails lendingApplicationDetails = new LendingApplicationDetails();
//        lendingApplicationDetails.setIsNachSkip(true);
//        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(anyLong())).thenReturn(lendingApplicationDetails);
//
//        MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = new MerchantNachDetailsResponseDTO();
//        when(enachHandler.findByMerchantIdAndApplicationIdAndLender(any(), any(), any())).thenReturn(merchantNachDetailsResponseDTO);
//
//        UgroPennyDropResponse ugroPennyDropResponse = new UgroPennyDropResponse();
//        ugroPennyDropResponse.setLeadId("LD123");
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroPennyDropResponse, "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
//
//        UgroPennyDropResponse pennyDropResponse = new UgroPennyDropResponse();
//        pennyDropResponse.setLeadId("LD123");
//        when(objectMapper.convertValue(any(), eq(UgroPennyDropResponse.class))).thenReturn(pennyDropResponse);
//
//        ugroPennyDropService.invokePennyDrop(lenderAssociationDetailsDto);
//    }
//
//    @Test
//    public void testInvokePennyDrop_EmptyPayload() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.PENNY_DROP_PENDING.name());
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 200128L, null, lendingApplication, null, lendingApplicationLenderDetails, false, false);
//
//        ugroPennyDropService.invokePennyDrop(lenderAssociationDetailsDto);
//    }
//
//    @Test
//    public void testInvokePennyDrop_InvalidPayload() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.PENNY_DROP_PENDING.name());
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 200128L, null, lendingApplication, null, lendingApplicationLenderDetails, false, false);
//
//        LendingApplicationDetails lendingApplicationDetails = new LendingApplicationDetails();
//        lendingApplicationDetails.setIsNachSkip(true);
//        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(anyLong())).thenReturn(lendingApplicationDetails);
//
//        MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = new MerchantNachDetailsResponseDTO();
//        when(enachHandler.findByMerchantIdAndApplicationIdAndLender(any(), any(), any())).thenReturn(null);
//
//        when(payloadValidation.isInvalidUpdateLeadPayload(any())).thenReturn(true);
//
//        ugroPennyDropService.invokePennyDrop(lenderAssociationDetailsDto);
//    }
//
//    @Test
//    public void testInvokePennyDrop_Exception() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.PENNY_DROP_PENDING.name());
//        lendingApplicationLenderDetails.setLeadId("LD123");
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 200128L, null, lendingApplication, null, lendingApplicationLenderDetails, false, false);
//
//        LendingApplicationDetails lendingApplicationDetails = new LendingApplicationDetails();
//        lendingApplicationDetails.setIsNachSkip(true);
//        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(anyLong())).thenReturn(lendingApplicationDetails);
//
//        MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = new MerchantNachDetailsResponseDTO();
//        when(enachHandler.findByMerchantIdAndApplicationIdAndLender(any(), any(), any())).thenReturn(merchantNachDetailsResponseDTO);
//
//        UgroPennyDropResponse ugroPennyDropResponse = new UgroPennyDropResponse();
//        ugroPennyDropResponse.setLeadId("LD123");
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroPennyDropResponse, "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
//
//        UgroPennyDropResponse pennyDropResponse = new UgroPennyDropResponse();
//        pennyDropResponse.setLeadId("LD123");
//        when(objectMapper.convertValue(any(), eq(UgroPennyDropResponse.class))).thenThrow(new RuntimeException("Some exception has occurred!"));
//
//        ugroPennyDropService.invokePennyDrop(lenderAssociationDetailsDto);
//    }
//
//    @Test
//    public void testProcessCallback() throws Exception {
//        when(ugroConfig.getClosedResponse()).thenReturn("CLOSED");
//        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
//        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");
//
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
//
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLender("UGRO");
//        lendingApplicationLenderDetails.setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name());
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
//        getLeadResponse.setStatus("empty");
//        getLeadResponse.setBankAccountVerification("SUCCESS");
//        getLeadResponse.setBusinessProofVerification("SUCCESS");
//        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);
//
//
//        ugroPennyDropService.processCallback(nbfcResponseDTO);
//    }
//
//    @Test
//    public void testProcessCallback_EmptyLA() throws Exception {
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", null, "");
//
//        LendingApplication lendingApplication = null;
//        when(lendingApplicationDao.findById(any())).thenReturn(Optional.ofNullable(lendingApplication));
//
//        ugroPennyDropService.processCallback(nbfcResponseDTO);
//    }
//
//    @Test
//    public void testProcessCallback_EmptyLALD() throws Exception {
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", null, "");
//
//        LendingApplication lendingApplication = new LendingApplication();
//        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
//
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = null;
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//
//        ugroPennyDropService.processCallback(nbfcResponseDTO);
//    }
//
//    @Test
//    public void testProcessCallback_NotPennyDropStatus() throws Exception {
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", null, "");
//
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
//
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLender("UGRO");
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//
//        ugroPennyDropService.processCallback(nbfcResponseDTO);
//    }
//
//    @Test
//    public void testProcessCallback_EmptyGetLeadResponse() throws Exception {
//        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");
//
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
//
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLender("UGRO");
//        lendingApplicationLenderDetails.setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name());
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        UgroGetLeadResponse getLeadResponse = null;
//        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);
//
//        ugroPennyDropService.processCallback(nbfcResponseDTO);
//    }
//
//    @Test
//    public void testProcessCallback_ClosedLoan() throws Exception {
//        when(ugroConfig.getClosedResponse()).thenReturn("CLOSED");
//        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");
//
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
//
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLender("UGRO");
//        lendingApplicationLenderDetails.setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name());
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
//        getLeadResponse.setStatus("CLOSED");
//        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);
//
//
//
//        ugroPennyDropService.processCallback(nbfcResponseDTO);
//    }
//
//    @Test
//    public void testProcessCallback_UdyamSuccess() throws Exception {
//        when(ugroConfig.getClosedResponse()).thenReturn("CLOSED");
//        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
//        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");
//
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
//
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLender("UGRO");
//        lendingApplicationLenderDetails.setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name());
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
//        getLeadResponse.setStatus("empty");
//        getLeadResponse.setBankAccountVerification("SUCCESS");
//        getLeadResponse.setBusinessProofVerification("SUCCESS");
//        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);
//
//
//        ugroPennyDropService.processCallback(nbfcResponseDTO);
//    }
//
//    @Test
//    public void testProcessCallback_UdyamSuccess2() throws Exception {
//        when(ugroConfig.getClosedResponse()).thenReturn("CLOSED");
//        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
//        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");
//
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
//
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLender("UGRO");
//        lendingApplicationLenderDetails.setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name());
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
//        getLeadResponse.setKybRemarks(new UgroGetLeadResponse.KYBRemarks("SUCCESS", "SUCCESS"));
//        getLeadResponse.setStatus("empty");
//        getLeadResponse.setBankAccountVerification("SUCCESS");
//        getLeadResponse.setBusinessProofVerification("PENDING");
//        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);
//
//
//        ugroPennyDropService.processCallback(nbfcResponseDTO);
//    }
//
//    @Test
//    public void testProcessCallback_UdyamPending() throws Exception {
//        when(ugroConfig.getClosedResponse()).thenReturn("CLOSED");
//        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
//        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");
//
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
//
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLender("UGRO");
//        lendingApplicationLenderDetails.setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name());
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
//        getLeadResponse.setKybRemarks(null);
//        getLeadResponse.setStatus("empty");
//        getLeadResponse.setBankAccountVerification("SUCCESS");
//        getLeadResponse.setBusinessProofVerification("PENDING");
//        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);
//
//
//        ugroPennyDropService.processCallback(nbfcResponseDTO);
//    }
//
//    @Test
//    public void testProcessCallback_Exception() throws Exception {
//        when(ugroConfig.getClosedResponse()).thenReturn("CLOSED");
//        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
//        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");
//
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
//
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLender("UGRO");
//        lendingApplicationLenderDetails.setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name());
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
//        getLeadResponse.setStatus("empty");
//        getLeadResponse.setBankAccountVerification("SUCCESS");
//        getLeadResponse.setBusinessProofVerification("SUCCESS");
//        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenThrow(new RuntimeException("Some exception has occurred!"));
//
//
//        ugroPennyDropService.processCallback(nbfcResponseDTO);
//    }
//}
//
//
//
//
//
//
//
//
//
//
//
