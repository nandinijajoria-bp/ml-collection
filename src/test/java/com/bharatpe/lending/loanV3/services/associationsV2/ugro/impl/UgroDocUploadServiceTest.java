//package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;
//
//import com.bharatpe.common.entities.LendingApplication;
//import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
//import com.bharatpe.lending.common.enums.LenderAssociationStages;
//import com.bharatpe.lending.dao.LendingKfsDao;
//import com.bharatpe.lending.entity.LendingKfs;
//import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
//import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
//import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
//import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
//import com.bharatpe.lending.loanV3.services.associationsV2.ugro.validations.UgroPayloadValidation;
//import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
//import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
//import com.bharatpe.lending.loanV3.utils.KycUtils;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.test.context.junit4.SpringRunner;
//
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.when;
//
//@RunWith(SpringRunner.class)
//public class UgroDocUploadServiceTest {
//    @Mock
//    KycUtils kycUtils;
//
//    @Mock
//    CommonService commonService;
//
//    @Mock
//    ILenderAPIGateway lenderAPIGateway;
//
//    @Mock
//    LendingKfsDao lendingKfsDao;
//
//    @Mock
//    DocUploadUtils docUploadUtils;
//
//    @Mock
//    UgroPayloadValidation payloadValidation;
//
//    @InjectMocks
//    UgroDocUploadService ugroDocUploadService;
//
//    @Test
//    public void testinvokeDocUpload_Selfie() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplication.setMerchantId(20040289L);
//        CKycResponseDto cKycResponseDto = new CKycResponseDto();
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 20040289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, false);
//
//        String docType = "SELFIE_UPLOAD";
//
//        when(kycUtils.getKycData(anyLong())).thenReturn(cKycResponseDto);
//
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", "Data", "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDTO);
//
//        ugroDocUploadService.invokeDocUpload(lenderAssociationDetailsDto, docType);
//    }
//
//    @Test
//    public void testinvokeDocUpload_Aadhar() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplication.setMerchantId(20040289L);
//        CKycResponseDto cKycResponseDto = new CKycResponseDto();
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 20040289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, false);
//
//        String docType = "AADHAR_UPLOAD";
//
//        when(kycUtils.getKycData(anyLong())).thenReturn(cKycResponseDto);
//
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", "Data", "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDTO);
//
//        ugroDocUploadService.invokeDocUpload(lenderAssociationDetailsDto, docType);
//    }
//
//    @Test
//    public void testinvokeDocUpload_InvalidPayload() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplication.setMerchantId(20040289L);
//        CKycResponseDto cKycResponseDto = null;
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 20040289L, cKycResponseDto, lendingApplication, null, lendingApplicationLenderDetails, true, false);
//
//        String docType = "SELFIE_UPLOAD";
//
//        when(payloadValidation.isInValidDocUploadPayload(any())).thenReturn(true);
//
//        ugroDocUploadService.invokeDocUpload(lenderAssociationDetailsDto, docType);
//    }
//
////    @Test
////    public void testinvokeDocUpload_EmptyPayload() throws Exception {
////        LendingApplication lendingApplication = new LendingApplication();
////        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
////        lendingApplication.setMerchantId(20040289L);
////        CKycResponseDto cKycResponseDto = null;
////        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(60012L, 20040289L, cKycResponseDto, null, null, lendingApplicationLenderDetails, true, false);
////
////        String docType = "SELFIE_UPLOAD";
////
////        when(payloadValidation.isInValidDocUploadPayload(any())).thenReturn(true);
////
////        ugroDocUploadService.invokeDocUpload(lenderAssociationDetailsDto, docType);
////    }
//
//
//
//    @Test
//    public void testinvokeDocUpload_EmptyApplicationId() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplication.setMerchantId(20040289L);
//        CKycResponseDto cKycResponseDto = null;
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(null, 20040289L, cKycResponseDto, lendingApplication, null, lendingApplicationLenderDetails, true, false);
//
//        String docType = "SELFIE_UPLOAD";
//
//        when(payloadValidation.isInValidDocUploadPayload(any())).thenReturn(true);
//
//        ugroDocUploadService.invokeDocUpload(lenderAssociationDetailsDto, docType);
//    }
//
//    @Test
//    public void testinvokeDocUpload_SelfieException() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplication.setMerchantId(20040289L);
//        CKycResponseDto cKycResponseDto = new CKycResponseDto();
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 20040289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, false);
//
//        String docType = "SELFIE_UPLOAD";
//
//        when(kycUtils.getKycData(anyLong())).thenReturn(cKycResponseDto);
//
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", "Data", "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenThrow(new RuntimeException("Some exception has occurred!"));
//
//        ugroDocUploadService.invokeDocUpload(lenderAssociationDetailsDto, docType);
//    }
//
//    @Test
//    public void testinvokeDocUpload_AadharException() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplication.setMerchantId(20040289L);
//        CKycResponseDto cKycResponseDto = new CKycResponseDto();
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 20040289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, false);
//
//        String docType = "AADHAR_UPLOAD";
//
//        when(kycUtils.getKycData(anyLong())).thenReturn(cKycResponseDto);
//
//        NBFCResponseDTO<?> nbfcResponseDTO = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", "Data", "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenThrow(new RuntimeException("Some exception has occurred!"));
//
//        ugroDocUploadService.invokeDocUpload(lenderAssociationDetailsDto, docType);
//    }
//
//    @Test
//    public void testInvokeAdditionalDocUpload() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        String docType = "KEY_FACT_STATEMENT_LOAN_AGREEMENT_MERGED";
//
//        LendingKfs lendingKfs = new LendingKfs();
//        lendingKfs.setApr(122D);
//        when(lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(any(), any())).thenReturn(lendingKfs);
//
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", "null", "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
//
//        ugroDocUploadService.invokeAdditionalDocUpload(lendingApplication, lendingApplicationLenderDetails, docType);
//    }
//
//    @Test
//    public void testInvokeAdditionalDocUpload_EmptyRequest() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        String docType = "KEY_FACT_STATEMENT_LOAN_AGREEMENT_MERGED";
//
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto(600123L, 200129L, null, lendingApplication, null, lendingApplicationLenderDetails, true, false);
//
//        ugroDocUploadService.invokeAdditionalDocUpload(lendingApplication, lendingApplicationLenderDetails, docType);
//    }
//
//    @Test
//    public void testInvokeAdditionalDocUpload_EmptyKFS() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        String docType = "KEY_FACT_STATEMENT_LOAN_AGREEMENT_MERGED";
//
//        ugroDocUploadService.invokeAdditionalDocUpload(lendingApplication, lendingApplicationLenderDetails, docType);
//    }
//
//
//    @Test
//    public void testInvokeAdditionalDocUpload_Exception() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        String docType = "KEY_FACT_STATEMENT_LOAN_AGREEMENT_MERGED";
//
//        LendingKfs lendingKfs = new LendingKfs();
//        lendingKfs.setApr(122D);
//        when(lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(any(), any())).thenReturn(lendingKfs);
//
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", "null", "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenThrow(new RuntimeException("Some exception has occurred!"));
//
//        ugroDocUploadService.invokeAdditionalDocUpload(lendingApplication, lendingApplicationLenderDetails, docType);
//    }
//
//}
