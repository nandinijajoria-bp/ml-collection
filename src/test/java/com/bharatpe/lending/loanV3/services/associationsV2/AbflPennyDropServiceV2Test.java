package com.bharatpe.lending.loanV3.services.associationsV2;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.ExperianSnapshot;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.PincodeColor;
import com.bharatpe.lending.common.enums.RiskSegment;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.ConfigResolver;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.dto.ABFLPennyDropRequestDTO;
import com.bharatpe.lending.loanV3.dto.ABFLPennyDropResponseDTO;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.services.gateway.AbflApiGateway;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class AbflPennyDropServiceV2Test {
    @Mock
    LendingApplicationDao lendingApplicationDao;
    @Mock
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Mock
    MerchantService merchantService;
    @Mock
    AbflApiGateway abflApiGateway;
    @Mock
    ConfigResolver configResolver;
    @Mock
    NbfcUtils nbfcUtils;
    @InjectMocks
    AbflPennyDropServiceV2 abflPennyDropServiceV2;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testInvokePennyDrop_failure() {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");
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
        lendingApplication.setAgreementAt(new Date());
        lendingApplication.setTenureInMonths(12);

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("ABFL");
        lendingApplicationLenderDetails.setStage("PENNY_DROP");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setDob("07/10/1999");
        cKycResponseDto.setPanNumber("KIXXXXXXXH");
        cKycResponseDto.setName("Test");
        cKycResponseDto.setMobile("9350XXXXXX");
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);
        ExperianSnapshot experianSnapshot = new ExperianSnapshot();
        experianSnapshot.setPincode(136119);
        Map<String, String> businessCategories = new HashMap<>();
        businessCategories.put("businessCategory", "Groceries");
        businessCategories.put("businessSubcategory", "Store");
        Map<String,String> latLong = new HashMap<>();
        latLong.put("lat", "122424i4i");
        latLong.put("long", "402387208");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setCKycResponseDto(cKycResponseDto);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);

        BankDetailsDto bankDetailsDto = new BankDetailsDto();
        bankDetailsDto.setBankCode("asd");
        bankDetailsDto.setBankName("HDFC");
        bankDetailsDto.setAccountNumber("1231231");


        when(lendingApplicationDao.save(any())).thenReturn(null);
        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString())).thenReturn(lendingApplicationLenderDetails);
        when(lendingApplicationLenderDetailsDao.save(any())).thenReturn(lendingApplicationLenderDetails);
        when(lendingApplicationLenderDetailsDao.findById(any())).thenReturn(Optional.of(lendingApplicationLenderDetails));
        when(merchantService.fetchMerchantBankDetails(anyLong())).thenReturn(Optional.of(bankDetailsDto));
        when(abflApiGateway.invokePennyDrop(any())).thenReturn(null);

        HashMap<String, String> map = new HashMap<>();
        map.put("application_id", "5756732");

        abflPennyDropServiceV2.invokePennyDrop(map);

    }

    @Test
    public void testInvokePennyDrop_success() {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");
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
        lendingApplication.setAgreementAt(new Date());
        lendingApplication.setTenureInMonths(12);

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("ABFL");
        lendingApplicationLenderDetails.setStage("PENNY_DROP");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setDob("07/10/1999");
        cKycResponseDto.setPanNumber("KIXXXXXXXH");
        cKycResponseDto.setName("Test");
        cKycResponseDto.setMobile("9350XXXXXX");
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);
        ExperianSnapshot experianSnapshot = new ExperianSnapshot();
        experianSnapshot.setPincode(136119);
        Map<String, String> businessCategories = new HashMap<>();
        businessCategories.put("businessCategory", "Groceries");
        businessCategories.put("businessSubcategory", "Store");
        Map<String,String> latLong = new HashMap<>();
        latLong.put("lat", "122424i4i");
        latLong.put("long", "402387208");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setCKycResponseDto(cKycResponseDto);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);

        BankDetailsDto bankDetailsDto = new BankDetailsDto();
        bankDetailsDto.setBankCode("asd");
        bankDetailsDto.setBankName("HDFC");
        bankDetailsDto.setAccountNumber("1231231");

        ABFLPennyDropResponseDTO abflPennyDropResponseDTO = ABFLPennyDropResponseDTO.builder()
                .success(true)
                .data(ABFLPennyDropResponseDTO.Data.builder()
                        .responseStatus("SUCCESS")
                        .data(ABFLPennyDropResponseDTO.Data.ResponseData.builder()
                                .iBLRefNo("414312930740")
                                .amount("1")
                                .statusCode("R000")
                                .customerRefNo("B2B061000002645")
                                .statusDesc("Success")
                                .tranType("IMPS")
                                .build())
                        .build())
                .build();
        when(lendingApplicationDao.save(any())).thenReturn(null);
        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString())).thenReturn(lendingApplicationLenderDetails);
        when(lendingApplicationLenderDetailsDao.save(any())).thenReturn(lendingApplicationLenderDetails);
        when(lendingApplicationLenderDetailsDao.findById(any())).thenReturn(Optional.of(lendingApplicationLenderDetails));
        when(merchantService.fetchMerchantBankDetails(anyLong())).thenReturn(Optional.of(bankDetailsDto));
        when(abflApiGateway.invokePennyDrop(any())).thenReturn(abflPennyDropResponseDTO);

        HashMap<String, String> map = new HashMap<>();
        map.put("application_id", "5756732");

        abflPennyDropServiceV2.invokePennyDrop(map);
        verify(nbfcUtils, times(1)).pushApplicationToNextStage(any(),any(),any(), anyBoolean());

    }


    @Test
    public void testCreatePayload_success(){
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");
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
        lendingApplication.setAgreementAt(new Date());
        lendingApplication.setTenureInMonths(12);

        BankDetailsDto bankDetailsDto = new BankDetailsDto();
        bankDetailsDto.setBankCode("HDFC101");
        bankDetailsDto.setIfsc("HDFC11001");
        bankDetailsDto.setBankName("HDFC");
        bankDetailsDto.setAccountNumber("1231231");

        when(merchantService.fetchMerchantBankDetails(anyLong())).thenReturn(Optional.of(bankDetailsDto));

        ABFLPennyDropRequestDTO result = abflPennyDropServiceV2.createPayload(Long.valueOf(1), lendingApplication);
        Assert.assertEquals("1231231", result.getPayload().accountNumber);
    }

    @Test
    public void testCreatePayload_exception() {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);

        when(merchantService.fetchMerchantBankDetails(anyLong())).thenReturn(null);

        ABFLPennyDropRequestDTO abflPennyDropRequestDTO = abflPennyDropServiceV2.createPayload(Long.valueOf(1), lendingApplication);

        Assert.assertEquals(null, abflPennyDropRequestDTO);

    }

    @Test
    public void testInvokePennyDrop_success1() {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");
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
        lendingApplication.setAgreementAt(new Date());
        lendingApplication.setTenureInMonths(12);

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("ABFL");
        lendingApplicationLenderDetails.setStage("PENNY_DROP");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);

        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        cKycResponseDto.setDob("07/10/1999");
        cKycResponseDto.setPanNumber("KIXXXXXXXH");
        cKycResponseDto.setName("Test");
        cKycResponseDto.setMobile("9350XXXXXX");

        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
        lendingRiskVariablesSnapshot.setApplicationId(5756732L);
        lendingRiskVariablesSnapshot.setBureauScore(700D);
        lendingRiskVariablesSnapshot.setRiskSegment(RiskSegment.NTB_ETB_1);
        lendingRiskVariablesSnapshot.setRiskGroup("R1");
        lendingRiskVariablesSnapshot.setPincodeColor(PincodeColor.DARK_GREEN);

        ExperianSnapshot experianSnapshot = new ExperianSnapshot();
        experianSnapshot.setPincode(136119);
        Map<String, String> businessCategories = new HashMap<>();
        businessCategories.put("businessCategory", "Groceries");
        businessCategories.put("businessSubcategory", "Store");
        Map<String,String> latLong = new HashMap<>();
        latLong.put("lat", "122424i4i");
        latLong.put("long", "402387208");

        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequestDto.setCKycResponseDto(cKycResponseDto);
        lenderAssociationDetailsRequestDto.setMerchantId(20000404L);

        BankDetailsDto bankDetailsDto = new BankDetailsDto();
        bankDetailsDto.setBankCode("asd");
        bankDetailsDto.setBankName("HDFC");
        bankDetailsDto.setAccountNumber("1231231");

        ABFLPennyDropResponseDTO abflPennyDropResponseDTO = ABFLPennyDropResponseDTO.builder()
                .success(true)
                .data(ABFLPennyDropResponseDTO.Data.builder()
                        .responseStatus("success")
                        .data(ABFLPennyDropResponseDTO.Data.ResponseData.builder()
                                .iBLRefNo("414312930740")
                                .amount("1")
                                .statusCode("R000")
                                .customerRefNo("B2B061000002645")
                                .statusDesc("Success")
                                .tranType("IMPS")
                                .build())
                        .build())
                .build();
        when(lendingApplicationDao.save(any())).thenReturn(null);
        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString())).thenReturn(lendingApplicationLenderDetails);
        when(lendingApplicationLenderDetailsDao.save(any())).thenReturn(lendingApplicationLenderDetails);
        when(lendingApplicationLenderDetailsDao.findById(any())).thenReturn(Optional.of(lendingApplicationLenderDetails));
        when(merchantService.fetchMerchantBankDetails(anyLong())).thenReturn(Optional.of(bankDetailsDto));
        when(abflApiGateway.invokePennyDrop(any())).thenReturn(abflPennyDropResponseDTO);

        HashMap<String, String> map = new HashMap<>();
        map.put("application_id", "5756732");

        abflPennyDropServiceV2.invokePennyDrop(map);
        verify(nbfcUtils, times(1)).pushApplicationToNextStage(any(),any(),any(), anyBoolean());

    }

    @Test
    public void testInvokePennyDrop_empty_lendingApplicationLenderDetails() {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");
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
        lendingApplication.setAgreementAt(new Date());
        lendingApplication.setTenureInMonths(12);


        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString())).thenReturn(null);

        HashMap<String, String> map = new HashMap<>();
        map.put("application_id", "5756732");

        abflPennyDropServiceV2.invokePennyDrop(map);

    }

    @Test
    public void testInvokePennyDrop_stage_not_matching() {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");
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
        lendingApplication.setAgreementAt(new Date());
        lendingApplication.setTenureInMonths(12);

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("ABFL");
        lendingApplicationLenderDetails.setStage("ASSC_COMPLETED");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);

        BankDetailsDto bankDetailsDto = new BankDetailsDto();
        bankDetailsDto.setBankCode("asd");
        bankDetailsDto.setBankName("HDFC");
        bankDetailsDto.setAccountNumber("1231231");

        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString())).thenReturn(lendingApplicationLenderDetails);
        when(merchantService.fetchMerchantBankDetails(anyLong())).thenReturn(Optional.of(bankDetailsDto));

        HashMap<String, String> map = new HashMap<>();
        map.put("application_id", "5756732");

        abflPennyDropServiceV2.invokePennyDrop(map);

    }

    @Test
    public void testInvokePennyDrop_empty_applicationId() {

        HashMap<String, String> map = new HashMap<>();
        map.put("application_id", "5756732");
        when(lendingApplicationDao.findById(any())).thenReturn(Optional.empty());
        abflPennyDropServiceV2.invokePennyDrop(map);

    }


    @Test(expected = Exception.class)
    public void testInvokePennyDrop_reject_on_exception() {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("ABFL");
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
        lendingApplication.setAgreementAt(new Date());
        lendingApplication.setTenureInMonths(12);

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("ABFL");
        lendingApplicationLenderDetails.setStage("PENNY_DROP");
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);

        BankDetailsDto bankDetailsDto = new BankDetailsDto();
        bankDetailsDto.setBankCode("asd");
        bankDetailsDto.setBankName("HDFC");
        bankDetailsDto.setAccountNumber("1231231");

        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), anyString(), anyString())).thenReturn(lendingApplicationLenderDetails);
        when(merchantService.fetchMerchantBankDetails(anyLong())).thenReturn(Optional.of(bankDetailsDto));

        when(lendingApplicationLenderDetailsDao.save(any())).thenThrow(new NullPointerException("exception occured"));
        HashMap<String, String> map = new HashMap<>();
        map.put("application_id", "5756732");

        abflPennyDropServiceV2.invokePennyDrop(map);

    }


}
