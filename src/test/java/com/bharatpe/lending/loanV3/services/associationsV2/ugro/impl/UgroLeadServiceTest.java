package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroCreateLeadResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.ugro.validations.UgroPayloadValidation;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class UgroLeadServiceTest {
    @Mock
    KycUtils kycUtils;

    @Mock
    UgroPayloadValidation payloadValidation;

    @Mock
    CommonService commonService;

    @Mock
    ILenderAPIGateway lenderAPIGateway;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    UgroConfig ugroConfig;

    @Mock
    ConverterUtils converterUtils;

    @Mock
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @InjectMocks
    UgroLeadService ugroLeadService;

    @Test
    public void testInvokeCreateLead() throws Exception{
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setPincode(500110L);
        lendingApplication.setProcessingFee(100.20D);
        lendingApplication.setLoanAmount(23232D);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();

        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setAddress("377, gali Number 6, Akbarpur, Baharampur, Ghaziabad");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(6100123L, 20040289L, cKycResponseDto, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        when(lendingRiskVariablesSnapshotDao.findByApplicationId(any())).thenReturn(lendingRiskVariablesSnapshot);

        when(converterUtils.parseData(cKycResponseDto.getAddress())).thenReturn("377, gali Number 6, Akbarpur, Baharampur, Ghaziabad");

        UgroCreateLeadResponse ugroCreateLeadResponse = new UgroCreateLeadResponse();
        NBFCResponseDTO<?> initialResponse = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO",ugroCreateLeadResponse, "");
        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(initialResponse);

        UgroCreateLeadResponse createLeadResponse = new UgroCreateLeadResponse();
        createLeadResponse.setLeadId("LD123");
        when(objectMapper.convertValue(any(), eq(UgroCreateLeadResponse.class))).thenReturn(createLeadResponse);

        ugroLeadService.invokeCreateLead(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeCreateLead_EmptyLA() throws Exception{
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(6100123L, 20040289L, null, null, null, null, true, true);

        ugroLeadService.invokeCreateLead(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeCreateLead_InvalidPayload() throws Exception{
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setName("");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(6100123L, 20040289L, cKycResponseDto, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        when(payloadValidation.isInvalidCreateLeadCKycData(any())).thenReturn(true);

        ugroLeadService.invokeCreateLead(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeCreateLead_EmptyCreateLeadResponse() throws Exception{
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();

        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(6100123L, 20040289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        ugroLeadService.invokeCreateLead(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeCreateLead_AddressSize1() throws Exception{
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setPincode(500110L);
        lendingApplication.setProcessingFee(100.20D);
        lendingApplication.setLoanAmount(23232D);
        lendingApplication.setArea("area asdfas df asd fas df asd fa sdf as dfa sdf as df asdf");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();

        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setAddress("377, gali Number 6, Akbarpur, Baharampur, Ghaziabad");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(6100123L, 20040289L, cKycResponseDto, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        when(lendingRiskVariablesSnapshotDao.findByApplicationId(any())).thenReturn(lendingRiskVariablesSnapshot);

        when(converterUtils.parseData(cKycResponseDto.getAddress())).thenReturn("377 Ghaziabad");

        ugroLeadService.invokeCreateLead(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeCreateLead_AddressSize2() throws Exception{
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setPincode(500110L);
        lendingApplication.setProcessingFee(100.20D);
        lendingApplication.setLoanAmount(23232D);
        lendingApplication.setArea("area asdfas df asd fas df as asd fa sd fas df asd fas df as dfa sd fas df asdf asd fas df as dfd fa sdf as dfa sdf as df asdf");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();

        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setAddress("377, gali Number 6, Akbarpur, Baharampur, Ghaziabad");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(6100123L, 20040289L, cKycResponseDto, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        when(lendingRiskVariablesSnapshotDao.findByApplicationId(any())).thenReturn(lendingRiskVariablesSnapshot);

        when(converterUtils.parseData(cKycResponseDto.getAddress())).thenReturn("377, gali Number 6fasdfasd fas d fas dfa sd as df a sd fa sd f as df, Akbarpur, Baharampur, Ghaziabad");

        ugroLeadService.invokeCreateLead(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeCreateLead_AddressSize3() throws Exception{
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setPincode(500110L);
        lendingApplication.setProcessingFee(100.20D);
        lendingApplication.setLoanAmount(23232D);
        lendingApplication.setArea("area asdfas df asd fas df as asd fa sd fas df asd fas df as dfa sd fas df asdf asd fas df as dfd fa sdf as dfa sdf as df asdf");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();

        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setAddress("377, gali Number 6, Akbarpur, Baharampur, Ghaziabad");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(6100123L, 20040289L, cKycResponseDto, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        when(lendingRiskVariablesSnapshotDao.findByApplicationId(any())).thenReturn(lendingRiskVariablesSnapshot);

        when(converterUtils.parseData(cKycResponseDto.getAddress())).thenReturn("377, gali Number 6fasdfasd fas d fas dfa sd as df a asd fa sd fas df as df asd fas df asd fas df as df asd fas df asd fa sd fas df asdfasdfasdfassd fas d fas d fas df asd f asd fas df as df asd fas df as df as dfa sdf as df  df as dfa sd fa sd fas dfsd fa sd f as df, Akbarpur, Baharampur, Ghaziabad");

        ugroLeadService.invokeCreateLead(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeCreateLead_DedupeError() throws Exception{
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setPincode(500110L);
        lendingApplication.setProcessingFee(100.20D);
        lendingApplication.setLoanAmount(23232D);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();

        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setAddress("377, gali Number 6, Akbarpur, Baharampur, Ghaziabad");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(6100123L, 20040289L, cKycResponseDto, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        when(lendingRiskVariablesSnapshotDao.findByApplicationId(any())).thenReturn(lendingRiskVariablesSnapshot);

        when(converterUtils.parseData(cKycResponseDto.getAddress())).thenReturn("377, gali Number 6, Akbarpur, Baharampur, Ghaziabad");

        UgroCreateLeadResponse ugroCreateLeadResponse = new UgroCreateLeadResponse();
        NBFCResponseDTO<?> initialResponse = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO",ugroCreateLeadResponse, HttpStatus.BAD_REQUEST.toString());
        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(initialResponse);

        UgroCreateLeadResponse createLeadResponse = new UgroCreateLeadResponse();
        createLeadResponse.setLeadId("LD123");
        createLeadResponse.setErr("DEDUPUE");
        when(objectMapper.convertValue(any(), eq(UgroCreateLeadResponse.class))).thenReturn(createLeadResponse);

        ugroLeadService.invokeCreateLead(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeCreateLead_Exception() throws Exception{
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setPincode(500110L);
        lendingApplication.setProcessingFee(100.20D);
        lendingApplication.setLoanAmount(23232D);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();

        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setAddress("377, gali Number 6, Akbarpur, Baharampur, Ghaziabad");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(6100123L, 20040289L, cKycResponseDto, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        when(lendingRiskVariablesSnapshotDao.findByApplicationId(any())).thenReturn(lendingRiskVariablesSnapshot);

        when(converterUtils.parseData(cKycResponseDto.getAddress())).thenReturn("377, gali Number 6, Akbarpur, Baharampur, Ghaziabad");

        UgroCreateLeadResponse ugroCreateLeadResponse = new UgroCreateLeadResponse();
        NBFCResponseDTO<?> initialResponse = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO",ugroCreateLeadResponse, "");
        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(initialResponse);

        UgroCreateLeadResponse createLeadResponse = new UgroCreateLeadResponse();
        createLeadResponse.setLeadId("LD123");
        when(objectMapper.convertValue(any(), eq(UgroCreateLeadResponse.class))).thenThrow(new RuntimeException("Some exception has occurred!"));

        ugroLeadService.invokeCreateLead(lenderAssociationDetailsDto);
    }
}
