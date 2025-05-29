package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV3.revamp.dto.*;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class ShopPicturesStageDataService implements IStageDataService<ShopPicturesStateDTO>{

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Autowired
    private LoanDetailsV3Service loanDetailsV3Service;

    @Autowired
    LendingResubmitTaskDao lendingResubmitTaskDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Lazy
    @Autowired
    KycUtils kycUtils;

    @Autowired
    private KycHandler kycHandler;

    @Autowired
    LoanUtil loanUtil;

    @Value("${enable.bl.tagging:true}")
    Boolean blTaggingEnabled;

    @Value("${bl.eligible.lenders:IIFL}")
    String blEligibleLendersList;

    @Value("${shop.photo.sync.rollout:0}")
    private Integer shopPhotoSyncRollout;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Override
    public LendingStateDTO<ShopPicturesStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<ShopPicturesStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        if (LendingViewStates.LENDER_AGGREGATION.equals(lendingStateDTO.getLendingViewStates())){
            return lendingStateDTO;
        }

        if(Objects.nonNull(scopeDataArgs.getLoanDetailsV3Request().getResubmitReason())){
            lendingStateDTO.setLendingViewStates(LendingViewStates.SHOP_PICTURES_PAGE);
        }
        else if(LendingViewStates.BL_DOC_UPLOAD_PAGE.equals(lendingStateDTO.getLendingViewStates())) {
            return lendingStateDTO;
        }else if (!hasShopPictureAndShopStockImageByMerchantIdAndApplicationId(lendingStateDTO.getData().getMerchantId(),lendingStateDTO.getData().getApplicationId())) {
            log.info("one picture is missing of shop for merchantId : {} and applicationId : {}",lendingStateDTO.getData().getMerchantId(),lendingStateDTO.getData().getMerchantId());
        }
        else{
            lendingStateDTO.setLendingViewStates(LendingViewStates.KYC_PAGE);
            if(Objects.nonNull(scopeDataArgs.getLoanDetailsV3Request())){
                log.info("loanDetails v3 request : {}", scopeDataArgs.getLoanDetailsV3Request());
                if(scopeDataArgs.getLoanDetailsV3Request().isShopPhotoStepCompleted() && easyLoanUtil.percentScaleUp(scopeDataArgs.getMerchant().getId(), shopPhotoSyncRollout ) ){
                    kycHandler.syncShopPhoto(scopeDataArgs.getMerchant().getId(), scopeDataArgs.getApplicationId());
                }
                if (scopeDataArgs.getLoanDetailsV3Request().isShopPhotoStepCompleted()) {
                    LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
                    log.info("publishing data to ds in loanDetailV3 for application : {}", lendingApplication.getId());
                    loanUtil.publishDSData(lendingApplication);
                }
            }
            else{
                log.info("loanDetails v3 request is null");
            }



        }
        if(!lendingStateDTO.getData().getResubmitState()){
            loanDetailsV3Service.saveApplicationViewState(null, scopeDataArgs.getApplicationId(), LendingViewStates.SHOP_PICTURES_PAGE);
        }
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<ShopPicturesStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        ShopPicturesStateDTO shopPicturesStateDTO = new ShopPicturesStateDTO();

        try {
            shopPicturesStateDTO.setMerchantId(scopeDataArgs.getMerchant().getId());
            LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
            }

            LendingApplicationDetails lendingApplicationDetails =
                    lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            if (!ObjectUtils.isEmpty(lendingApplicationDetails)) {
                log.info("lender assc for {} {}", lendingApplicationDetails.getLenderAssc(), lendingApplicationDetails.getApplicationId());
                shopPicturesStateDTO.setLenderAssc(Optional.ofNullable(lendingApplicationDetails.getLenderAssc()).orElse(false));
                if (!hasShopPictureAndShopStockImageByMerchantIdAndApplicationId(lendingApplication.getId(),scopeDataArgs.getMerchant().getId())) {
                    log.info("already, one picture is missing of shop for merchantId : {} and applicationId : {}",scopeDataArgs.getApplicationId(),scopeDataArgs.getMerchant().getId());
                }else {
                    log.info("both shop image for merchantId : {} and applicationId : {}",scopeDataArgs.getApplicationId(),scopeDataArgs.getMerchant().getId());
                    if (LenderAssociationStages.LENDER_CHANGE.name().equals(lendingApplicationDetails.getStage()) && !ObjectUtils.isEmpty(loanUtil.getLenderAggregationScreen(lendingApplication.getId()))) {
                        log.info("LENDER_CHANGE, merchantId : {} and applicationId : {}",scopeDataArgs.getApplicationId(),scopeDataArgs.getMerchant().getId());
                        return new LendingStateDTO<>(shopPicturesStateDTO, LendingViewStates.LENDER_AGGREGATION, LendingViewStates.SHOP_PICTURES_PAGE);
                    }
                    if (blTaggingEnabled && blEligibleLendersList.contains(lendingApplication.getLender()) && !lendingApplicationDetails.getIsDocSkip()) {
                        log.info("blTaggingEnabled & BL_DOC_UPLOAD_PAGE, merchantId : {} and applicationId : {}",scopeDataArgs.getApplicationId(),scopeDataArgs.getMerchant().getId());
                        return new LendingStateDTO<>(shopPicturesStateDTO, LendingViewStates.BL_DOC_UPLOAD_PAGE, LendingViewStates.SHOP_PICTURES_PAGE);
                    }
                }
            }

            shopPicturesStateDTO.setLenderKycPipe(kycUtils.isELigibleForLenderKyc(lendingApplication.getLender(), lendingApplication.getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())));

            if(easyLoanUtil.isDummyMerchant(scopeDataArgs.getMerchant().getId()))shopPicturesStateDTO.setDummyMerchant(true);
            scopeDataArgs.setApplicationId(lendingApplication.getId());
            shopPicturesStateDTO.setApplicationId(lendingApplication.getId());
            shopPicturesStateDTO.setLender(lendingApplication.getLender());

            LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationId(lendingApplication.getId());
            if(lendingResubmitTask != null && lendingResubmitTask.getResubmit() && !lendingResubmitTask.getResubmitDone()){
                shopPicturesStateDTO.setResubmitState(true);
            }
            log.info("last line print");
            return new LendingStateDTO<>(shopPicturesStateDTO , LendingViewStates.SHOP_PICTURES_PAGE, LendingViewStates.SHOP_PICTURES_PAGE);
        } catch (Exception e) {
            log.info("Error while fetching KFS stage data for {}, {}, {}", scopeDataArgs.getMerchant().getMobile(),
                    e.getMessage(), Arrays.asList(e.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
    }

    /**
     * Checks if the lending shop documents for the given merchant and application are valid.
     * A valid set of documents is defined by the following criteria:
     * <ul>
     *     <li>There must be at least 2 documents in total.</li>
     *     <li>At least one document must have the proof type "shop-front".</li>
     *     <li>At least one document must have the proof type "shop-stock".</li>
     * </ul>
     *
     * @param merchantId the ID of the merchant whose documents are being checked.
     * @param applicationId the ID of the application for which the documents are being checked.
     * @return {@code true} if the lending shop documents are valid (i.e., meet the above criteria),
     *         otherwise {@code false}.
     */
    public boolean hasShopPictureAndShopStockImageByMerchantIdAndApplicationId(Long merchantId, Long applicationId) {
        log.info("Shop Picture And Shop Stock Image by merchantId : {} and applicationId : {}",merchantId,applicationId);
     return lendingShopDocumentsDao.hasValidProofTypes(merchantId, applicationId) == 1;
    }
}
