package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroDisbursalResponse;
import com.bharatpe.lending.loanV3.services.associationsV2.ugro.validations.UgroPayloadValidation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class UgroDisbursalServiceTest {

    @Mock
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Mock
    UgroPayloadValidation ugroPayloadValidation;

    @Mock
    UgroConfig ugroConfig;

    @InjectMocks
    UgroDisbursalService ugroDisbursalService;

    @Test
    public void testParseCallbackResponse() throws Exception {
        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");

        UgroDisbursalResponse.Events event = new UgroDisbursalResponse.Events("type", "101", 1736246727L, null, "createdBy", 2000D, 10D, 10D, 10D, "1011011");
        List<UgroDisbursalResponse.Events> events = new ArrayList<>();
        events.add(event);
        UgroDisbursalResponse ugroDisbursalResponse = new UgroDisbursalResponse("SUCCESS", events);
        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO(true, "600123", "LENDING", "UGRO", ugroDisbursalResponse, "");

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("LD123");
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);

        ugroDisbursalService.parseCallbackResponse(nbfcResponseDTO);
    }

    @Test
    public void testParseCallbackResponse_EmptyNBFC() throws Exception {
        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");

        NBFCResponseDTO nbfcResponseDTO = null;

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("LD123");
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);

        ugroDisbursalService.parseCallbackResponse(nbfcResponseDTO);
    }

    @Test
    public void testParseCallbackResponse_InvalidReponse() throws Exception {
        UgroDisbursalResponse ugroDisbursalResponse = new UgroDisbursalResponse("SUCCESS", new ArrayList<>());
        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO(true, "600123", "LENDING", "UGRO", ugroDisbursalResponse, "");

        LendingApplicationLenderDetails lendingApplicationLenderDetails = null;
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);

        when(ugroPayloadValidation.isInvalidRejectedDisbursalResponse(any(), any())).thenReturn(true);
        ugroDisbursalService.parseCallbackResponse(nbfcResponseDTO);
    }

    @Test
    public void testParseCallbackResponse_Exception() throws Exception {
        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");

        UgroDisbursalResponse.Events event = new UgroDisbursalResponse.Events("type", "101", 1736246727L, null, "createdBy", 2000D, 10D, 10D, 10D, "1011011");
        List<UgroDisbursalResponse.Events> events = new ArrayList<>();
        events.add(event);
        UgroDisbursalResponse ugroDisbursalResponse = new UgroDisbursalResponse("SUCCESS", events);
        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO(true, "600123", "LENDING", "UGRO", ugroDisbursalResponse, "");

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("LD123");
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenThrow(new RuntimeException("Some exception has occurred!"));

        ugroDisbursalService.parseCallbackResponse(nbfcResponseDTO);
    }
}
