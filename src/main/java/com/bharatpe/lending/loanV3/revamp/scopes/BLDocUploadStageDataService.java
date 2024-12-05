package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dao.BLDocLenderDetailsDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.InitiateKycBLDocUploadDTO;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.entity.BLDocLenderDetails;
import com.bharatpe.lending.enums.KycDocStatus;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV3.revamp.dto.*;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BLDocUploadStageDataService implements IStageDataService<BLDocUploadStateDTO>{

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    BLDocLenderDetailsDao blDocLenderDetailsDao;
    @Autowired
    LoanDetailsV3Service loanDetailsV3Service;

    @Value("${kyc.loan.deeplink.v3}")
    private String callBackURL;

    @Override
    public LendingStateDTO<BLDocUploadStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        return fetchScopedData(scopeDataArgs);
    }

    @Override
    public LendingStateDTO<BLDocUploadStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        BLDocUploadStateDTO blDocUploadStateDTO = new BLDocUploadStateDTO();
        if(ObjectUtils.isEmpty(scopeDataArgs.getApplicationId())){
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_ID_MISSING.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_ID_MISSING.getErrorMessage());
        }

        LendingApplication lendingApplication = lendingApplicationDao.findById(scopeDataArgs.getApplicationId()).orElse(null);
        if(ObjectUtils.isEmpty(lendingApplication)) {
            log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
        }

        blDocUploadStateDTO.setApplicationId(lendingApplication.getId());
        LendingViewStates nextPage = fetchDocsAndInitiateKyc(blDocUploadStateDTO, lendingApplication);
        return new LendingStateDTO<>(blDocUploadStateDTO , nextPage, LendingViewStates.BL_DOC_UPLOAD_PAGE);
    }

    private LendingViewStates fetchDocsAndInitiateKyc(BLDocUploadStateDTO blDocUploadStateDTO, LendingApplication lendingApplication) {
        LendingViewStates nextPage = LendingViewStates.BL_DOC_UPLOAD_PAGE;
        try {
            List<String> requiredDocTypes = Optional.ofNullable(
                            getRequiredDocsForMerchant(lendingApplication.getLender()))
                    .orElse(Collections.emptyList());

            if (requiredDocTypes.isEmpty()) {
                log.info("No docs required of merchant {} for lender: {}", lendingApplication.getMerchantId(), lendingApplication.getLender());
                nextPage = LendingViewStates.KYC_PAGE;
                loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), nextPage);
            }else{
                log.info("Required docs for lender: {} {}", lendingApplication.getLender(), requiredDocTypes);
                List<KycDoc> kycDocs = kycHandler.getBusinessDocs(lendingApplication.getMerchantId());
                List<KycDoc> uploadedDocs = filterApprovedOrPendingBusinessDocs(kycDocs);
                if(uploadedDocs.isEmpty()) {
                    log.info("No docs uploaded by merchantId: {}. Initiating KYC for all required docs: {}",
                            lendingApplication.getMerchantId(), requiredDocTypes);

                    initiateKycForRequiredDocs(blDocUploadStateDTO, lendingApplication, requiredDocTypes);
                }else {
                    log.info("Docs uploaded for merchantId:{} {}", lendingApplication.getMerchantId(), uploadedDocs);
                    Map<KycDocType, KycDocStatus> uploadedDocStatus = uploadedDocs.stream()
                            .collect(Collectors.toMap(KycDoc::getSubDocType, KycDoc::getStatus));
                    boolean allApproved = requiredDocTypes.stream()
                            .allMatch(doc -> "APPROVED".equalsIgnoreCase(String.valueOf(uploadedDocStatus.get(doc))));

                    if(allApproved) {
                        log.info("All required docs are approved for applicationId: {}", lendingApplication.getId());
                        nextPage = LendingViewStates.KYC_PAGE;
                        loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), nextPage);
                    }else{
                        log.info("Initiating kyc for docs: {}", requiredDocTypes);
                        initiateKycForRequiredDocs(blDocUploadStateDTO, lendingApplication, requiredDocTypes);
                    }
                }
            }
        }catch (Exception e) {
            log.error("Exception occurred inside BLDocUploadStage for merchantId: {} {}", lendingApplication.getMerchantId(), Arrays.asList(e.getStackTrace()));
        }
        return nextPage;
    }

    private void initiateKycForRequiredDocs(BLDocUploadStateDTO blDocUploadStateDTO, LendingApplication lendingApplication, List<String> requiredDocTypes) {
        try {
            InitiateKycBLDocUploadDTO initiateKycDTO = InitiateKycBLDocUploadDTO.builder()
                    .referenceId(UUID.randomUUID().toString())
                    .docUploadCountRequiredByProduct(requiredDocTypes.size())
                    .callBackUrl(callBackURL)
                    .merchantId(lendingApplication.getMerchantId().toString()).build();

            Map<String, String> ckycResponseObj = kycHandler.initiateKycForBusinessDoc(lendingApplication.getMerchantId(), initiateKycDTO, requiredDocTypes);
            log.info("response for initiateKyc of merchantId:{} is {}", lendingApplication.getMerchantId(), ckycResponseObj);
            if (ckycResponseObj.containsKey("ckycId")) {
                blDocUploadStateDTO.setDeeplink(ckycResponseObj.getOrDefault("businessDocsUploadUrl", null));
                loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), LendingViewStates.BL_DOC_UPLOAD_PAGE);
                return;
            }
            log.error("Unable to initiate kyc for merchant :{} with error message:{}", lendingApplication.getMerchantId(), ckycResponseObj.get("message"));
            throw new LoanDetailsException(LoanDetailExceptionEnum.INITIATE_KYC_FAILED.getErrorCode(), LoanDetailExceptionEnum.INITIATE_KYC_FAILED.getErrorMessage());
        }catch (Exception e) {
            log.error("Exception while initiating kyc for bl doc upload stage of applicationId: {} {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
    }

    private List<KycDoc> filterApprovedOrPendingBusinessDocs(List<KycDoc> kycDocs) {
        if(ObjectUtils.isEmpty(kycDocs)) {
            return Collections.emptyList();
        }
        return kycDocs.stream()
                .filter(doc -> KycDocType.BUSINESSDOCS.equals(doc.getDocType())
                        && (KycDocStatus.APPROVED.equals(doc.getStatus()) || KycDocStatus.PENDING.equals(doc.getStatus())))
                .collect(Collectors.toList());
    }

    public List<String> getRequiredDocsForMerchant(String lender) {
        return blDocLenderDetailsDao.findMandatoryActiveDocsForLender(lender, "ACTIVE", true).stream()
                .sorted(Comparator.comparingInt(BLDocLenderDetails::getPriority)) // Sort by priority
                .map(BLDocLenderDetails::getDocType) // Extract document type
                .limit(2) // Take the top 2
                .collect(Collectors.toList());
    }

}
