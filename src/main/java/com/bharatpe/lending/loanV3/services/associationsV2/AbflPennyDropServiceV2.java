package com.bharatpe.lending.loanV3.services.associationsV2;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.ConfigResolver;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.services.gateway.AbflApiGateway;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import org.slf4j.MDC;
import lombok.extern.slf4j.Slf4j;
import javax.transaction.Transactional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.util.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;


@Service
@Slf4j
public class AbflPennyDropServiceV2 {
    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    MerchantService merchantService;

    @Autowired
    AbflApiGateway abflApiGateway;

    @Autowired
    ConfigResolver configResolver;

    @Autowired
    NbfcUtils nbfcUtils;

    @Async
    @Transactional
    public void invokePennyDrop(Map<String,String> request) {

        Optional<LendingApplication> lendingApplication = Optional.empty();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = null;

        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            log.info("Received pennydrop request:{}", request);

            lendingApplication = lendingApplicationDao.findById(Long.valueOf(request.get("application_id")));
            if (!lendingApplication.isPresent()) {
                log.info("no application found for id {}", request.get("application_id"));
                return;
            }
            lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.get().getId(), Status.ACTIVE.name(), Lender.ABFL.name());

            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("lending application lender details not found for {}", request.get("application_id"));
                return;
            }

            ABFLPennyDropRequestDTO abflPennyDropRequestDTO = createPayload(Long.valueOf(request.get("application_id")), lendingApplication.get());
            if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails) &&
                    (!abflPennyDropRequestDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) ||
                    !LenderAssociationStages.PENNY_DROP.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
                log.info("lender or stage mismatch while initiating pennydrop for application {}", abflPennyDropRequestDTO.getApplicationId());
                return;
            }

            lendingApplicationLenderDetails.setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name());

            ABFLPennyDropResponseDTO abflPennyDropResponseDTO = abflApiGateway.invokePennyDrop(abflPennyDropRequestDTO);

            log.info("pennydrop api response {}", abflPennyDropResponseDTO);

            if (!ObjectUtils.isEmpty(abflPennyDropResponseDTO)
                    && !(ObjectUtils.isEmpty(abflPennyDropResponseDTO.getData()))
                    && !(ObjectUtils.isEmpty(abflPennyDropResponseDTO.getData().getData()))
                    && ("SUCCESS".equalsIgnoreCase(abflPennyDropResponseDTO.getData().getResponseStatus()))
                    && ("SUCCESS".equalsIgnoreCase(abflPennyDropResponseDTO.getData().getData().getStatusCode()))
            ) {

                log.info("successfully placed the penny drop request at lender for {}", request);
                lendingApplicationLenderDetails.setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_SUCCESS.name());
                LenderAssociationStages nextStage = LenderAssociationStageFactory.getNextStage(Lender.ABFL,LenderAssociationStages.PENNY_DROP);
                lendingApplicationLenderDetails.setStage(nextStage.name());
                lendingApplicationDao.save(lendingApplication.get());
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);

                nbfcUtils.pushApplicationToNextStage(lendingApplication.get().getId(),
                        lendingApplication.get().getLender(), LenderAssociationStages.PENNY_DROP.name(),
                        LenderAssociationStageFactory.autoInvokeNextStage(Lender.valueOf(lendingApplication.get().getLender()), LenderAssociationStages.PENNY_DROP));
                return;
            }
            log.info("lending_application db {}", lendingApplication.get());
            log.info("lending_application_lender_details db {}", lendingApplicationLenderDetails);
            log.info("rejecting application as pennydrop is failed {}", abflPennyDropResponseDTO);

            rejectApplication(lendingApplication.get(), lendingApplicationLenderDetails, LenderAssociationStatus.PENNY_DROP_FAILED.name());
        } catch (Exception ex) {
            log.info("exception lending_application db {}", lendingApplication.get());
            log.info("exception lending_application_lender_details db {}", lendingApplicationLenderDetails);
            log.error("exception occurred while processing pennydrop request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            rejectApplication(lendingApplication.get(), lendingApplicationLenderDetails, LenderAssociationStatus.PENNY_DROP_FAILED.name());
        }

    }

    public ABFLPennyDropRequestDTO createPayload(Long applicationId, LendingApplication lendingApplication) {
        try {

            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.error("application not found !! {}", applicationId);
            }

            Optional<BankDetailsDto> merchantBankDetails = merchantService.fetchMerchantBankDetails(lendingApplication.getMerchantId());
            if (!merchantBankDetails.isPresent()) {
                throw new RuntimeException("merchant bank details not found for application");
            }
            BankDetailsDto bankDetailsDto = merchantBankDetails.get();

            ABFLPennyDropRequestDTO pennyDropRequestDTO = ABFLPennyDropRequestDTO.builder()
                    .applicationId(applicationId)
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(ABFLPennyDropRequestDTO.Payload.builder()
                            .accountId(lendingApplication.getExternalLoanId())
                            .accountNumber(bankDetailsDto.getAccountNumber())
                            .bankBranch(bankDetailsDto.getBankCode())
                            .iFSCCode(bankDetailsDto.getIfsc())
                            .bankName(bankDetailsDto.getBankName())
                            .build())
                    .build();
            log.info("abfl pennydrop payload {}", pennyDropRequestDTO);
            return pennyDropRequestDTO;
        } catch (Exception e) {
            log.error("exception occurred while initiating pennyDropRequestDTO workflow for  {}", applicationId);
        }
        return null;
    }
    private void rejectApplication(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String status) {
        log.info("rejecting application as pennydrop stage failed of ABFL for {}", lendingApplication.getId());
        if(!ObjectUtils.isEmpty(lendingApplication)) {
            log.info("lending_application not empty setting status to REJECTED");
            lendingApplication.setStatus("rejected");
            lendingApplicationDao.save(lendingApplication);
        } else {
            log.info("lending application is empty");
        }
        if(!ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("lending_application_lender_details not empty setting status to INACTIVE");
            lendingApplicationLenderDetails.setPennyDropStatus(status);
            lendingApplicationLenderDetails.setStatus(Status.INACTIVE.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        } else {
            log.info("lending_application_lender_details is empty");
        }
    }


}
