package com.bharatpe.lending.loanV3.services.associationsV2.payu.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dto.LoanInsuranceDTO;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.PayULoanDocumentDownloadRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUDocumentDownloadResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class PayUInsuranceService {
    @Autowired
    private EasyLoanUtil easyLoanUtil;

    @Value("${payu.insurance.rollout.percent:0}")
    private Integer payUInsuranceRolloutPercent;

    @Autowired
    private CommonService commonService;

    @Autowired
    private ILenderAPIGateway lenderAPIGateway;

    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    private static final List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());

    private static final String ICICI_LOMBARD = "ICICI_Lombard";
    private static final String PAYU_ICICI_IL = "PAYU-ICICI-IL";
    private static final double PREMIUM_RATE = 0.01;
    private static final double GST_RATE = 0.18;

    /**
     * Returns insurance premium details for the given lending application if insurance is enabled for the merchant.
     *
     * @param lendingApplication the lending application
     * @return LoanInsuranceDTO if insurance is enabled, otherwise null
     */
    public LoanInsuranceDTO getInsurancePremiums(LendingApplication lendingApplication) {
        if (topupLoans.contains(lendingApplication.getLoanType()) || !easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), payUInsuranceRolloutPercent)) {
            log.info("{} insurance not enabled for merchantId {}", lendingApplication.getLender(), lendingApplication.getMerchantId());
            return LoanInsuranceDTO.builder().build();
        }
        log.info("[getInsurancePremiums] Fetching insurance premiums from PayU for applicationId {}", lendingApplication.getId());
        LoanInsuranceDTO insuranceDTO = LoanInsuranceDTO.builder()
                .insurances(Arrays.asList(
                        LoanInsuranceDTO.InsuranceDetails.builder()
                                .insurancePremium(calculatePremium(lendingApplication.getLoanAmount()))
                                .sumInsured(lendingApplication.getLoanAmount())
                                .policyTermsInMonths((lendingApplication.getTenureInMonths() <= 12) ? 12 : 24)
                                .product(PAYU_ICICI_IL + "-" + lendingApplication.getTenureInMonths())
                                .provider(ICICI_LOMBARD)
                                .build()
                ))
                .build();
        log.info("[getInsurancePremiums] Returning insurance premiums: {}", insuranceDTO);
        return insuranceDTO;
    }


    private Double calculatePremium(Double loanAmount) {
        double premium = loanAmount * PREMIUM_RATE;
        double gst = premium * GST_RATE;
        log.info("Calculated premium: {}, GST: {}, Total: {}", premium, gst, premium + gst);
        return Math.ceil(premium + gst);
    }

    @Transactional
    public String invokeInsuranceDocument(LendingApplication lendingApplication) {
        log.info("[invokeInsuranceDocument] Starting insurance document invocation for applicationId: {}", lendingApplication.getId());

        try {
            LendingApplicationLenderDetails lenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(
                    lendingApplication.getId(), lendingApplication.getLender());

            if (ObjectUtils.isEmpty(lenderDetails)) {
                log.warn("[invokeInsuranceDocument] No lender details found for applicationId: {}", lendingApplication.getId());
                return null;
            }

            NBFCRequestDTO nbfcRequestDTO = getPayload(lendingApplication);
            if (ObjectUtils.isEmpty(nbfcRequestDTO)) {
                log.warn("[invokeInsuranceDocument] Failed to create NBFC request payload for applicationId: {}", lendingApplication.getId());
                return null;
            }

            log.info("[invokeInsuranceDocument] Invoking lender API for applicationId: {}", lendingApplication.getId());
            NBFCResponseDTO nbfcResponseDTO = lenderAPIGateway.invokeStage(nbfcRequestDTO, LenderAssociationStages.INSURANCE_DOC);

            if (Objects.isNull(nbfcResponseDTO) || !nbfcResponseDTO.getSuccess() || Objects.isNull(nbfcResponseDTO.getData())) {
                log.warn("[invokeInsuranceDocument] Lender API invocation failed for applicationId: {}", lendingApplication.getId());
                return null;
            }

            log.info("[invokeInsuranceDocument] Successfully invoked lender API for applicationId: {}", lendingApplication.getId());
            PayUDocumentDownloadResponseDTO responseDTO = new ObjectMapper().convertValue(nbfcResponseDTO.getData(), PayUDocumentDownloadResponseDTO.class);

            if (Objects.isNull(responseDTO) || Objects.isNull(responseDTO.getApiResponse()) ||
                    ObjectUtils.isEmpty(responseDTO.getApiResponse().getDocumentList())) {
                log.warn("[invokeInsuranceDocument] No documents found in lender response for applicationId: {}", lendingApplication.getId());
                return null;
            }

            log.info("[invokeInsuranceDocument] Processing documents for applicationId: {}", lendingApplication.getId());
            for (PayUDocumentDownloadResponseDTO.DocumentList document : responseDTO.getApiResponse().getDocumentList()) {
                if (!ObjectUtils.isEmpty(document.getContentUrl())) {
                    log.info("[invokeInsuranceDocument] Found document content URL: {} for applicationId: {}", document.getContentUrl(), lendingApplication.getId());
                    return document.getContentUrl();
                }
            }

            log.warn("[invokeInsuranceDocument] No valid content URL found in documents for applicationId: {}", lendingApplication.getId());
        } catch (Exception e) {
            log.error("[invokeInsuranceDocument] Exception occurred while processing insurance document for applicationId: {}. Error: {}", lendingApplication.getId(), e.getMessage(), e);
        }

        return null;
    }

    public NBFCRequestDTO getPayload(LendingApplication lendingApplication) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || ObjectUtils.isEmpty(lendingApplicationLenderDetails.getLeadId())) {
            log.info("No lender details / leadId found for applicationId  {}", lendingApplication.getId());
            return null;
        }
        return NBFCRequestDTO.builder()
                .applicationId(lendingApplication.getId())
                .lender(lendingApplication.getLender())
                .productName("LENDING")
                .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))
                .payload(PayULoanDocumentDownloadRequestDTO.builder()
                        .applicationId(lendingApplicationLenderDetails.getLeadId())
                        .build())
                .build();
    }
}
