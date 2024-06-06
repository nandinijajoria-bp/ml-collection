package com.bharatpe.lending.loanV3.services.associationsV2;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.loanV3.dto.AbflDigiSignResponseDTO;
import com.bharatpe.lending.loanV3.dto.AbflDigiSignStatusResponseDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.bharatpe.lending.loanV3.services.gateway.AbflApiGateway;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Optional;

import static org.mockito.Mockito.*;

public class AbflDigiSignServiceTest {
    @Mock
    LendingApplicationDao lendingApplicationDao;
    @Mock
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Mock
    LenderGatewayFactory lenderGatewayFactory;
    @Mock
    LendingKfsDao lendingKfsDao;
    @Mock
    MerchantService merchantService;
    @Spy
    ObjectMapper objectMapper;
    @Mock
    DocUploadUtils docUploadUtils;

    @Mock
    INbfcLenderGateway iNbfcLenderGateway;

    @InjectMocks
    AbflDigiSignService abflDigiSignService;

    @Mock
    AbflApiGateway abflApiGateway;




    @Before
    public void setUp() {MockitoAnnotations.initMocks(this);}

    @Test
    public void testInvokeDigiSign() {

        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("ABFL");


        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);


        MerchantDetailsDto merchantDetailsDto = new MerchantDetailsDto();
        BasicDetailsDto basicDetailsDto = new BasicDetailsDto();
        basicDetailsDto.setId(1231L);
        basicDetailsDto.setMobile("919877XXXXXX");
        merchantDetailsDto.setMerchantDetail(basicDetailsDto);
        AbflDigiSignResponseDTO abflDigiSignResponseDTO = new AbflDigiSignResponseDTO();
        AbflDigiSignResponseDTO.RpsResponse response = new AbflDigiSignResponseDTO.RpsResponse();
        AbflDigiSignResponseDTO.SuccessMessage successMessage = new AbflDigiSignResponseDTO.SuccessMessage();
        AbflDigiSignResponseDTO.ResponseData responseData = new AbflDigiSignResponseDTO.ResponseData();
        responseData.setSuccess_message(successMessage);
        responseData.setPdf_request_type("THREE_DIFFERENT_FILES");
        successMessage.setMessage("Request created");
        response.setData(responseData);
        response.setResponseStatus("SUCCESS");
        abflDigiSignResponseDTO.setLender("ABFL");
        abflDigiSignResponseDTO.setApplicationId(5756732L);
        abflDigiSignResponseDTO.setSuccess(true);
        abflDigiSignResponseDTO.setData(response);

        AbflDigiSignResponseDTO mockAbflDigiSignResponseDTO = new AbflDigiSignResponseDTO();
        AbflDigiSignResponseDTO.RpsResponse mockResponse = new AbflDigiSignResponseDTO.RpsResponse();
        AbflDigiSignResponseDTO.SuccessMessage mockSuccessMessage = new AbflDigiSignResponseDTO.SuccessMessage();
        AbflDigiSignResponseDTO.ResponseData mockResponseData = new AbflDigiSignResponseDTO.ResponseData();
        mockResponseData.setSuccess_message(mockSuccessMessage);
        mockResponseData.setPdf_request_type("THREE_DIFFERENT_FILES");
        mockSuccessMessage.setMessage("Request created");
        mockResponse.setData(mockResponseData);
        mockResponse.setResponseStatus("SUCCESS");
        mockAbflDigiSignResponseDTO.setLender("ABFL");
        mockAbflDigiSignResponseDTO.setApplicationId(5756732L);
        mockAbflDigiSignResponseDTO.setSuccess(true);
        mockAbflDigiSignResponseDTO.setData(mockResponse);


        LendingKfs lendingKfs = new LendingKfs();
        lendingKfs.setLender("ABFL");
        lendingKfs.setApplicationId(5756732L);
        lendingKfs.setKfsDocUrl("http://abcd");
        lendingKfs.setSanctionLoanAgreementDocUrl("http://abcd");


        when(lendingApplicationDao.save(any())).thenReturn(null);
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString())).thenReturn(lendingApplicationLenderDetails);
        when(lendingApplicationLenderDetailsDao.save(any())).thenReturn(null);
        when(lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(anyLong())).thenReturn(lendingKfs);
        when(lendingKfsDao.save(any())).thenReturn(null);
        when(merchantService.fetchMerchantDetails(anyLong())).thenReturn(merchantDetailsDto);
        when(lenderGatewayFactory.getLenderApiGateway("ABFL")).thenReturn(abflApiGateway);
        when(abflApiGateway.invokeDigiSign(any())).thenReturn(abflDigiSignResponseDTO);
        when(iNbfcLenderGateway.invokeDigiSign(any())).thenReturn(abflDigiSignResponseDTO);

        AbflDigiSignResponseDTO result = abflDigiSignService.invokeDigiSign(Long.valueOf(1), lendingApplication);
        Assert.assertEquals(mockAbflDigiSignResponseDTO.getData().getResponseStatus(), result.getData().getResponseStatus());
    }

    @Test
    public void testInvokeDigiSign_failure() {

        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("ABFL");

        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);

        MerchantDetailsDto merchantDetailsDto = new MerchantDetailsDto();
        BasicDetailsDto basicDetailsDto = new BasicDetailsDto();
        basicDetailsDto.setId(1231L);
        basicDetailsDto.setMobile("919877XXXXXX");
        merchantDetailsDto.setMerchantDetail(basicDetailsDto);
        AbflDigiSignResponseDTO abflDigiSignResponseDTO = new AbflDigiSignResponseDTO();
        AbflDigiSignResponseDTO.RpsResponse response = new AbflDigiSignResponseDTO.RpsResponse();
        AbflDigiSignResponseDTO.SuccessMessage successMessage = new AbflDigiSignResponseDTO.SuccessMessage();
        AbflDigiSignResponseDTO.ResponseData responseData = new AbflDigiSignResponseDTO.ResponseData();
        responseData.setSuccess_message(successMessage);
        responseData.setPdf_request_type("ALREADY_MERGED_FILE");
        successMessage.setMessage("There is already a request pending");
        successMessage.setStatus("PENDING");
        response.setData(responseData);
        response.setResponseStatus("SUCCESS");
        abflDigiSignResponseDTO.setLender("ABFL");
        abflDigiSignResponseDTO.setApplicationId(5756732L);
        abflDigiSignResponseDTO.setSuccess(true);
        abflDigiSignResponseDTO.setData(response);

        AbflDigiSignResponseDTO mockAbflDigiSignResponseDTO = new AbflDigiSignResponseDTO();
        AbflDigiSignResponseDTO.RpsResponse mockResponse = new AbflDigiSignResponseDTO.RpsResponse();
        AbflDigiSignResponseDTO.SuccessMessage mockSuccessMessage = new AbflDigiSignResponseDTO.SuccessMessage();
        AbflDigiSignResponseDTO.ResponseData mockResponseData = new AbflDigiSignResponseDTO.ResponseData();
        responseData.setSuccess_message(mockSuccessMessage);
        responseData.setPdf_request_type("THREE_DIFFERENT_FILES");
        mockSuccessMessage.setMessage("Request created");
        mockSuccessMessage.setStatus("PENDING");
        mockResponse.setData(mockResponseData);
        mockResponse.setResponseStatus("SUCCESS");
        mockAbflDigiSignResponseDTO.setLender("ABFL");
        mockAbflDigiSignResponseDTO.setApplicationId(5756732L);
        mockAbflDigiSignResponseDTO.setSuccess(true);
        mockAbflDigiSignResponseDTO.setData(mockResponse);


        LendingKfs lendingKfs = new LendingKfs();
        lendingKfs.setLender("ABFL");
        lendingKfs.setApplicationId(5756732L);
        lendingKfs.setKfsDocUrl("http://abcd");
        lendingKfs.setSanctionLoanAgreementDocUrl("http://abcd");


        when(lendingApplicationDao.save(any())).thenReturn(null);
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString())).thenReturn(lendingApplicationLenderDetails);
        when(lendingApplicationLenderDetailsDao.save(any())).thenReturn(null);
        when(lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(anyLong())).thenReturn(lendingKfs);
        when(lendingKfsDao.save(any())).thenReturn(null);
        when(merchantService.fetchMerchantDetails(anyLong())).thenReturn(merchantDetailsDto);
        when(lenderGatewayFactory.getLenderApiGateway("ABFL")).thenReturn(abflApiGateway);
        when(abflApiGateway.invokeDigiSign(any())).thenReturn(abflDigiSignResponseDTO);
        when(iNbfcLenderGateway.invokeDigiSign(any())).thenReturn(abflDigiSignResponseDTO);

        AbflDigiSignResponseDTO result = abflDigiSignService.invokeDigiSign(Long.valueOf(1), lendingApplication);
        Assert.assertEquals(mockAbflDigiSignResponseDTO.getData().getResponseStatus(), result.getData().getResponseStatus());
    }


    @Test
    public void testProcessDigitalSignCallback_success() throws Exception {

        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("ABFL");


        AbflDigiSignStatusResponseDTO abflDigiSignStatusResponseDTO = new AbflDigiSignStatusResponseDTO();
        AbflDigiSignStatusResponseDTO.ResponseData responseData1 = new AbflDigiSignStatusResponseDTO.ResponseData();
        responseData1.setShortUrl("http://asbbc");
        responseData1.setStatus("success");
        responseData1.setAccountId("1234");
        abflDigiSignStatusResponseDTO.setData(responseData1);
        abflDigiSignStatusResponseDTO.setResponseStatus("SUCCESS");



        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setLender("ABFL");
        nbfcResponseDTO.setSuccess(true);
        nbfcResponseDTO.setApplicationId("123123");
        nbfcResponseDTO.setProductName("LENDING");
        nbfcResponseDTO.setData(abflDigiSignStatusResponseDTO);

        when(lendingApplicationDao.save(any())).thenReturn(null);
        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString())).thenReturn(lendingApplicationLenderDetails);
        when(lendingApplicationLenderDetailsDao.save(any())).thenReturn(null);
        when(lendingApplicationLenderDetailsDao.findById(any())).thenReturn(Optional.of(lendingApplicationLenderDetails));
        when(lendingKfsDao.findById(any())).thenReturn(null);
        doNothing().when(docUploadUtils).saveESignedDocs(any(), anyString(), anyString());
        Boolean result = abflDigiSignService.processDigitalSignCallback(nbfcResponseDTO);
        Assert.assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testProcessDigitalSignCallback_no_lald() throws Exception {

        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");

        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setLender("ABFL");
        nbfcResponseDTO.setSuccess(true);
        nbfcResponseDTO.setApplicationId("123123");
        nbfcResponseDTO.setProductName("LENDING");

        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString())).thenReturn(null);
        Boolean result = abflDigiSignService.processDigitalSignCallback(nbfcResponseDTO);
        Assert.assertEquals(Boolean.FALSE, result);
    }
    @Test
    public void testProcessDigitalSignCallback_no_la() throws Exception {

        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setLender("ABFL");
        nbfcResponseDTO.setSuccess(true);
        nbfcResponseDTO.setApplicationId("123123");
        nbfcResponseDTO.setProductName("LENDING");

        when(lendingApplicationDao.findById(any())).thenReturn(Optional.empty());

        Boolean result = abflDigiSignService.processDigitalSignCallback(nbfcResponseDTO);
        Assert.assertEquals(Boolean.FALSE, result);
    }

    @Test
    public void testInvokeDigiSign_no_lendingkfs() {

        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("ABFL");

        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);

        MerchantDetailsDto merchantDetailsDto = new MerchantDetailsDto();
        BasicDetailsDto basicDetailsDto = new BasicDetailsDto();
        basicDetailsDto.setId(1231L);
        basicDetailsDto.setMobile("919877XXXXXX");
        merchantDetailsDto.setMerchantDetail(basicDetailsDto);
        AbflDigiSignResponseDTO abflDigiSignResponseDTO = new AbflDigiSignResponseDTO();
        AbflDigiSignResponseDTO.RpsResponse response = new AbflDigiSignResponseDTO.RpsResponse();
        AbflDigiSignResponseDTO.SuccessMessage successMessage = new AbflDigiSignResponseDTO.SuccessMessage();
        AbflDigiSignResponseDTO.ResponseData responseData = new AbflDigiSignResponseDTO.ResponseData();
        responseData.setSuccess_message(successMessage);
        responseData.setPdf_request_type("ALREADY_MERGED_FILE");
        successMessage.setMessage("There is already a request pending");
        successMessage.setStatus("PENDING");
        response.setData(responseData);
        response.setResponseStatus("SUCCESS");
        abflDigiSignResponseDTO.setLender("ABFL");
        abflDigiSignResponseDTO.setApplicationId(5756732L);
        abflDigiSignResponseDTO.setSuccess(true);
        abflDigiSignResponseDTO.setData(response);

        AbflDigiSignResponseDTO mockAbflDigiSignResponseDTO = new AbflDigiSignResponseDTO();
        AbflDigiSignResponseDTO.RpsResponse mockResponse = new AbflDigiSignResponseDTO.RpsResponse();
        AbflDigiSignResponseDTO.SuccessMessage mockSuccessMessage = new AbflDigiSignResponseDTO.SuccessMessage();
        AbflDigiSignResponseDTO.ResponseData mockResponseData = new AbflDigiSignResponseDTO.ResponseData();
        mockResponseData.setSuccess_message(mockSuccessMessage);
        mockResponseData.setPdf_request_type("THREE_DIFFERENT_FILES");
        mockSuccessMessage.setMessage("Request created");
        mockSuccessMessage.setStatus("PENDING");
        mockResponse.setData(mockResponseData);
        mockResponse.setResponseStatus("SUCCESS");
        mockAbflDigiSignResponseDTO.setLender("ABFL");
        mockAbflDigiSignResponseDTO.setApplicationId(5756732L);
        mockAbflDigiSignResponseDTO.setSuccess(true);
        mockAbflDigiSignResponseDTO.setData(mockResponse);



        when(lendingApplicationDao.save(any())).thenReturn(null);
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString())).thenReturn(lendingApplicationLenderDetails);
        when(lendingApplicationLenderDetailsDao.save(any())).thenReturn(null);
        when(lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(anyLong())).thenReturn(null);
        when(lendingKfsDao.save(any())).thenReturn(null);
        when(merchantService.fetchMerchantDetails(anyLong())).thenReturn(merchantDetailsDto);
        when(lenderGatewayFactory.getLenderApiGateway("ABFL")).thenReturn(abflApiGateway);
        when(abflApiGateway.invokeDigiSign(any())).thenReturn(abflDigiSignResponseDTO);
        when(iNbfcLenderGateway.invokeDigiSign(any())).thenReturn(abflDigiSignResponseDTO);

        AbflDigiSignResponseDTO result = abflDigiSignService.invokeDigiSign(Long.valueOf(1), lendingApplication);
        Assert.assertEquals(result, null);
    }

    @Test
    public void testInvokeDigiSign_no_merchant_details() {

        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");

        LendingKfs lendingKfs = new LendingKfs();
        lendingKfs.setLender("ABFL");
        lendingKfs.setApplicationId(5756732L);
        lendingKfs.setKfsDocUrl("http://abcd");
        lendingKfs.setSanctionLoanAgreementDocUrl("http://abcd");


        when(lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(anyLong())).thenReturn(lendingKfs);
        when(merchantService.fetchMerchantDetails(anyLong())).thenReturn(null);

        AbflDigiSignResponseDTO result = abflDigiSignService.invokeDigiSign(Long.valueOf(1), lendingApplication);
        Assert.assertEquals(result, null);
    }

    @Test
    public void testProcessDigitalSignCallback_failure() throws Exception {

        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("ABFL");


        AbflDigiSignStatusResponseDTO abflDigiSignStatusResponseDTO = new AbflDigiSignStatusResponseDTO();
        AbflDigiSignStatusResponseDTO.ResponseData responseData1 = new AbflDigiSignStatusResponseDTO.ResponseData();
        abflDigiSignStatusResponseDTO.setData(responseData1);
        abflDigiSignStatusResponseDTO.setResponseStatus("SUCCESS");

        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setLender("ABFL");
        nbfcResponseDTO.setSuccess(true);
        nbfcResponseDTO.setApplicationId("123123");
        nbfcResponseDTO.setProductName("LENDING");
        nbfcResponseDTO.setData(abflDigiSignStatusResponseDTO);

        when(lendingApplicationDao.save(any())).thenReturn(null);
        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString())).thenReturn(lendingApplicationLenderDetails);
        when(lendingApplicationLenderDetailsDao.save(any())).thenReturn(null);
        when(lendingApplicationLenderDetailsDao.findById(any())).thenReturn(Optional.of(lendingApplicationLenderDetails));
        when(lendingKfsDao.findById(any())).thenReturn(null);
        doNothing().when(docUploadUtils).saveESignedDocs(any(), anyString(), anyString());
        Boolean result = abflDigiSignService.processDigitalSignCallback(nbfcResponseDTO);
        Assert.assertEquals(Boolean.FALSE, result);
    }

}