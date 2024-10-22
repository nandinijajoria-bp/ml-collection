package com.bharatpe.lending.loanV3.services.associationsV2.wrapper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InvokeCreateLeadAndDocUploadWrapperService {
    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    CommonService commonService;

    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    AssociationServiceUtil associationServiceUtil;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Async("lenderPoolTaskExecutor")
    public void invokeCreateLeadAndDocUpload(Map<String, String> request, Map<String, Object> args) {
        try {
            List<String> stagesToBeInvokedInOrder = new ArrayList<>();
            if (!ObjectUtils.isEmpty(args)) {
                if (args.containsKey("stages")) {
                    stagesToBeInvokedInOrder = Arrays.stream(((String) args.get("stages")).split(",")).collect(Collectors.toList());
                }
                if (args.containsKey("requestId")) {
                    MDC.put("requestId", (String) args.get("requestId"));
                }
            }
            Long applicationId = Long.valueOf(request.get("application_id"));
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
            lenderAssociationDetailsRequestDto.setApplicationId(applicationId);
            runBaseChecksAndCreateRecord(lenderAssociationDetailsRequestDto);
            if (ObjectUtils.isEmpty(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails())) {
                log.info("no record found in lendingApplicationLenderDetails for applicationId {} with lender {}", applicationId, lenderAssociationDetailsRequestDto.getLendingApplication().getLender());
                MDC.clear();
                return;
            }
            log.info("base checks ran for applicationId {} with lender {}", applicationId, lenderAssociationDetailsRequestDto.getLendingApplication().getLender());
            stagesToBeInvokedInOrder = getStageToBeInvokedInOrder(lenderAssociationDetailsRequestDto.getLendingApplication().getId(), lenderAssociationDetailsRequestDto.getLendingApplication().getLender());
            lenderAssociationDetailsRequestDto.setManageState(true);
            Optional<String> failureStage = stagesToBeInvokedInOrder.stream().filter(stage -> !nbfcUtils.invokeSpecificStage(lenderAssociationDetailsRequestDto.getLendingApplication().getLender(), lenderAssociationDetailsRequestDto, stage)).findFirst();
            if (failureStage.isPresent()) {
                log.info("lender association failed at {} stage for applicationId {}  with lender {}", failureStage.get(), applicationId, lenderAssociationDetailsRequestDto.getLendingApplication().getLender());
                MDC.clear();
                return;
            }
            if(!Arrays.asList(Lender.TRILLIONLOANS.name(), Lender.MUTHOOT.name(), Lender.CREDITSAISON.name()).contains(lenderAssociationDetailsRequestDto.getLendingApplication().getLender())){
                invokeBREWorkflow(lenderAssociationDetailsRequestDto);
            }
            MDC.clear();
        } catch (Exception e) {
            log.info("Exception in invoking createLead and doc upload flow for applicationId : {} {}", request.get("application_id"), Arrays.asList(e.getStackTrace()));
            MDC.clear();
        }
    }

    public void invokeBREWorkflow(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        log.info("Pushing application to BRE stage for : {} {}", lenderAssociationDetailsDto.getApplicationId(), lenderAssociationDetailsDto);
        String currStage = lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getStage();
        LenderAssociationStages nextStage =
                LenderAssociationStageFactoryV2.getNextStage(Lender.valueOf(lenderAssociationDetailsDto.getLendingApplication().getLender()),
                        LenderAssociationStages.valueOf(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getStage()));
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setStage(nextStage.name());
        commonService.manageApplicationState(lenderAssociationDetailsDto);
        nbfcUtils.pushApplicationToNextStage(lenderAssociationDetailsDto.getApplicationId(),
                lenderAssociationDetailsDto.getLendingApplication().getLender(),
                currStage,
                Boolean.TRUE
        );
    }


    public void runBaseChecksAndCreateRecord(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(lenderAssociationDetailsDto.getApplicationId());
        log.info("lending application {}", lendingApplication.get());
        if (ObjectUtils.isEmpty(lendingApplication.get())) {
            log.info("no application found for {}", lenderAssociationDetailsDto.getApplicationId());
            return;
        }
        lenderAssociationDetailsDto.setLendingApplication(lendingApplication.get());
        lenderAssociationDetailsDto.setMerchantId(lendingApplication.get().getMerchantId());
        lenderAssociationDetailsDto.setModifyLender(enableLenderChange);
        lenderAssociationDetailsDto.setManageState(Boolean.TRUE);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao
                .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lenderAssociationDetailsDto.getApplicationId(), Status.ACTIVE.name(), lendingApplication.get().getLender());
        if (Objects.nonNull(lendingApplicationLenderDetails) && Objects.nonNull(lendingApplicationLenderDetails.getLeadId())) {
            log.info("lead creation already done for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
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
        switch (stage) {
            case "CREATE_LEAD":
                return associationServiceUtil.invokeCreateLeadService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            //case "PAN_UPLOAD":
            case "AADHAR_UPLOAD":
            case "SELFIE_UPLOAD":
            case "SHOP_PHOTO_UPLOAD":
            case "SHOP_STOCK_PHOTO_UPLOAD":
                return associationServiceUtil.invokeDocUploadService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto, stage);
            case "CREATE_CLIENT" :
                return associationServiceUtil.invokeCreateClientService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "KYC":
                return associationServiceUtil.invokeKycService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "UPDATE_LEAD":
                return associationServiceUtil.invokeLeadUpdateService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            default:
                return true;
        }
    }

    public static boolean kycDataNeeded(String stage) {
        switch (stage) {
            case "CREATE_LEAD":
            case "AADHAR_UPLOAD":
            case "SELFIE_UPLOAD":
            case "UPDATED_LEAD":
            case "CREATE_CLIENT":
                return true;
            default:
                return false;
        }
    }

    public List<String> getStageToBeInvokedInOrder(Long applicationId, String lender) {
        log.info("Getting stages to be invoked in createLead and docUpload for applicationId  {} and lender {} ", applicationId, lender);
        if (ObjectUtils.isEmpty(lender)) {
            throw new RuntimeException("Invalid lender " + lender + " for applicationId " + applicationId);
        }
        switch (Lender.valueOf(lender)) {
            case USFB:  //TODO Add PAN in list if required
                return Arrays.asList(LenderAssociationStages.CREATE_LEAD.name(), LenderAssociationStages.AADHAR_UPLOAD.name(),
                        LenderAssociationStages.SELFIE_UPLOAD.name(), LenderAssociationStages.SHOP_PHOTO_UPLOAD.name());
            case TRILLIONLOANS:
                return Arrays.asList(LenderAssociationStages.CREATE_CLIENT.name(), LenderAssociationStages.CREATE_LEAD.name(),
                        LenderAssociationStages.SELFIE_UPLOAD.name(), LenderAssociationStages.AADHAR_UPLOAD.name());
            case MUTHOOT:
                return Arrays.asList(LenderAssociationStages.CREATE_LEAD.name(), LenderAssociationStages.UPDATE_LEAD.name(),
                        LenderAssociationStages.AADHAR_UPLOAD.name(), LenderAssociationStages.KYC.name());
            case CAPRI:
                return Arrays.asList(LenderAssociationStages.CREATE_CLIENT.name(), LenderAssociationStages.CREATE_LEAD.name(),LenderAssociationStages.AADHAR_UPLOAD.name(),
                        LenderAssociationStages.SELFIE_UPLOAD.name());
            case PAYU:
                return Arrays.asList(LenderAssociationStages.CREATE_LEAD.name(), LenderAssociationStages.UPDATE_LEAD.name(), LenderAssociationStages.AADHAR_UPLOAD.name(), LenderAssociationStages.SHOP_PHOTO_UPLOAD.name(),
                        LenderAssociationStages.SHOP_STOCK_PHOTO_UPLOAD.name(), LenderAssociationStages.SELFIE_UPLOAD.name(), LenderAssociationStages.KYC.name());
            case CREDITSAISON:
                return Arrays.asList(LenderAssociationStages.CREATE_CLIENT.name(), LenderAssociationStages.KYC.name());
            default:
                throw new RuntimeException("Invalid lender " + lender + " for applicationId " + applicationId);
        }
    }

}
