package com.bharatpe.lending.lendingplatform.lms.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingPincodesDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingPincodes;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.dto.LendingPaymentScheduleDTO;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.lendingplatform.lms.client.LendingPlatformHttpClient;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.lendingplatform.lms.dto.response.ApiResponse;
import com.bharatpe.lending.lendingplatform.lms.dto.response.ForeclosureDetailsResponse;
import com.bharatpe.lending.lendingplatform.lms.dto.response.LoanDetailsResponse;
import com.bharatpe.lending.lendingplatform.nbfc.exception.LendingApplicationNotFoundException;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ApiEndPointConstants.GET_FORECLOSURE_AMOUNT;
import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ApiEndPointConstants.GET_LOAN_SUMMARY;

@Service
@Slf4j
public class LmsLoanDetailsService {

    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private LendingKfsDao lendingKfsDao;

    @Autowired
    private LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;

    @Autowired
    private KycUtils kycUtils;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    private EnachHandler enachHandler;

    @Autowired
    private LendingPlatformHttpClient lendingPlatformHttpClient;

    @Autowired
    private LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    private LoanUtil loanUtil;

    @Autowired
    private LendingPincodesDao lendingPincodesDao;

    public LoanDetailsResponse getLoanSummaryFromOneLms(String bpLoanId) {
        Map<String, String> params = new HashMap<>();
        params.put("bpLoanId", bpLoanId);

        try {
            ApiResponse<LoanDetailsResponse> loanDetailsResponse = lendingPlatformHttpClient.sendGetRequestWithParams(GET_LOAN_SUMMARY, params, LoanDetailsResponse.class);

            if (ObjectUtils.isEmpty(loanDetailsResponse) || ObjectUtils.isEmpty(loanDetailsResponse.getData()) || ObjectUtils.isEmpty(loanDetailsResponse.getData().getBpLoanId())) {
                log.error("Invalid response from Loan Details API: {}", loanDetailsResponse);
                throw new RuntimeException("Invalid response from Loan Details API");
            }

            log.info("Transaction details fetched successfully.");
            return loanDetailsResponse.getData();
        } catch (Exception e) {
            log.error("HTTP error while fetching loan details for bpLoanId: {}", bpLoanId, e);
            throw new RuntimeException("Error fetching loan details", e);
        }
    }

    public LendingPaymentScheduleDTO getLendingPaymentScheduleDTOFromOneLms(String bpLoanId, LendingPaymentScheduleSlave lps) {

        try {
            LoanDetailsResponse loanDetailsResponse = getLoanSummaryFromOneLms(bpLoanId);
            if(loanDetailsResponse == null || loanDetailsResponse.getLoanSummary() == null) {
                log.info("No loan details found from 1LMS for applicationId: {}, merchantId: {}", lps.getApplicationId(), lps.getMerchantId());
                throw new RuntimeException("No loan details found from 1LMS for applicationId: " + lps.getApplicationId() + ", merchantId: " + lps.getMerchantId());
            }
            return LendingPaymentScheduleDTO.fromEntityWithApiData(lps, loanDetailsResponse.getLoanSummary());
        } catch (Exception e) {
            log.error("HTTP error while parsing lms loan details schedule for bpLoanId: {}, merchantsId: {}", bpLoanId, lps.getMerchantId(), e);
            throw new RuntimeException("Error fetching payment schedule", e);
        }
    }

    public LendingPaymentScheduleDTO getLendingPaymentScheduleDTOFromOneLms(String bpLoanId, LendingPaymentSchedule lps) {

        try {
            LoanDetailsResponse loanDetailsResponse = getLoanSummaryFromOneLms(bpLoanId);
            if(loanDetailsResponse == null || loanDetailsResponse.getLoanSummary() == null) {
                log.info("No loan details found from 1LMS for applicationId: {}, merchantId: {}", lps.getApplicationId(), lps.getMerchantId());
                throw new RuntimeException("No loan details found from 1LMS for applicationId: " + lps.getApplicationId() + ", merchantId: " + lps.getMerchantId());
            }
            return LendingPaymentScheduleDTO.fromEntityWithApiData(lps, loanDetailsResponse.getLoanSummary());
        } catch (Exception e) {
            log.error("HTTP error while parsing lms loan details schedule for bpLoanId: {}, merchantsId: {}", bpLoanId, lps.getMerchantId(), e);
            throw new RuntimeException("Error fetching payment schedule", e);
        }
    }

    public LendingApplicationLenderDetails getLenderDetails(Long loanId, String lender) {
        log.info("Fetching Lender details for loanId: {}", loanId);
        return lendingApplicationLenderDetailsDao
                .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                        loanId, Constants.ACTIVE, lender);
    }

    public BankDetailsDto getMerchantBankDetails(Long merchantId) {
        return merchantService.fetchMerchantBankDetails(merchantId)
                .orElseThrow(() -> {
                    log.info("Bank details not found for merchantId: {}", merchantId);
                    return new RuntimeException("Bank details not found for merchant id: " + merchantId);
                });
    }

    public LendingKfs getLendingKfs(Long applicationId) {
        log.info("Fetching kfs docs fro applicationId:{}",applicationId);
        LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
        if (ObjectUtils.isEmpty(lendingKfs)) {
            throw new RuntimeException("KFS details not found for application id: " + applicationId);
        }
        return lendingKfs;
    }

    public CKycResponseDto getKycData(Long merchantId) {
        return kycUtils.getKycData(merchantId);
    }

    public LendingApplicationKycDetails getKycDetails(Long applicationId) {
        return lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
    }

    public MerchantNachDetailsResponseDTO getMandateDetails(LendingApplication lendingApplication) {
        return enachHandler.findByMerchantIdAndApplicationIdAndLender(lendingApplication.getMerchantId(), lendingApplication.getId(), loanUtil.enachServiceLenderMapper(lendingApplication.getLender()));
    }

    public LendingApplication getLendingApplicationDetails(Long merchantId){
        return lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
    }

    public LendingApplication getLendingApplicationByApplicationId(Long applicationId) {
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        if (!lendingApplicationOptional.isPresent()) {
            throw new LendingApplicationNotFoundException("Lending application not found for application id: " + applicationId);
        }
        return lendingApplicationOptional.get();
    }

    public List<LendingShopDocuments> getShopFrontImage(Long merchantId, Long applicationId) {
        return lendingShopDocumentsDao.findByMerchantIdAndApplicationId(merchantId, applicationId);
    }

    public LendingPincodes getCustomerAddressDetails(Integer pincode) {
        log.info("Fetching merchant city & state details for pincode:{}", pincode);
        return lendingPincodesDao.findByPincode(pincode);
    }

    public int getForeclosureAmount(Long merchantId, String externalLoanId) {
        try {
            Map<String, String> requestParams = new HashMap<>();
            requestParams.put("bpLoanId", externalLoanId);
            ApiResponse<ForeclosureDetailsResponse> foreclosureResponse = lendingPlatformHttpClient.sendGetRequestWithParams(GET_FORECLOSURE_AMOUNT,
                    requestParams,
                    ForeclosureDetailsResponse.class);
            if (!ObjectUtils.isEmpty(foreclosureResponse) && foreclosureResponse.isSuccess() && !ObjectUtils.isEmpty(foreclosureResponse.getData())
                    && !ObjectUtils.isEmpty(foreclosureResponse.getData().getForeclosureAmount())) {
                log.info("Foreclosure Amount fetched successfully. Merchant ID: {}", merchantId);
                return (int) Math.ceil(foreclosureResponse.getData().getForeclosureAmount().doubleValue());
            }
            log.error("Error in fetching foreclosure details: Empty or invalid response received for Merchant ID: {}", merchantId);
            throw new RuntimeException("Error in fetching foreclosure details: Empty or invalid response received from lending platform.");
        } catch (Exception e) {
            log.error("Exception occurred while initiating loan request: {}", e.getMessage(), e);
            throw new RuntimeException("Exception occurred while fetching foreclosure details: " + e.getMessage(), e);
        }
    }
}