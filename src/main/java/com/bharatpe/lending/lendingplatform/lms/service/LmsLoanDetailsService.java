package com.bharatpe.lending.lendingplatform.lms.service;

import com.bharatpe.common.entities.LendingApplication;
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
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.lendingplatform.lms.client.LendingPlatformHttpClient;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.lendingplatform.lms.dto.response.ApiResponse;
import com.bharatpe.lending.lendingplatform.lms.dto.response.LoanDetailsResponse;
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

    public List<LendingShopDocuments> getShopFrontImage(Long merchantId, Long applicationId) {
        return lendingShopDocumentsDao.findByMerchantIdAndApplicationId(merchantId, applicationId);
    }

    public LendingPincodes getCustomerAddressDetails(Integer pincode) {
        log.info("Fetching merchant city & state details for pincode:{}", pincode);
        return lendingPincodesDao.findByPincode(pincode);
    }
}