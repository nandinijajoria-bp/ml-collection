package com.bharatpe.lending.loanV3.services.associationsV2;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
public class AbflDocGenerateService {
    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DocUploadUtils docUploadUtils;

    @Autowired
    LendingNotificationService lendingNotificationService;

    @Autowired
    MerchantService merchantService;

    @Autowired
    LoanUtil loanUtil;

    @Value("${loanDashboard.deeplink:bharatpe://dynamic?key=loan-dashboard-qa}")
    String loanDashboardDeeplink;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    public boolean invokeDocGenerate(LendingApplication lendingApplication, DocType docType, Boolean preSigned, Boolean saveDocs) {
        try {
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("invalid params for ABFL doc generate");
                return false;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("lender record not exist for ABFL with applicationId {} ", lendingApplication.getId());
                return false;
            }
            if (LenderAssociationStatus.DOC_GENERATE_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getLeadStatus())) {
                return fetchAndSaveLenderDocs(lendingApplication, lendingApplicationLenderDetails, preSigned);
            }
            lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.DOC_GENERATE_PENDING.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
            try {
                NBFCRequestDTO<?> nbfcRequest = getPayload(lendingApplication, lendingApplicationLenderDetails);
                NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequest, LenderAssociationStages.DOC_GENERATE);
                if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                    AbflGenerateDocResponseDto abflResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), AbflGenerateDocResponseDto.class);
                    if (!ObjectUtils.isEmpty(abflResponseDto) && "SUCCESS".equalsIgnoreCase(abflResponseDto.getResponseStatus())) {
                        lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.DOC_GENERATE_IN_PROGRESS.name());
                        lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                        return saveDocs ? fetchAndSaveLenderDocs(lendingApplication, lendingApplicationLenderDetails, preSigned) : true;
                    }
                }
            } catch (Exception e) {
                log.info("exception occurred while fetching {} of ABFL for {} {}, {}", docType, lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            }
        } catch (Exception e) {
            log.error("Exception in generating docs of ABFL for applicationId {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private boolean fetchAndSaveLenderDocs(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, Boolean preSigned) {
        try {
            NBFCRequestDTO<?> nbfcRequest = NBFCRequestDTO.builder()
                    .lender("ABFL")
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .payload(AbflFetchDocRequestDto.builder().applicationId(lendingApplication.getExternalLoanId()).build()).build();
            log.info("starting fetch docs from ABFL for application id {} with request {}", lendingApplication.getId(), nbfcRequest);
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequest, LenderAssociationStages.DOWNLOAD_DOCUMENT);
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                AbflFetchDocResponseDto abflResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), AbflFetchDocResponseDto.class);
                log.info("fetch docs response from ABFL for application id {} {}", lendingApplication.getId(), nbfcResponseDto);
                if (!ObjectUtils.isEmpty(abflResponseDto) && "SUCCESS".equalsIgnoreCase(abflResponseDto.getResponseStatus()) && !ObjectUtils.isEmpty(abflResponseDto.getData())) {
                    byte[] agreementDocBytes = Base64.getDecoder().decode(abflResponseDto.getData().getLoanAgreement());
                    InputStream kfsStream = new ByteArrayInputStream(agreementDocBytes);
                    InputStream sanctionStream = new ByteArrayInputStream(agreementDocBytes);
                    docUploadUtils.saveAgreementDocs(lendingApplication, "ABFL", kfsStream, sanctionStream, preSigned);
                    lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.DOC_GENERATE_SUCCESS.name());
                    lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                    sendSuccessNotification(lendingApplication);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Exception in fetching or saving docs of ABFL for applicationId {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private void sendSuccessNotification(LendingApplication lendingApplication) {
        BasicDetailsDto basicDetailsDto = merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId()).orElse(null);
        if (!ObjectUtils.isEmpty(basicDetailsDto)) {
            Map<String, Object> templateParams = new HashMap<>();
            templateParams.put("loan_amount", lendingApplication.getLoanAmount());
            NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
            notificationPayloadDto.setPushTitle("Complete your loan application now! ⏳");
            notificationPayloadDto.setTemplateIdentifier("COMPLETE_AGREEMENT_PUSH");
            notificationPayloadDto.setMobile(basicDetailsDto.getMobile());
            notificationPayloadDto.setPushDeepLink(loanDashboardDeeplink);
            notificationPayloadDto.setClientName("LENDING");
            notificationPayloadDto.setTemplateParams(templateParams);
            lendingNotificationService.notify(notificationPayloadDto);
        }
    }

    private NBFCRequestDTO<?> getPayload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) throws Exception {
        AbflGenerateDocRequestDto abflRequestDto = AbflGenerateDocRequestDto.builder()
                .applicationId(lendingApplication.getExternalLoanId())
                .roi(String.valueOf(lendingApplicationLenderDetails.getAnnualRoi()))
                .disbursementAmount(String.valueOf(lendingApplication.getDisbursalAmount()))
                .loanAmount(String.valueOf(lendingApplication.getLoanAmount()))
                .processingFee(String.valueOf(lendingApplication.getProcessingFee()))
                .tenure(String.valueOf(lendingApplication.getTenureInMonths()))
                .uniqueId(UUID.randomUUID().toString().replace("-", ""))
                .build();

        if (LoanType.TOPUP.name().equals(lendingApplication.getLoanType())) {
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingApplication.getMerchantId(), "ACTIVE");
            if (ObjectUtils.isEmpty(lendingPaymentSchedule) || !Lender.ABFL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc())) {
                throw new Exception("Unable to fetch active parent loan details for merchantId: " + lendingApplication.getMerchantId());
            }
            LendingApplication parentLendingApplication = lendingApplicationDao.findById(lendingPaymentSchedule.getApplicationId()).orElse(null);
            if (ObjectUtils.isEmpty(parentLendingApplication) || ObjectUtils.isEmpty(parentLendingApplication.getExternalLoanId())) {
                throw new Exception("Unable to fetch parent application for applicationId: " + lendingPaymentSchedule.getApplicationId());
            }
            abflRequestDto.setParentLanNo(parentLendingApplication.getExternalLoanId());
            abflRequestDto.setParentLoanOutstandingAmount(fetchLenderForeclosureAmount(lendingPaymentSchedule));
        }

        return NBFCRequestDTO.builder()
                .topup(LoanType.TOPUP.name().equals(lendingApplication.getLoanType()))
                .lender("ABFL")
                .applicationId(lendingApplication.getId())
                .productName("LENDING")
                .payload(abflRequestDto).build();
    }

    private Double fetchLenderForeclosureAmount(LendingPaymentSchedule lendingPaymentSchedule) throws Exception {
        Double foreClosureAmountForABFL = loanUtil.getForeClosureAmountForABFL(lendingPaymentSchedule);
        if (foreClosureAmountForABFL <= 0) {
            log.error("previousAmount <= 0 for merchantId {}, loan : {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
            throw new Exception("Unable to fetch foreclosure amount for parent loan id " + lendingPaymentSchedule.getApplicationId());
        }
        return foreClosureAmountForABFL;
    }

}
