package com.bharatpe.lending.loanV3.services.associationsV2.smfg.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.loanV3.config.SmfgConfig;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.response.smfg.SmfgDocUploadResponseDto;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@TestConfiguration
public class SmfgDocUploadServiceTest {

    @Mock
    private LendingKfsDao lendingKfsDao;

    @Mock
    private ILenderAPIGateway lenderAPIGateway;

    @Mock
    private KycUtils kycUtils;

    @Mock
    private CommonService commonService;

    @Mock
    private DocUploadUtils docUploadUtils;

    @Mock
    private SmfgConfig smfgconfig;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SmfgDocUploadService smfgDocUploadService;

    @Test
    public void invokeDocUpload_BaseConditionFailure() {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("KYC");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setSelfieString("selfieString");
        cKycResponseDto.setPoaString("poaString");
        cKycResponseDto.setPanName("Tarsem Singh");
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(Boolean.FALSE);
        doReturn("123").when(smfgconfig).getPartnerId();

        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.SELFIE_UPLOAD));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(anyLong());
        doCallRealMethod().when(docUploadUtils).getDocName(any(String.class));
        doCallRealMethod().when(docUploadUtils).getStatusForDocumentUpload(any(DocType.class), anyString());
        doCallRealMethod().when(docUploadUtils).getFileBlob(any(DocType.class), any(CKycResponseDto.class), any(), any(), any());

        boolean result = smfgDocUploadService.invokeDocUpload(lenderAssociationDetailsRequestDto, "SELFIE");
        assertFalse(result);
    }

    @Test
    public void invokeDocUploadSelfie_Success() throws IOException {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("KYC");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setSelfieString("selfieString");
        cKycResponseDto.setPoaPdf("poaPdf");
        cKycResponseDto.setPanName("Tarsem Singh");
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(Boolean.TRUE);
        SmfgDocUploadResponseDto smfgDocUploadResponseDto = new SmfgDocUploadResponseDto();
        smfgDocUploadResponseDto.setStatus("SUCCESS");
        nbfcResponseDTO.setData(smfgDocUploadResponseDto);

        doReturn("123").when(smfgconfig).getPartnerId();
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.DOC_UPLOAD));
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgDocUploadResponseDto.class));
        doReturn(smfgDocUploadResponseDto).when(objectMapper).readValue(any(String.class), eq(SmfgDocUploadResponseDto.class));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(anyLong());
        doCallRealMethod().when(docUploadUtils).getDocName(any(String.class));
        doCallRealMethod().when(docUploadUtils).getStatusForDocumentUpload(any(DocType.class), anyString());
        doCallRealMethod().when(docUploadUtils).getFileBlob(any(DocType.class), any(CKycResponseDto.class), any(), any(), any());

        boolean result = smfgDocUploadService.invokeDocUpload(lenderAssociationDetailsRequestDto, "SELFIE");
        assertTrue(result);
        assertEquals(LenderAssociationStatus.SELFIE_UPLOAD_SUCCESS.name(), lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getKycStatus());
        verify(kycUtils, times(1)).getKycData(20000404L);
        verify(commonService, times(2)).manageApplicationState(lenderAssociationDetailsRequestDto);
    }

    @Test
    public void invokeDocUploadSelfie_ValidationFailure() {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("KYC");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setSelfieString("selfieString");
        cKycResponseDto.setPoaPdf("poaPdf");
        cKycResponseDto.setPanName("Tarsem Singh");
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(Boolean.FALSE);
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.FAILED));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(anyLong());
        doCallRealMethod().when(docUploadUtils).getStatusForDocumentUpload(any(DocType.class), anyString());
        boolean result = smfgDocUploadService.invokeDocUpload(lenderAssociationDetailsRequestDto, "SELFIE");
        assertFalse(result);
    }

    @Test
    public void invokeDocUploadSelfie_InvalidPayloadFailure() {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("KYC");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setSelfieString("selfieString");
        cKycResponseDto.setPoaPdf("poaPdf");
        cKycResponseDto.setPanName("Tarsem Singh");
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(Boolean.FALSE);
        doReturn("123").when(smfgconfig).getPartnerId();
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.SELFIE_UPLOAD));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(anyLong());
        doCallRealMethod().when(docUploadUtils).getDocName(any(String.class));
        doCallRealMethod().when(docUploadUtils).getStatusForDocumentUpload(any(DocType.class), anyString());

        boolean result = smfgDocUploadService.invokeDocUpload(lenderAssociationDetailsRequestDto, "SELFIE");
        assertFalse(result);
        assertEquals(LenderAssociationStatus.SELFIE_UPLOAD_FAILED.name(), lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getKycStatus());
        verify(kycUtils, times(1)).getKycData(20000404L);
    }

    @Test
    public void invokeDocUploadAadharPdf_Success() throws IOException {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("KYC");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setSelfieString("selfieString");
        cKycResponseDto.setPoaPdf("poaPdf");
        cKycResponseDto.setPanName("Tarsem Singh");
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(Boolean.TRUE);
        SmfgDocUploadResponseDto smfgDocUploadResponseDto = new SmfgDocUploadResponseDto();
        smfgDocUploadResponseDto.setStatus("SUCCESS");
        nbfcResponseDTO.setData(smfgDocUploadResponseDto);
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.DOC_UPLOAD));
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgDocUploadResponseDto.class));
        doReturn(smfgDocUploadResponseDto).when(objectMapper).readValue(any(String.class), eq(SmfgDocUploadResponseDto.class));

        doReturn(cKycResponseDto).when(kycUtils).getKycData(anyLong());
        doCallRealMethod().when(docUploadUtils).getDocName(any(String.class));
        doCallRealMethod().when(docUploadUtils).getStatusForDocumentUpload(any(DocType.class), anyString());
        doCallRealMethod().when(docUploadUtils).getFileBlob(any(DocType.class), any(CKycResponseDto.class), any(), any(), any());
        boolean result = smfgDocUploadService.invokeDocUpload(lenderAssociationDetailsRequestDto, "DIGILOCKER_AADHAAR_XML");

        assertTrue(result);
        assertEquals(LenderAssociationStatus.AADHAR_UPLOAD_SUCCESS.name(), lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getKycStatus());
        verify(kycUtils, times(1)).getKycData(20000404L);
        verify(commonService, times(2)).manageApplicationState(lenderAssociationDetailsRequestDto);
    }
    
    @Test
    public void invokeDocUploadAadharPdf_InvalidPayloadFailure() {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("KYC");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setSelfieString("selfieString");
        cKycResponseDto.setPoaPdf("poaPdf");
        cKycResponseDto.setPanName("Tarsem Singh");
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(Boolean.FALSE);
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.AADHAR_UPLOAD));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(anyLong());
        doCallRealMethod().when(docUploadUtils).getDocName(any(String.class));
        doCallRealMethod().when(docUploadUtils).getStatusForDocumentUpload(any(DocType.class), anyString());
        boolean result = smfgDocUploadService.invokeDocUpload(lenderAssociationDetailsRequestDto, "DIGILOCKER_AADHAAR_XML");

        assertFalse(result);
        assertEquals(LenderAssociationStatus.AADHAR_UPLOAD_FAILED.name(), lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getKycStatus());
        verify(kycUtils, times(1)).getKycData(20000404L);
    }
    
    @Test
    public void invokeDocUploadAadharPdf_APIFailure() {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("KYC");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setSelfieString("selfieString");
        cKycResponseDto.setPoaPdf("poaPdf");
        cKycResponseDto.setPanName("Tarsem Singh");
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(Boolean.FALSE);
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.AADHAR_UPLOAD));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(anyLong());
        doCallRealMethod().when(docUploadUtils).getDocName(any(String.class));
        doCallRealMethod().when(docUploadUtils).getStatusForDocumentUpload(any(DocType.class), anyString());
        doCallRealMethod().when(docUploadUtils).getFileBlob(any(DocType.class), any(CKycResponseDto.class), any(), any(), any());
        boolean result = smfgDocUploadService.invokeDocUpload(lenderAssociationDetailsRequestDto, "DIGILOCKER_AADHAAR_XML");
        assertFalse(result);
        assertEquals(LenderAssociationStatus.AADHAR_UPLOAD_FAILED.name(), lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getKycStatus());
        verify(kycUtils, times(1)).getKycData(20000404L);
    }
    
    @Test
    public void invokeDocUploadBusinessDoc_SuccessPslFlag() throws IOException {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("KYC");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setSelfieString("selfieString");
        cKycResponseDto.setPoaPdf("poaPdf");
        cKycResponseDto.setPanName("Tarsem Singh");
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(Boolean.TRUE);
        SmfgDocUploadResponseDto smfgDocUploadResponseDto = new SmfgDocUploadResponseDto();
        smfgDocUploadResponseDto.setStatus("SUCCESS");
        nbfcResponseDTO.setData(smfgDocUploadResponseDto);

        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.DOC_UPLOAD));
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgDocUploadResponseDto.class));
        doReturn(smfgDocUploadResponseDto).when(objectMapper).readValue(any(String.class), eq(SmfgDocUploadResponseDto.class));

        doReturn(cKycResponseDto).when(kycUtils).getKycData(anyLong());
        doCallRealMethod().when(docUploadUtils).getDocName(any(String.class));
        doCallRealMethod().when(docUploadUtils).getStatusForDocumentUpload(any(DocType.class), anyString());
        doCallRealMethod().when(docUploadUtils).getFileBlob(any(DocType.class), any(CKycResponseDto.class), any(), any(), any());
        boolean result = smfgDocUploadService.invokeDocUpload(lenderAssociationDetailsRequestDto, "BUSINESS_DOC");
        assertTrue(result);
    }


    @Test
    public void invokeDocUploadAuditTrail_Success() throws IOException {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("DOC_UPLOAD");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);

        LendingKfs lendingKfs = new LendingKfs();
        lendingKfs.setLender("SMFG");
        lendingKfs.setApplicationId(5756732L);
        lendingKfs.setKfsDocFile("KfsFile");
        lendingKfs.setKfsDocUrl("KfsUrl");
        lendingKfs.setKfsSignedAt(new Date());
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setPanName("Tarsem Singh");

        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(Boolean.TRUE);
        SmfgDocUploadResponseDto smfgDocUploadResponseDto = new SmfgDocUploadResponseDto();
        smfgDocUploadResponseDto.setStatus("SUCCESS");
        nbfcResponseDTO.setData(smfgDocUploadResponseDto);

        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgDocUploadResponseDto.class));
        doReturn(smfgDocUploadResponseDto).when(objectMapper).readValue(any(String.class), eq(SmfgDocUploadResponseDto.class));

        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(ArgumentMatchers.any(NBFCRequestDTO.class), eq(LenderAssociationStages.DOC_UPLOAD));
        doReturn(lendingKfs).when(lendingKfsDao).findTop1ByApplicationIdAndLenderOrderByIdDesc(anyLong(), anyString());
        doReturn(cKycResponseDto).when(kycUtils).getPanData(anyLong());
        doReturn(cKycResponseDto).when(kycUtils).getKycData(anyLong());
        doCallRealMethod().when(docUploadUtils).getDocName(any(String.class));
        doCallRealMethod().when(docUploadUtils).getStatusForDocumentUpload(any(DocType.class), anyString());
        doReturn("mockUrl").when(docUploadUtils).getFileBlob(any(DocType.class), any(), any(LendingKfs.class), any(), any());
        boolean result = smfgDocUploadService.invokeDocUpload(lenderAssociationDetailsRequestDto, "AUDIT_TRAIL_DOC");
        assertTrue(result);
        verify(lenderAPIGateway, times(1)).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.DOC_UPLOAD));
    }

    @Test
    public void invokeDocUploadAuditTrail_Failed() throws IOException {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("KYC");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setSelfieString("selfieString");
        cKycResponseDto.setPoaPdf("poaPdf");
        cKycResponseDto.setPanName("Tarsem Singh");
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(Boolean.TRUE);
        SmfgDocUploadResponseDto smfgDocUploadResponseDto = new SmfgDocUploadResponseDto();
        smfgDocUploadResponseDto.setStatus("SUCCESS");
        nbfcResponseDTO.setData(smfgDocUploadResponseDto);

        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.DOC_UPLOAD));
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgDocUploadResponseDto.class));
        doReturn(smfgDocUploadResponseDto).when(objectMapper).readValue(any(String.class), eq(SmfgDocUploadResponseDto.class));

        doReturn(cKycResponseDto).when(kycUtils).getKycData(anyLong());
        doCallRealMethod().when(docUploadUtils).getDocName(any(String.class));
        doCallRealMethod().when(docUploadUtils).getStatusForDocumentUpload(any(DocType.class), anyString());
        doCallRealMethod().when(docUploadUtils).getFileBlob(any(DocType.class), any(CKycResponseDto.class), any(), any(), any());
        boolean result = smfgDocUploadService.invokeDocUpload(lenderAssociationDetailsRequestDto, "AUDIT_TRAIL_DOC");

        assertFalse(result);
        assertEquals(LenderAssociationStatus.AUDIT_TRAIL_FAILED.name(), lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getKycStatus());
        verify(kycUtils, times(1)).getKycData(20000404L);
        verify(commonService, times(2)).manageApplicationState(lenderAssociationDetailsRequestDto);
    }
    
}
