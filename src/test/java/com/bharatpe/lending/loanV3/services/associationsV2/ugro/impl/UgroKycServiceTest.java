package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroGetLeadResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;


import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class UgroKycServiceTest {

    @Mock
    LendingApplicationDao lendingApplicationDao;

    @Mock
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    CommonService commonService;

    @InjectMocks
    UgroKycService ugroKycService;

    @Value("false")
    Boolean enableLenderChange;

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(ugroKycService, "enableLenderChange", false);
    }

    @Test
    public void testProcessKycCallback() throws Exception {
        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");

        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(12323L);
        lendingApplication.setEdi(1111D);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLender("UGRO");
        lendingApplicationLenderDetails.setStage(LenderAssociationStages.KYC.name());
        lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_IN_PROGRESS.name());

        when(lendingApplicationDao.findById(anyLong())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);

        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
        getLeadResponse.setStatus("APPLICATION_APPROVED");
        UgroGetLeadResponse.ApprovedParameters approvedParameters = new UgroGetLeadResponse.ApprovedParameters();
        approvedParameters.setInstallmentAmount(1111D);
        getLeadResponse.setApprovedParameters(approvedParameters);
        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);


        ugroKycService.processKycCallback(nbfcResponseDTO);
    }

    @Test
    public void testProcessKycCallback_EmptyLA() throws Exception {
        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO(true, "600123", "LENDING", "UGRO", null, "");

        ugroKycService.processKycCallback(nbfcResponseDTO);
    }

    @Test
    public void testProcessKycCallback_EmptyLALD() throws Exception {
        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO(true, "600123", "LENDING", "UGRO", null, "");

        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = null;
        when(lendingApplicationDao.findById(anyLong())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), any(), any())).thenReturn(lendingApplicationLenderDetails);

        ugroKycService.processKycCallback(nbfcResponseDTO);
    }

    @Test
    public void testProcessKycCallback_StageMismatch() throws Exception {
        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO(true, "600123", "LENDING", "UGRO", null, "");

        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLender("UGRO");
        when(lendingApplicationDao.findById(anyLong())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);

        ugroKycService.processKycCallback(nbfcResponseDTO);
    }

    @Test
    public void testProcessKycCallback_EmptyNbfcData() throws Exception {
        UgroGetLeadResponse ugroGetLeadResponse = null;
        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");

        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(12323L);
        lendingApplication.setEdi(1111D);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLender("UGRO");
        lendingApplicationLenderDetails.setStage(LenderAssociationStages.KYC.name());
        lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_IN_PROGRESS.name());

        when(lendingApplicationDao.findById(anyLong())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);

        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
        getLeadResponse.setStatus("APPLICATION_APPROVED");
        UgroGetLeadResponse.ApprovedParameters approvedParameters = new UgroGetLeadResponse.ApprovedParameters();
        approvedParameters.setInstallmentAmount(1111D);
        getLeadResponse.setApprovedParameters(approvedParameters);
        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenThrow(new RuntimeException("Some exception has occurred!"));


        ugroKycService.processKycCallback(nbfcResponseDTO);
    }


    @Test
    public void testProcessKycCallback_Exception() throws Exception {
        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");

        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(12323L);
        lendingApplication.setEdi(1111D);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLender("UGRO");
        lendingApplicationLenderDetails.setStage(LenderAssociationStages.KYC.name());
        lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_IN_PROGRESS.name());

        when(lendingApplicationDao.findById(anyLong())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);

        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
        getLeadResponse.setStatus("APPLICATION_APPROVED");
        UgroGetLeadResponse.ApprovedParameters approvedParameters = new UgroGetLeadResponse.ApprovedParameters();
        approvedParameters.setInstallmentAmount(1111D);
        getLeadResponse.setApprovedParameters(approvedParameters);
        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenThrow(new RuntimeException("Some exception has occurred!"));


        ugroKycService.processKycCallback(nbfcResponseDTO);
    }

}






















