package com.bharatpe.lending.collection.core.service;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingPullPaymentDao;
import com.bharatpe.lending.common.dto.ResponseDTO;
import com.bharatpe.lending.common.entity.LendingPullPayment;
import com.bharatpe.lending.common.query.dao.LendingPullPaymentDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPullPaymentSlave;
import com.bharatpe.lending.dto.PgMandateExecutionResponse;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.service.APIGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class MandateCancellationService {

    private static final String DEFAULT_LOAN_CLOSURE_CANCELLATION_REASON = "Cancelled due to loan closure";

    @Autowired
    private LendingPullPaymentDao lendingPullPaymentDao;

    @Autowired
    private LendingPullPaymentDaoSlave lendingPullPaymentDaoSlave;

    @Autowired
    private APIGatewayService apiGatewayService;

    public void cancelPendingMandateExecutions(LendingPaymentSchedule loan) {
        cancelPendingMandateExecutions(loan, DEFAULT_LOAN_CLOSURE_CANCELLATION_REASON);
    }

    public void cancelPendingMandateExecutions(LendingPaymentSchedule loan, String purpose) {
        String cancellationReason = StringUtils.hasText(purpose) ? purpose.trim() : DEFAULT_LOAN_CLOSURE_CANCELLATION_REASON;
        try {
            if ("ACTIVE".equalsIgnoreCase(loan.getStatus())) {
                log.info("Skipping cancel mandate execution - loan status is {} for loanId:{}", loan.getStatus(), loan.getId());
                return;
            }

            Date createdAfter = new Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000L));
            List<LendingPullPaymentSlave> pendingExecutions = lendingPullPaymentDaoSlave.findPendingPullPaymentsByLoanIdCreatedAfter(loan.getId(), createdAfter);
            if (CollectionUtils.isEmpty(pendingExecutions)) {
                log.info("No pending UPI autopay mandate executions found for loanId:{}", loan.getId());
                return;
            }
            log.info("Found {} pending UPI autopay mandate executions for loanId:{}, loanStatus:{}", pendingExecutions.size(), loan.getId(), loan.getStatus());

            Lender lender = Lender.valueOf(loan.getNbfc());
            for (LendingPullPaymentSlave pendingExecution : pendingExecutions) {
                String pgOrderId = "LENDING" + pendingExecution.getId();
                log.info("Cancelling mandate execution for loanId:{}, pullPaymentId:{}, pgOrderId:{}, merchantId:{}", loan.getId(), pendingExecution.getId(), pgOrderId, loan.getMerchantId());

                Optional<LendingPullPayment> opt = lendingPullPaymentDao.findById(pendingExecution.getId());
                if (!opt.isPresent()) {
                    log.warn("Pull payment {} not found for loanId:{}, skipping", pendingExecution.getId(), loan.getId());
                    continue;
                }
                LendingPullPayment pullPayment = opt.get();
                if (!"PENDING".equals(pullPayment.getStatus())) {
                    log.info("Pull payment {} already in status {} for loanId:{}, skipping cancel", pendingExecution.getId(), pullPayment.getStatus(), loan.getId());
                    continue;
                }

                if (isDigioProvider(pullPayment.getProvider())) {
                    cancelDigioPendingMandateExecutions(pullPayment, loan.getId(), cancellationReason);
                    continue;
                }

                PgMandateExecutionResponse response = apiGatewayService.cancelMandateExecution(pgOrderId, loan.getMerchantId(), lender);
                boolean isCancelled = response != null && "200".equals(response.getStatusCode())
                        && response.getData() != null && "CLIENT_CANCELLED".equalsIgnoreCase(response.getData().getStatus());

                if (isCancelled) {
                    pullPayment.setStatus("CANCELLED");
                    pullPayment.setErrorDescription(cancellationReason);
                } else {
                    pullPayment.setErrorCode(response != null ? response.getStatusCode() : "500");
                    pullPayment.setErrorDescription(response != null && response.getData() != null
                            ? response.getData().getMessage() : (response != null ? response.getMessage() : "No response from PG"));
                }
                lendingPullPaymentDao.save(pullPayment);
                log.info("Cancel mandate execution result - loanId:{}, pullPaymentId:{}, pgOrderId:{}, cancelled:{}, pgStatus:{}, response:{}",
                        loan.getId(), pullPayment.getId(), pgOrderId, isCancelled, response != null && response.getData() != null ? response.getData().getStatus() : null, response);
            }
        } catch (Exception e) {
            log.error("Error cancelling pending mandate executions for loanId:{}, error:{}", loan.getId(), e.getMessage(), e);
        }
    }

    private static boolean isDigioProvider(String provider) {
        return provider != null && "DIGIO".equalsIgnoreCase(provider.trim());
    }

    private void cancelDigioPendingMandateExecutions(LendingPullPayment pullPayment, Long loanId, String cancellationReason) {
        if (pullPayment.getNachTransactionId() == null) {
            log.info("Skipping Digio presentation cancel for pullPaymentId:{} loanId:{} due to missing nach_transaction_id", pullPayment.getId(), loanId);
            return;
        }

        ResponseDTO<Map<String, Object>> response = apiGatewayService.cancelDigioPresentmentOnForeclosure(pullPayment.getNachTransactionId());

        boolean isCancelled = response != null && response.isSuccess();
        if (isCancelled) {
            pullPayment.setStatus("CANCELLED");
            pullPayment.setErrorDescription(cancellationReason);
            lendingPullPaymentDao.save(pullPayment);
        }
        log.info("Digio presentation cancel result - loanId:{}, pullPaymentId:{}, nachTransactionId:{}, cancelled:{}, response:{}", loanId, pullPayment.getId(), pullPayment.getNachTransactionId(), isCancelled, response);
    }
}
