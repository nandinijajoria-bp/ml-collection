package com.bharatpe.lending.loanV3.services.associationsV2.smfg.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.PincodeColor;
import com.bharatpe.lending.common.enums.RiskSegment;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.PanFetchKYCResponseDto;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.config.SmfgConfig;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.smfg.SmfgCallbackRequest;
import com.bharatpe.lending.loanV3.dto.response.smfg.SmfgAppPushResponseDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Date;
import java.util.Optional;
import java.util.PriorityQueue;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doCallRealMethod;

@RunWith(SpringJUnit4ClassRunner.class)
@TestConfiguration
public class SmfgBreServiceTest {
    @Mock
    private CommonService commonService;

    @Mock
    private ILenderAPIGateway lenderAPIGateway;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LendingApplicationDao lendingApplicationDao;

    @Mock
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Mock
    private LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Mock
    private KycUtils kycUtils;

    @Mock
    private KycHandler kycHandler;

    @Mock
    private SmfgConfig smfgConfig;

    @Mock
    private ConverterUtils converterUtils;

    @Mock
    private MerchantService merchantService;

    @Mock
    private LendingApplicationServiceV2 lendingApplicationServiceV2;

    @InjectMocks
    private SmfgBreService smfgBreService;



    public void setup() throws NoSuchFieldException, IllegalAccessException {
        MockitoAnnotations.initMocks(this);

        Field field = SmfgBreService.class.getDeclaredField("enableLenderChange");
        field.setAccessible(true);
        field.set(smfgBreService, true);
    }


    @Test
    public void invokeBreFlow_Success() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        lendingApplication.setCreatedAt(new Date());
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setPanNumber("KIXXXXXXXH");
        cKycResponseDto.setPanName("Ankush Singh");
        cKycResponseDto.setGender("Male");
        cKycResponseDto.setDob("07/10/1999");
        cKycResponseDto.setMobile("9350XXXXXX");
        cKycResponseDto.setAddress("Khizarpura Kamoda Kurukshetra Haryana 136119");
        cKycResponseDto.setAadharNumber("123456789");
        cKycResponseDto.setSelfieAadhaarFaceMatchPer(90D);
        cKycResponseDto.setSelfieLivelinessScore(0.9D);
        cKycResponseDto.setBankBenePanNameMatchPer(0.7D);
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);
        lendingRiskVariablesSnapshot.setVintage(790L);
        lendingRiskVariablesSnapshot.setMonthlyNfi(82342347D);
        lendingRiskVariablesSnapshot.setSummaryTpv(855D);
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(true);
        SmfgAppPushResponseDto smfgAppPushResponseDto = new SmfgAppPushResponseDto();
        smfgAppPushResponseDto.setStatus("SUCCESS");
        SmfgAppPushResponseDto.Data smfgdata = new SmfgAppPushResponseDto.Data();
        smfgdata.setApplicationid("1234");
        smfgAppPushResponseDto.setData(smfgdata);
        nbfcResponseDTO.setData(smfgAppPushResponseDto);

        NameAndDobDetailsDto nameAndDobDetailsDto = new NameAndDobDetailsDto();
        nameAndDobDetailsDto.setFirstName("DILJIT");
        nameAndDobDetailsDto.setLastName("DOSANJH");
        doReturn(nameAndDobDetailsDto).when(kycUtils).getNameAndDobValues(any(),anyLong());

        doReturn("20068").when(smfgConfig).getCurrentAddressType();
        doReturn("20069").when(smfgConfig).getPermanentAddressType();
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(ArgumentMatchers.any(NBFCRequestDTO.class), eq(LenderAssociationStages.BRE));
        doReturn(lendingRiskVariablesSnapshot).when(lendingRiskVariablesSnapshotDao).findByApplicationId(eq(5756732L));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(eq(20000404L));
        doCallRealMethod().when(converterUtils).formatDecimalValue(anyDouble(), anyString());
        doCallRealMethod().when(converterUtils).parseData(anyString());
        doReturn("shop address").when(lendingApplicationServiceV2).constructShopAddress(lendingApplication);

        Optional<BankDetailsDto> bankDetailsDto = Optional.of(new BankDetailsDto());
        doReturn(bankDetailsDto).when(merchantService).fetchMerchantBankDetails(anyLong());

        PanFetchKYCResponseDto panFetchKYCResponseDto = new PanFetchKYCResponseDto();
        PanFetchKYCResponseDto.Data data = new PanFetchKYCResponseDto.Data();
        data.setId("123");
        data.setVerifiedDob("07/10/1999");
        data.setMaskedAadhaar("123456789");
        data.setIsPanNsdlVerified(true);
        panFetchKYCResponseDto.setData(data);
        doReturn(panFetchKYCResponseDto).when(kycHandler).panFetch(anyString(), anyLong());

        BusinessDocsDTO businessDocsDTO = new BusinessDocsDTO();
        businessDocsDTO.setPdfUrl("http://sampleurl.com");
        PriorityQueue<BusinessDocsDTO> businessDocsQueue = new PriorityQueue<>(
                Comparator.comparing(BusinessDocsDTO::getPdfUrl)
        );
        businessDocsQueue.add(businessDocsDTO);
        doReturn(businessDocsQueue).when(kycUtils).getBusinessDocData(anyLong(),anyString(),anyString());
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgAppPushResponseDto.class));
        doReturn(smfgAppPushResponseDto).when(objectMapper).readValue(any(String.class), eq(SmfgAppPushResponseDto.class));

        boolean result = smfgBreService.invokeBre(lenderAssociationDetailsRequestDto);
        assertTrue(result);
        assertEquals(LenderAssociationStatus.RISK_IN_PROGRESS.name(), lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getBreStatus());
        verify(commonService, times(2)).manageApplicationState(lenderAssociationDetailsRequestDto);
        verify(lenderAPIGateway, times(1)).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.BRE));
    }

    @Test
    public void invokeBreFlow_SelfieMatchFailure() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        lendingApplication.setCreatedAt(new Date());
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setPanNumber("KIXXXXXXXH");
        cKycResponseDto.setPanName("Ankush Singh");
        cKycResponseDto.setGender("Male");
        cKycResponseDto.setDob("07/10/1999");
        cKycResponseDto.setMobile("9350XXXXXX");
        cKycResponseDto.setAddress("Khizarpura Kamoda Kurukshetra Haryana 136119");
        cKycResponseDto.setAadharNumber("123456789");
        //cKycResponseDto.setSelfieAadhaarFaceMatchPer(70D);
        cKycResponseDto.setSelfieLivelinessScore(0.9D);
        cKycResponseDto.setBankBenePanNameMatchPer(0.7D);
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);
        lendingRiskVariablesSnapshot.setVintage(790L);
        lendingRiskVariablesSnapshot.setMonthlyNfi(82342347D);
        lendingRiskVariablesSnapshot.setSummaryTpv(855D);
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(true);
        SmfgAppPushResponseDto smfgAppPushResponseDto = new SmfgAppPushResponseDto();
        smfgAppPushResponseDto.setStatus("SUCCESS");
        SmfgAppPushResponseDto.Data smfgdata = new SmfgAppPushResponseDto.Data();
        smfgdata.setApplicationid("1234");
        smfgAppPushResponseDto.setData(smfgdata);
        nbfcResponseDTO.setData(smfgAppPushResponseDto);

        NameAndDobDetailsDto nameAndDobDetailsDto = new NameAndDobDetailsDto();
        nameAndDobDetailsDto.setFirstName("DILJIT");
        nameAndDobDetailsDto.setLastName("DOSANJH");
        doReturn(nameAndDobDetailsDto).when(kycUtils).getNameAndDobValues(any(),anyLong());

        doReturn("20068").when(smfgConfig).getCurrentAddressType();
        doReturn("20069").when(smfgConfig).getPermanentAddressType();
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(ArgumentMatchers.any(NBFCRequestDTO.class), eq(LenderAssociationStages.BRE));
        doReturn(lendingRiskVariablesSnapshot).when(lendingRiskVariablesSnapshotDao).findByApplicationId(eq(5756732L));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(eq(20000404L));
        doCallRealMethod().when(converterUtils).formatDecimalValue(anyDouble(), anyString());
        doCallRealMethod().when(converterUtils).parseData(anyString());
        doReturn("shop address").when(lendingApplicationServiceV2).constructShopAddress(lendingApplication);

        Optional<BankDetailsDto> bankDetailsDto = Optional.of(new BankDetailsDto());
        doReturn(bankDetailsDto).when(merchantService).fetchMerchantBankDetails(anyLong());

        PanFetchKYCResponseDto panFetchKYCResponseDto = new PanFetchKYCResponseDto();
        PanFetchKYCResponseDto.Data data = new PanFetchKYCResponseDto.Data();
        data.setId("123");
        data.setVerifiedDob("07/10/1999");
        data.setMaskedAadhaar("123456789");
        data.setIsPanNsdlVerified(true);
        panFetchKYCResponseDto.setData(data);
        doReturn(panFetchKYCResponseDto).when(kycHandler).panFetch(anyString(), anyLong());

        BusinessDocsDTO businessDocsDTO = new BusinessDocsDTO();
        businessDocsDTO.setPdfUrl("http://sampleurl.com");
        PriorityQueue<BusinessDocsDTO> businessDocsQueue = new PriorityQueue<>(
                Comparator.comparing(BusinessDocsDTO::getPdfUrl)
        );
        businessDocsQueue.add(businessDocsDTO);
        doReturn(businessDocsQueue).when(kycUtils).getBusinessDocData(anyLong(),anyString(),anyString());
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgAppPushResponseDto.class));
        doReturn(smfgAppPushResponseDto).when(objectMapper).readValue(any(String.class), eq(SmfgAppPushResponseDto.class));

        boolean result = smfgBreService.invokeBre(lenderAssociationDetailsRequestDto);
        assertFalse(result);
    }

    @Test
    public void invokeBreFlow_SelfieLivelinessFailure() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        lendingApplication.setCreatedAt(new Date());
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setPanNumber("KIXXXXXXXH");
        cKycResponseDto.setPanName("Ankush Singh");
        cKycResponseDto.setGender("Male");
        cKycResponseDto.setDob("07/10/1999");
        cKycResponseDto.setMobile("9350XXXXXX");
        cKycResponseDto.setAddress("Khizarpura Kamoda Kurukshetra Haryana 136119");
        cKycResponseDto.setAadharNumber("123456789");
        cKycResponseDto.setSelfieAadhaarFaceMatchPer(70D);
        //cKycResponseDto.setSelfieLivelinessScore(0.9D);
        cKycResponseDto.setBankBenePanNameMatchPer(0.7D);
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);
        lendingRiskVariablesSnapshot.setVintage(790L);
        lendingRiskVariablesSnapshot.setMonthlyNfi(82342347D);
        lendingRiskVariablesSnapshot.setSummaryTpv(855D);
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(true);
        SmfgAppPushResponseDto smfgAppPushResponseDto = new SmfgAppPushResponseDto();
        smfgAppPushResponseDto.setStatus("SUCCESS");
        SmfgAppPushResponseDto.Data smfgdata = new SmfgAppPushResponseDto.Data();
        smfgdata.setApplicationid("1234");
        smfgAppPushResponseDto.setData(smfgdata);
        nbfcResponseDTO.setData(smfgAppPushResponseDto);

        NameAndDobDetailsDto nameAndDobDetailsDto = new NameAndDobDetailsDto();
        nameAndDobDetailsDto.setFirstName("DILJIT");
        nameAndDobDetailsDto.setLastName("DOSANJH");
        doReturn(nameAndDobDetailsDto).when(kycUtils).getNameAndDobValues(any(),anyLong());

        doReturn("20068").when(smfgConfig).getCurrentAddressType();
        doReturn("20069").when(smfgConfig).getPermanentAddressType();
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(ArgumentMatchers.any(NBFCRequestDTO.class), eq(LenderAssociationStages.BRE));
        doReturn(lendingRiskVariablesSnapshot).when(lendingRiskVariablesSnapshotDao).findByApplicationId(eq(5756732L));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(eq(20000404L));
        doCallRealMethod().when(converterUtils).formatDecimalValue(anyDouble(), anyString());
        doCallRealMethod().when(converterUtils).parseData(anyString());
        doReturn("shop address").when(lendingApplicationServiceV2).constructShopAddress(lendingApplication);

        Optional<BankDetailsDto> bankDetailsDto = Optional.of(new BankDetailsDto());
        doReturn(bankDetailsDto).when(merchantService).fetchMerchantBankDetails(anyLong());

        PanFetchKYCResponseDto panFetchKYCResponseDto = new PanFetchKYCResponseDto();
        PanFetchKYCResponseDto.Data data = new PanFetchKYCResponseDto.Data();
        data.setId("123");
        data.setVerifiedDob("07/10/1999");
        data.setMaskedAadhaar("123456789");
        data.setIsPanNsdlVerified(true);
        panFetchKYCResponseDto.setData(data);
        doReturn(panFetchKYCResponseDto).when(kycHandler).panFetch(anyString(), anyLong());

        BusinessDocsDTO businessDocsDTO = new BusinessDocsDTO();
        businessDocsDTO.setPdfUrl("http://sampleurl.com");
        PriorityQueue<BusinessDocsDTO> businessDocsQueue = new PriorityQueue<>(
                Comparator.comparing(BusinessDocsDTO::getPdfUrl)
        );
        businessDocsQueue.add(businessDocsDTO);
        doReturn(businessDocsQueue).when(kycUtils).getBusinessDocData(anyLong(),anyString(),anyString());
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgAppPushResponseDto.class));
        doReturn(smfgAppPushResponseDto).when(objectMapper).readValue(any(String.class), eq(SmfgAppPushResponseDto.class));

        boolean result = smfgBreService.invokeBre(lenderAssociationDetailsRequestDto);
        assertFalse(result);
    }

    @Test
    public void invokeBreFlow_BankBeneMatchFailure() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        lendingApplication.setCreatedAt(new Date());
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setPanNumber("KIXXXXXXXH");
        cKycResponseDto.setPanName("Ankush Singh");
        cKycResponseDto.setGender("Male");
        cKycResponseDto.setDob("07/10/1999");
        cKycResponseDto.setMobile("9350XXXXXX");
        cKycResponseDto.setAddress("Khizarpura Kamoda Kurukshetra Haryana 136119");
        cKycResponseDto.setAadharNumber("123456789");
        cKycResponseDto.setSelfieAadhaarFaceMatchPer(70D);
        cKycResponseDto.setSelfieLivelinessScore(0.9D);
        //cKycResponseDto.setBankBenePanNameMatchPer(0.7D);
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);
        lendingRiskVariablesSnapshot.setVintage(790L);
        lendingRiskVariablesSnapshot.setMonthlyNfi(82342347D);
        lendingRiskVariablesSnapshot.setSummaryTpv(855D);
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(true);
        SmfgAppPushResponseDto smfgAppPushResponseDto = new SmfgAppPushResponseDto();
        smfgAppPushResponseDto.setStatus("SUCCESS");
        SmfgAppPushResponseDto.Data smfgdata = new SmfgAppPushResponseDto.Data();
        smfgdata.setApplicationid("1234");
        smfgAppPushResponseDto.setData(smfgdata);
        nbfcResponseDTO.setData(smfgAppPushResponseDto);

        NameAndDobDetailsDto nameAndDobDetailsDto = new NameAndDobDetailsDto();
        nameAndDobDetailsDto.setFirstName("DILJIT");
        nameAndDobDetailsDto.setLastName("DOSANJH");
        doReturn(nameAndDobDetailsDto).when(kycUtils).getNameAndDobValues(any(),anyLong());

        doReturn("20068").when(smfgConfig).getCurrentAddressType();
        doReturn("20069").when(smfgConfig).getPermanentAddressType();
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(ArgumentMatchers.any(NBFCRequestDTO.class), eq(LenderAssociationStages.BRE));
        doReturn(lendingRiskVariablesSnapshot).when(lendingRiskVariablesSnapshotDao).findByApplicationId(eq(5756732L));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(eq(20000404L));
        doCallRealMethod().when(converterUtils).formatDecimalValue(anyDouble(), anyString());
        doCallRealMethod().when(converterUtils).parseData(anyString());
        doReturn("shop address").when(lendingApplicationServiceV2).constructShopAddress(lendingApplication);

        Optional<BankDetailsDto> bankDetailsDto = Optional.of(new BankDetailsDto());
        doReturn(bankDetailsDto).when(merchantService).fetchMerchantBankDetails(anyLong());

        PanFetchKYCResponseDto panFetchKYCResponseDto = new PanFetchKYCResponseDto();
        PanFetchKYCResponseDto.Data data = new PanFetchKYCResponseDto.Data();
        data.setId("123");
        data.setVerifiedDob("07/10/1999");
        data.setMaskedAadhaar("123456789");
        data.setIsPanNsdlVerified(true);
        panFetchKYCResponseDto.setData(data);
        doReturn(panFetchKYCResponseDto).when(kycHandler).panFetch(anyString(), anyLong());

        BusinessDocsDTO businessDocsDTO = new BusinessDocsDTO();
        businessDocsDTO.setPdfUrl("http://sampleurl.com");
        PriorityQueue<BusinessDocsDTO> businessDocsQueue = new PriorityQueue<>(
                Comparator.comparing(BusinessDocsDTO::getPdfUrl)
        );
        businessDocsQueue.add(businessDocsDTO);
        doReturn(businessDocsQueue).when(kycUtils).getBusinessDocData(anyLong(),anyString(),anyString());
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgAppPushResponseDto.class));
        doReturn(smfgAppPushResponseDto).when(objectMapper).readValue(any(String.class), eq(SmfgAppPushResponseDto.class));

        boolean result = smfgBreService.invokeBre(lenderAssociationDetailsRequestDto);
        assertFalse(result);
    }

    @Test
    public void invokeBreFlow_DOBMatchFailure() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        lendingApplication.setCreatedAt(new Date());
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setPanNumber("KIXXXXXXXH");
        cKycResponseDto.setPanName("Ankush Singh");
        cKycResponseDto.setGender("Male");
        cKycResponseDto.setDob("07/10/1999");
        cKycResponseDto.setMobile("9350XXXXXX");
        cKycResponseDto.setAddress("Khizarpura Kamoda Kurukshetra Haryana 136119");
        cKycResponseDto.setAadharNumber("123456789");
        cKycResponseDto.setSelfieAadhaarFaceMatchPer(70D);
        cKycResponseDto.setSelfieLivelinessScore(0.9D);
        //cKycResponseDto.setBankBenePanNameMatchPer(0.7D);
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);
        lendingRiskVariablesSnapshot.setVintage(790L);
        lendingRiskVariablesSnapshot.setMonthlyNfi(82342347D);
        lendingRiskVariablesSnapshot.setSummaryTpv(855D);
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(true);
        SmfgAppPushResponseDto smfgAppPushResponseDto = new SmfgAppPushResponseDto();
        smfgAppPushResponseDto.setStatus("SUCCESS");
        SmfgAppPushResponseDto.Data smfgdata = new SmfgAppPushResponseDto.Data();
        smfgdata.setApplicationid("1234");
        smfgAppPushResponseDto.setData(smfgdata);
        nbfcResponseDTO.setData(smfgAppPushResponseDto);

        NameAndDobDetailsDto nameAndDobDetailsDto = new NameAndDobDetailsDto();
        nameAndDobDetailsDto.setFirstName("DILJIT");
        nameAndDobDetailsDto.setLastName("DOSANJH");
        doReturn(nameAndDobDetailsDto).when(kycUtils).getNameAndDobValues(any(),anyLong());

        doReturn("20068").when(smfgConfig).getCurrentAddressType();
        doReturn("20069").when(smfgConfig).getPermanentAddressType();
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(ArgumentMatchers.any(NBFCRequestDTO.class), eq(LenderAssociationStages.BRE));
        doReturn(lendingRiskVariablesSnapshot).when(lendingRiskVariablesSnapshotDao).findByApplicationId(eq(5756732L));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(eq(20000404L));
        doCallRealMethod().when(converterUtils).formatDecimalValue(anyDouble(), anyString());
        doCallRealMethod().when(converterUtils).parseData(anyString());
        doReturn("shop address").when(lendingApplicationServiceV2).constructShopAddress(lendingApplication);

        Optional<BankDetailsDto> bankDetailsDto = Optional.of(new BankDetailsDto());
        doReturn(bankDetailsDto).when(merchantService).fetchMerchantBankDetails(anyLong());

        PanFetchKYCResponseDto panFetchKYCResponseDto = new PanFetchKYCResponseDto();
        PanFetchKYCResponseDto.Data data = new PanFetchKYCResponseDto.Data();
        data.setId("123");
        data.setVerifiedDob("17/10/1999");
        data.setMaskedAadhaar("123456789");
        data.setIsPanNsdlVerified(true);
        panFetchKYCResponseDto.setData(data);
        doReturn(panFetchKYCResponseDto).when(kycHandler).panFetch(anyString(), anyLong());

        BusinessDocsDTO businessDocsDTO = new BusinessDocsDTO();
        businessDocsDTO.setPdfUrl("http://sampleurl.com");
        PriorityQueue<BusinessDocsDTO> businessDocsQueue = new PriorityQueue<>(
                Comparator.comparing(BusinessDocsDTO::getPdfUrl)
        );
        businessDocsQueue.add(businessDocsDTO);
        doReturn(businessDocsQueue).when(kycUtils).getBusinessDocData(anyLong(),anyString(),anyString());
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgAppPushResponseDto.class));
        doReturn(smfgAppPushResponseDto).when(objectMapper).readValue(any(String.class), eq(SmfgAppPushResponseDto.class));

        boolean result = smfgBreService.invokeBre(lenderAssociationDetailsRequestDto);
        assertFalse(result);
    }

    @Test
    public void invokeBreFlow_BRECheckFail() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setPanNumber("KIXXXXXXXH");
        cKycResponseDto.setPanName("Ankush Singh");
        cKycResponseDto.setGender("Male");
        cKycResponseDto.setDob("07/10/1999");
        cKycResponseDto.setMobile("9350XXXXXX");
        cKycResponseDto.setAddress("Khizarpura Kamoda Kurukshetra Haryana 136119");
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);
        lendingRiskVariablesSnapshot.setVintage(790L);
        lendingRiskVariablesSnapshot.setMonthlyNfi(82342347D);
        lendingRiskVariablesSnapshot.setSummaryTpv(855D);
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(true);

        PanFetchKYCResponseDto panFetchKYCResponseDto = new PanFetchKYCResponseDto();
        panFetchKYCResponseDto = new PanFetchKYCResponseDto();

        doReturn(panFetchKYCResponseDto).when(kycHandler).panFetch("PAN123", 123L);


        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(ArgumentMatchers.any(NBFCRequestDTO.class), eq(LenderAssociationStages.BRE));
        doReturn(lendingRiskVariablesSnapshot).when(lendingRiskVariablesSnapshotDao).findByApplicationId(eq(5756732L));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(eq(20000404L));
        doCallRealMethod().when(converterUtils).formatDecimalValue(anyDouble(), anyString());
        boolean result = smfgBreService.invokeBre(lenderAssociationDetailsRequestDto);
        assertFalse(result);
    }

    @Test
    public void invokeBreFlow_BRECheckFailNsdlVerified() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        lendingApplication.setCreatedAt(new Date());
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setPanNumber("KIXXXXXXXH");
        cKycResponseDto.setPanName("Ankush Singh");
        cKycResponseDto.setGender("Male");
        cKycResponseDto.setDob("07/10/1999");
        cKycResponseDto.setMobile("9350XXXXXX");
        cKycResponseDto.setAddress("Khizarpura Kamoda Kurukshetra Haryana 136119");
        cKycResponseDto.setAadharNumber("123456789");
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);
        lendingRiskVariablesSnapshot.setVintage(790L);
        lendingRiskVariablesSnapshot.setMonthlyNfi(82342347D);
        lendingRiskVariablesSnapshot.setSummaryTpv(855D);
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(true);
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(ArgumentMatchers.any(NBFCRequestDTO.class), eq(LenderAssociationStages.BRE));
        doReturn(lendingRiskVariablesSnapshot).when(lendingRiskVariablesSnapshotDao).findByApplicationId(eq(5756732L));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(eq(20000404L));
        doCallRealMethod().when(converterUtils).formatDecimalValue(anyDouble(), anyString());
        doCallRealMethod().when(converterUtils).parseData(anyString());
        doReturn("shop address").when(lendingApplicationServiceV2).constructShopAddress(lendingApplication);

        Optional<BankDetailsDto> bankDetailsDto = Optional.of(new BankDetailsDto());
        doReturn(bankDetailsDto).when(merchantService).fetchMerchantBankDetails(anyLong());

        PanFetchKYCResponseDto panFetchKYCResponseDto = new PanFetchKYCResponseDto();
        PanFetchKYCResponseDto.Data data = new PanFetchKYCResponseDto.Data();
        data.setId("123");
        data.setVerifiedDob("07/10/1999");
        data.setMaskedAadhaar("123456789");
        data.setIsPanNsdlVerified(false);
        panFetchKYCResponseDto.setData(data);
        doReturn(panFetchKYCResponseDto).when(kycHandler).panFetch(anyString(), anyLong());

        BusinessDocsDTO businessDocsDTO = new BusinessDocsDTO();
        businessDocsDTO.setPdfUrl("http://sampleurl.com");
        PriorityQueue<BusinessDocsDTO> businessDocsQueue = new PriorityQueue<>(
                Comparator.comparing(BusinessDocsDTO::getPdfUrl)
        );
        businessDocsQueue.add(businessDocsDTO);
        doReturn(businessDocsQueue).when(kycUtils).getBusinessDocData(anyLong(),anyString(),anyString());
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgAppPushResponseDto.class));

        boolean result = smfgBreService.invokeBre(lenderAssociationDetailsRequestDto);
        assertFalse(result);
    }

    @Test
    public void invokeBreFlow_BRECheckFailAadharMismatch() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        lendingApplication.setCreatedAt(new Date());
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setPanNumber("KIXXXXXXXH");
        cKycResponseDto.setPanName("Ankush Singh");
        cKycResponseDto.setGender("Male");
        cKycResponseDto.setDob("07/10/1999");
        cKycResponseDto.setMobile("9350XXXXXX");
        cKycResponseDto.setAddress("Khizarpura Kamoda Kurukshetra Haryana 136119");
        cKycResponseDto.setAadharNumber("123456789");
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);
        lendingRiskVariablesSnapshot.setVintage(790L);
        lendingRiskVariablesSnapshot.setMonthlyNfi(82342347D);
        lendingRiskVariablesSnapshot.setSummaryTpv(855D);
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(true);
        SmfgAppPushResponseDto smfgAppPushResponseDto = new SmfgAppPushResponseDto();
        smfgAppPushResponseDto.setStatus("SUCCESS");
        SmfgAppPushResponseDto.Data smfgdata = new SmfgAppPushResponseDto.Data();
        smfgdata.setApplicationid("1234");
        smfgAppPushResponseDto.setData(smfgdata);
        nbfcResponseDTO.setData(smfgAppPushResponseDto);

        NameAndDobDetailsDto nameAndDobDetailsDto = new NameAndDobDetailsDto();
        nameAndDobDetailsDto.setFirstName("DILJIT");
        nameAndDobDetailsDto.setLastName("DOSANJH");
        doReturn(nameAndDobDetailsDto).when(kycUtils).getNameAndDobValues(any(),anyLong());

        doReturn("20068").when(smfgConfig).getCurrentAddressType();
        doReturn("20069").when(smfgConfig).getPermanentAddressType();
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(ArgumentMatchers.any(NBFCRequestDTO.class), eq(LenderAssociationStages.BRE));
        doReturn(lendingRiskVariablesSnapshot).when(lendingRiskVariablesSnapshotDao).findByApplicationId(eq(5756732L));
        doReturn(cKycResponseDto).when(kycUtils).getKycData(eq(20000404L));
        doCallRealMethod().when(converterUtils).formatDecimalValue(anyDouble(), anyString());
        doCallRealMethod().when(converterUtils).parseData(anyString());
        doReturn("shop address").when(lendingApplicationServiceV2).constructShopAddress(lendingApplication);

        Optional<BankDetailsDto> bankDetailsDto = Optional.of(new BankDetailsDto());
        doReturn(bankDetailsDto).when(merchantService).fetchMerchantBankDetails(anyLong());

        PanFetchKYCResponseDto panFetchKYCResponseDto = new PanFetchKYCResponseDto();
        PanFetchKYCResponseDto.Data data = new PanFetchKYCResponseDto.Data();
        data.setId("123");
        data.setVerifiedDob("07/10/1999");
        data.setMaskedAadhaar("12");
        data.setIsPanNsdlVerified(true);
        panFetchKYCResponseDto.setData(data);
        doReturn(panFetchKYCResponseDto).when(kycHandler).panFetch(anyString(), anyLong());

        BusinessDocsDTO businessDocsDTO = new BusinessDocsDTO();
        businessDocsDTO.setPdfUrl("http://sampleurl.com");
        PriorityQueue<BusinessDocsDTO> businessDocsQueue = new PriorityQueue<>(
                Comparator.comparing(BusinessDocsDTO::getPdfUrl)
        );
        businessDocsQueue.add(businessDocsDTO);
        doReturn(businessDocsQueue).when(kycUtils).getBusinessDocData(anyLong(),anyString(),anyString());
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgAppPushResponseDto.class));
        doReturn(smfgAppPushResponseDto).when(objectMapper).readValue(any(String.class), eq(SmfgAppPushResponseDto.class));

        boolean result = smfgBreService.invokeBre(lenderAssociationDetailsRequestDto);
        assertFalse(result);
    }

    @Test
    public void invokeBreFlow_BaseConditionFailure() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);
        lendingRiskVariablesSnapshot.setVintage(790L);
        lendingRiskVariablesSnapshot.setMonthlyNfi(82342347D);
        lendingRiskVariablesSnapshot.setSummaryTpv(855D);
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(false);

        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(ArgumentMatchers.any(NBFCRequestDTO.class), eq(LenderAssociationStages.BRE));
        doCallRealMethod().when(converterUtils).formatDecimalValue(anyDouble(), anyString());
        boolean result = smfgBreService.invokeBre(lenderAssociationDetailsRequestDto);
        assertFalse(result);
        assertEquals(LenderAssociationStatus.RISK_FAILED.name(), lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getBreStatus());
        verify(commonService, times(1)).manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
    }

    @Test
    public void invokeBreFlow_ValidationFailure() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);
        lendingRiskVariablesSnapshot.setVintage(790L);
        lendingRiskVariablesSnapshot.setMonthlyNfi(82342347D);
        lendingRiskVariablesSnapshot.setSummaryTpv(855D);
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(false);
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(ArgumentMatchers.any(NBFCRequestDTO.class), eq(LenderAssociationStages.BRE));
        doReturn(lendingRiskVariablesSnapshot).when(lendingRiskVariablesSnapshotDao).findByApplicationId(eq(5756732L));
        doCallRealMethod().when(converterUtils).formatDecimalValue(anyDouble(), anyString());
        boolean result = smfgBreService.invokeBre(lenderAssociationDetailsRequestDto);
        assertFalse(result);
        assertEquals(LenderAssociationStatus.RISK_FAILED.name(), lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getBreStatus());
        verify(commonService, times(1)).manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
    }

    @Test
    public void invokeBreFlow_InvalidPayloadFailure() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);
        lendingRiskVariablesSnapshot.setVintage(790L);
        lendingRiskVariablesSnapshot.setMonthlyNfi(82342347D);
        lendingRiskVariablesSnapshot.setSummaryTpv(855D);
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);
        NBFCResponseDTO<Object> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(false);
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(ArgumentMatchers.any(NBFCRequestDTO.class), eq(LenderAssociationStages.BRE));
        doCallRealMethod().when(converterUtils).formatDecimalValue(anyDouble(), anyString());
        boolean result = smfgBreService.invokeBre(lenderAssociationDetailsRequestDto);
        assertFalse(result);
        assertEquals(LenderAssociationStatus.RISK_FAILED.name(), lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getBreStatus());
        verify(commonService, times(1)).manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
    }

    @Test
    public void processBreCallback_Success() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        SmfgCallbackRequest breCallbackResponseDTO = new SmfgCallbackRequest();
        SmfgCallbackRequest.Data data = new SmfgCallbackRequest.Data();
        SmfgCallbackRequest.Output output = new SmfgCallbackRequest.Output();
        output.setStatus("SUCCESS");
        output.setResult("APPROVE");
        data.setOutput(output);
        data.setCallbackStage("Eligibility approve");
        breCallbackResponseDTO.setData(data);
        NBFCResponseDTO<SmfgCallbackRequest> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(true);
        nbfcResponseDTO.setData(breCallbackResponseDTO);
        nbfcResponseDTO.setApplicationId("5756732");
        nbfcResponseDTO.setLender("SMFG");
        String response = "successResponse";
        doReturn(Optional.of(lendingApplication)).when(lendingApplicationDao).findById(any(Long.class));
        doReturn(lendingApplicationLenderDetails).when(lendingApplicationLenderDetailsDao).findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString());
        doReturn(response).when(objectMapper).writeValueAsString(any(SmfgCallbackRequest.class));
        doReturn(breCallbackResponseDTO).when(objectMapper).readValue(ArgumentMatchers.any(String.class), eq(SmfgCallbackRequest.class));
        boolean result = smfgBreService.processBreCallback(nbfcResponseDTO);

        assertTrue(result);
        verify(objectMapper, times(1)).writeValueAsString(breCallbackResponseDTO);
        verify(objectMapper, times(1)).readValue(response, SmfgCallbackRequest.class);
        verify(commonService, times(1)).manageApplicationStateAndPushToNextStage(ArgumentMatchers.any(LenderAssociationDetailsRequestDto.class));
    }

    @Test
    public void processBreCallback_LendingApplicationNotFoundFailure() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        SmfgCallbackRequest breCallbackResponseDTO = new SmfgCallbackRequest();
        NBFCResponseDTO<SmfgCallbackRequest> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(true);
        nbfcResponseDTO.setData(breCallbackResponseDTO);
        nbfcResponseDTO.setApplicationId("5756732");
        nbfcResponseDTO.setLender("SMFG");
        String response = "successResponse";
        doReturn(lendingApplicationLenderDetails).when(lendingApplicationLenderDetailsDao).findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString());
        doReturn(response).when(objectMapper).writeValueAsString(any(SmfgCallbackRequest.class));
        doReturn(breCallbackResponseDTO).when(objectMapper).readValue(ArgumentMatchers.any(String.class), eq(SmfgCallbackRequest.class));
        boolean result = smfgBreService.processBreCallback(nbfcResponseDTO);
        assertFalse(result);
    }

    @Test
    public void processBreCallback_LenderDetailsNotFoundFailure() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        SmfgCallbackRequest breCallbackResponseDTO = new SmfgCallbackRequest();
        NBFCResponseDTO<SmfgCallbackRequest> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(true);
        nbfcResponseDTO.setData(breCallbackResponseDTO);
        nbfcResponseDTO.setApplicationId("5756732");
        nbfcResponseDTO.setLender("SMFG");
        String response = "successResponse";
        doReturn(Optional.of(lendingApplication)).when(lendingApplicationDao).findById(any(Long.class));
        doReturn(response).when(objectMapper).writeValueAsString(any(SmfgCallbackRequest.class));
        doReturn(breCallbackResponseDTO).when(objectMapper).readValue(ArgumentMatchers.any(String.class), eq(SmfgCallbackRequest.class));
        boolean result = smfgBreService.processBreCallback(nbfcResponseDTO);
        assertFalse(result);
    }

    @Test
    public void processBreCallback_IncorrectStageFailure() throws Exception {
        setup();
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        lendingApplication.setLoanAmount(20000D);
        lendingApplication.setProcessingFee(2000D);
        lendingApplication.setExternalLoanId("BPL050520245756732");
        lendingApplication.setEdi(1000D);
        lendingApplication.setDisbursalAmount(18000D);
        lendingApplication.setPayableDays(365L);
        lendingApplication.setBusinessName("Groceries");
        lendingApplication.setCity("Kurukshetra");
        lendingApplication.setState("Haryana");
        lendingApplication.setArea("Khizarpura");
        lendingApplication.setTenureInMonths(12);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        SmfgCallbackRequest breCallbackResponseDTO = new SmfgCallbackRequest();
        NBFCResponseDTO<SmfgCallbackRequest> nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(true);
        nbfcResponseDTO.setData(breCallbackResponseDTO);
        nbfcResponseDTO.setApplicationId("5756732");
        nbfcResponseDTO.setLender("SMFG");
        String response = "successResponse";
        doReturn(Optional.of(lendingApplication)).when(lendingApplicationDao).findById(any(Long.class));
        doReturn(lendingApplicationLenderDetails).when(lendingApplicationLenderDetailsDao).findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString());
        doReturn(response).when(objectMapper).writeValueAsString(any(SmfgCallbackRequest.class));
        doReturn(breCallbackResponseDTO).when(objectMapper).readValue(ArgumentMatchers.any(String.class), eq(SmfgCallbackRequest.class));
        boolean result = smfgBreService.processBreCallback(nbfcResponseDTO);
        assertFalse(result);
    }
}
