package com.bharatpe.lending.loanV3.services.associationsV2.sib;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.collection.core.utils.LoanPaymentUtil;
import com.bharatpe.lending.common.dao.LendingCollectionAuditDao;
import com.bharatpe.lending.common.entity.LendingCollectionAudit;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.sib.SibForeclosureRequest;
import com.bharatpe.lending.loanV3.dto.request.sib.SibRepaymentRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.sib.SibForeclosureResponse;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;

@Slf4j
@Service
public class SibForeclosureService {
    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${sib.foreclosure.details.timeout.threshold:20000}")
    Integer sibForeclosureDetailsTimeoutThreshold;

    @Value("${sib.program.reference.number:}")
    String programReferenceNumber;

    @Value("${sib.npos.config.id:}")
    int nposConfigId;

    public static final String BHARATPE = "BHARATPE";
    private static final String PRODUCT_NAME = "LENDING";
    private static final String DATE_TIME_FORMAT = "dd-MM-yyyy";


    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingCollectionAuditDao lendingCollectionAuditDao;


    public Double getForeclosureDetails(Long applicationId) {
        try {
        LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("lender record not exist of SIB for {}", applicationId);
            return 0D;
        }

        if (ObjectUtils.isEmpty(lendingApplication.getNbfcId())) {
            log.info("NbfcId not found for SIB application {}", applicationId);
            return 0D;
        }

            NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                    .productName("LENDING")
                    .lender(Lender.SIB.name())
                    .applicationId(applicationId)
                    .payload(SibForeclosureRequest.builder()
                            .nposConfigId(nposConfigId)
                            .investorLoanId(lendingApplication.getNbfcId())
                            .build())
                    .build();

            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(
                    nbfcRequestDto,
                    LenderAssociationStages.FORECLOSURE_FETCH,
                    sibForeclosureDetailsTimeoutThreshold
            );

            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                SibForeclosureResponse response = objectMapper.readValue(
                        objectMapper.writeValueAsString(nbfcResponseDto.getData()),
                        SibForeclosureResponse.class
                );
                if (!ObjectUtils.isEmpty(response.getData()) && !ObjectUtils.isEmpty(response.getData().getForeclosureAmount())) {
                    return response.getData().getForeclosureAmount();
                }
            }
        } catch (Exception e) {
            log.info("exception occurred while parsing response data of SIB foreclosure details for {} {}, {}",
                    applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return 0D;
    }

    public NBFCRequestDTO getForeclosureReceiptRequest(Long applicationId, LendingLedger lendingLedger) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("lender record not exist of SIB for {}", applicationId);
                return null;
            }

            LendingCollectionAudit lendingCollectionAudit = lendingCollectionAuditDao.findByLedgerID(lendingLedger.getId(), 1);

            if (ObjectUtils.isEmpty(lendingApplication.getNbfcId())) {
                log.info("NbfcId not found for SIB application {}", applicationId);
                return null;
            }

            String utrNumber = ObjectUtils.isEmpty(lendingLedger.getTerminalOrderId())
                    ? String.valueOf(lendingLedger.getId())
                    : lendingLedger.getTerminalOrderId();
            String idempotencyKey = generateIdempotencyKey(lendingLedger);
            String transactionDate = DateTimeUtil.getDateInFormat(LoanPaymentUtil.getNonFutureTransactionDate(lendingLedger.getDate()), DATE_TIME_FORMAT);
            LinkedHashMap<String, Object> identifier = new LinkedHashMap<>();
            identifier.put("ledgerId", lendingLedger.getId());
            if(!ObjectUtils.isEmpty(lendingCollectionAudit) && !ObjectUtils.isEmpty(lendingCollectionAudit.getId()))
            identifier.put("bpReferenceId", lendingCollectionAudit.getId());
            else
                identifier.put("bpReferenceId", 0);
            identifier.put("lenderReferenceId", utrNumber);

            SibRepaymentRequestDTO.CollectionRecord collectionRecord = SibRepaymentRequestDTO.CollectionRecord.builder()
                    .partnerName(BHARATPE)
                    .programReference(programReferenceNumber)
                    .sibLoanId(lendingApplication.getNbfcId())
                    .bharatpeLoanId(lendingApplication.getExternalLoanId())
                    .collectionReferenceId(utrNumber)
                    .collectionDate(transactionDate)
                    .foreclosureDate(transactionDate)
                    .modeOfCollection(lendingLedger.getAdjustmentMode())
                    .amountCollected(fmtAmount(lendingLedger.getAmount()))
                    .principalCollected(fmtAmount(lendingLedger.getPrinciple()))
                    .interestCollected(fmtAmount(lendingLedger.getInterest()))
                    .penaltyCollected("0")
                    .chargeCollected("0")
                    .loanStatus("C")
                    .build();

            SibRepaymentRequestDTO.Collection collection = SibRepaymentRequestDTO.Collection.builder()
                    .records(Collections.singletonList(collectionRecord))
                    .build();

            SibRepaymentRequestDTO.RequestData requestData = SibRepaymentRequestDTO.RequestData.builder()
                    .collection(collection)
                    .build();

            return NBFCRequestDTO.builder()
                    .lender(Lender.SIB.name())
                    .productName(PRODUCT_NAME)
                    .applicationId(applicationId)
                    .payload(
                        SibRepaymentRequestDTO.builder()
                            .requestData(requestData)
                            .nposConfigId(nposConfigId)
                            .originatorName(BHARATPE)
                            .clientRequestId(idempotencyKey)
                            .build()
                    )
                    .identifier(identifier)
                    .build();

        } catch (Exception e) {
            log.info("Exception in generating foreclosure receipt payload of SIB for {}, {}, {}",
                    applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public String fmtAmount(Double value) {
        return value != null ? BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString() : "";
    }

    private String generateIdempotencyKey(LendingLedger lendingLedger) {
        String terminalOrderId = lendingLedger.getTerminalOrderId() == null ? "" : lendingLedger.getTerminalOrderId();
        return String.format("%d_%s", lendingLedger.getId(), terminalOrderId);
    }
}
