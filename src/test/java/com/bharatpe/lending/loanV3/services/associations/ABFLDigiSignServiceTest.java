package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.util.ConfigResolver;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.dto.AbflDigiSignResponseDTO;
import com.bharatpe.lending.loanV3.services.associationsV2.AbflDigiSignService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class ABFLDigiSignServiceTest {
    @Mock
    AbflDigiSignService abflDigiSignService;
    @Mock
    LendingApplicationDao lendingApplicationDao;
    @Mock
    ConfigResolver configResolver;
    @InjectMocks
    ABFLDigiSignService aBFLDigiSignService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testInvoke_success() {

        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");

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

        HashMap<String, Object> map = new HashMap<>();
        map.put("application_id", "5756732");
        when(abflDigiSignService.invokeDigiSign(anyLong(), any())).thenReturn(mockAbflDigiSignResponseDTO);
        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));

        AbflDigiSignResponseDTO result = aBFLDigiSignService.invoke(Long.valueOf(1), map);
        Assert.assertEquals(mockAbflDigiSignResponseDTO, result);
    }

    @Test
    public void testInvoke_exception() {

        HashMap<String, Object> map = new HashMap<>();
        map.put("application_id", "5756732");

        when(lendingApplicationDao.findById(any())).thenThrow(new NullPointerException("exception occured"));
        AbflDigiSignResponseDTO result = aBFLDigiSignService.invoke(Long.valueOf(1), map);
        Assert.assertEquals(null, result);
    }

    @Test
    public void testInvoke_empty_la() {

        HashMap<String, Object> map = new HashMap<>();
        map.put("application_id", "5756732");

        when(lendingApplicationDao.findById(any())).thenReturn(Optional.empty());
        AbflDigiSignResponseDTO result = aBFLDigiSignService.invoke(Long.valueOf(1), map);
        Assert.assertEquals(null, result);
    }

}