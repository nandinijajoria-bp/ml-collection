package com.bharatpe.lending.loanV3.services.associationsV2.piramal.wrapper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.CreateLeadService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.PiramalDocumentUploadService;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InvokeCreateLeadAndDocUploadWraperService {
    @Autowired
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    CreateLeadService createLeadService;

    @Autowired
    PiramalDocumentUploadService uploadDocumentService;

    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingGstDao lendingGstDao;

    @Autowired
    UpdateLeadAndRiskDecisionWrapperService updateLeadAndRiskDecisionWrapperService;

    @Autowired
    CommonService commonService;

    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Value("${lender.change.enabled:false}")
    private Boolean enableLenderChange;

    @Async("piramalPoolTaskExecutor")
    public void invokeCreateLeadAndDocUpload(Map<String, String> request, Map<String, Object> args) {
        List<String> stagesToBeInvokedInOrder = Arrays.asList(LenderAssociationStages.PiramalAssociationStages.LEAD_CREATION.name(),
                LenderAssociationStages.PiramalAssociationStages.AADHAR_UPLOAD.name(), LenderAssociationStages.PiramalAssociationStages.SELFIE_UPLOAD.name());
        if (null != args) {
            if (args.containsKey("stages")) {
                stagesToBeInvokedInOrder = Arrays.stream(((String) args.get("stages"))
                        .split(",")).collect(Collectors.toList());
            }
            if (args.containsKey("requestId")) {
                MDC.put("requestId", (String) args.get("requestId"));
            }
        }
        Long applicationId = Long.valueOf(request.get("application_id"));
        // base check and create record if dne
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequestDto.setApplicationId(applicationId);
        runBaseChecksAndCreateRecord(lenderAssociationDetailsRequestDto);
        if (ObjectUtils.isEmpty(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails())) {
            log.info("no record found for lendingApplicationLenderDetails for {}", applicationId);
            MDC.clear();
            return;
        }
        log.info("base checks ran for {}", applicationId);
        lenderAssociationDetailsRequestDto.setManageState(true);
        Optional<String> failureStage = stagesToBeInvokedInOrder.stream().filter(stage -> !invokeStage(lenderAssociationDetailsRequestDto, stage)).findFirst();
        if (failureStage.isPresent()) {
            log.info("lender assc failed at {} stage for  {}", failureStage.get(), applicationId);
            MDC.clear();
            return;
        }
        // update the record and check for gst details (if submitted trigger update workflow )
        checkForGSTDetailsAndInvokeBREWorkflow(lenderAssociationDetailsRequestDto);
        MDC.clear();
    }

    private void checkForGSTDetailsAndInvokeBREWorkflow(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lenderAssociationDetailsDto.getApplicationId());
        if (checkForGSTDetails(lenderAssociationDetailsDto.getApplicationId()) &&
                Boolean.TRUE.equals(lendingApplicationDetails.getCurrentAddressSameAsPermanentAddress())) {
            // push application to next stage
            //update lendingApplicationLenderDetails
            String currStage =  lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getStage();
            LenderAssociationStages nextStage =
                    LenderAssociationStageFactory.getNextStage(Lender.valueOf(lenderAssociationDetailsDto.getLendingApplication().getLender()),
                            LenderAssociationStages.valueOf(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getStage()));
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setStage(nextStage.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            nbfcUtils.pushApplicationToNextStage(lenderAssociationDetailsDto.getApplicationId(),
                    lenderAssociationDetailsDto.getLendingApplication().getLender(),
                    currStage,
                    Boolean.TRUE
            );
        } else if (Boolean.FALSE.equals(lendingApplicationDetails.getCurrentAddressSameAsPermanentAddress())) {
            log.info("modifying lender as permanent address is different that current address {}", lenderAssociationDetailsDto.getApplicationId());
            nbfcUtils.modifyLender(lenderAssociationDetailsDto.getLendingApplication(), lenderAssociationDetailsDto.getLendingApplicationLenderDetails(), LenderAssociationStatus.BRE_HARD_FAILED);
        }
    }

    private boolean checkForGSTDetails(Long applicationId) {
        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(applicationId);
        return (lendingGstDetail != null && null != lendingGstDetail.getShopType());
    }

    private void runBaseChecksAndCreateRecord(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(lenderAssociationDetailsDto.getApplicationId());
        log.info("lending app  {}", lendingApplication.get());
        if (!lendingApplication.isPresent()
            // TODO: 04/04/23 uncomment this later
//                || !Lender.PIRAMAL.name().equalsIgnoreCase(lendingApplication.get().getLender())
        ) {
            log.info("no application found  or lender {}  mismatch for {}", lendingApplication.get().getLender(), lenderAssociationDetailsDto.getApplicationId());
            return;
        }
        lenderAssociationDetailsDto.setLendingApplication(lendingApplication.get());
        lenderAssociationDetailsDto.setMerchantId(lendingApplication.get().getMerchantId());
        lenderAssociationDetailsDto.setModifyLender(enableLenderChange);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao
                .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lenderAssociationDetailsDto.getApplicationId(), Status.ACTIVE.name(),Lender.PIRAMAL.name());
        if (Objects.nonNull(lendingApplicationLenderDetails) && Objects.nonNull(lendingApplicationLenderDetails.getLeadId())) {
            log.info("lead creation already done for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
            return;
        }
        if (null == lendingApplicationLenderDetails) {
            lendingApplicationLenderDetails = createLenderRecord(lendingApplication.get(), LenderAssociationStages.KYC.name(), Status.ACTIVE.name());
        }
        lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
    }

    private LendingApplicationLenderDetails createLenderRecord(LendingApplication lendingApplication, String stage, String recordStatus) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails;
        lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(lendingApplication.getId());
        lendingApplicationLenderDetails.setLender(lendingApplication.getLender());
        lendingApplicationLenderDetails.setStage(stage);
        lendingApplicationLenderDetails.setStatus(recordStatus);
        lendingApplicationLenderDetails.setAccountId(lendingApplication.getExternalLoanId());
        DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(RoundingMode.DOWN);
        lendingApplicationLenderDetails.setAnnualRoi(Double.valueOf(df.format(
                lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getLoanAmount(),
                        LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().getNoOfEdiDaysInAWeek(), lendingApplication.getLender()))));
        return lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
    }


    private boolean invokeStage(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, String stage) {
        // fetch kyc data id dne
        switch (stage) {
            case "LEAD_CREATION":
                return createLeadService.invokeCreateLead(lenderAssociationDetailsDto);
            case "AADHAR_UPLOAD":
            case "SELFIE_UPLOAD":
                return uploadDocumentService.invokeDocUpload(lenderAssociationDetailsDto, stage);
            default:
                return true;
        }
    }

    public static boolean kycDataNeeded(String stage) {
        switch (stage) {
            case "LEAD_CREATION":
            case "AADHAR_UPLOAD":
            case "SELFIE_UPLOAD":
                return true;
            default:
                return false;
        }
    }
}
