package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.EasyLoanUtil;
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
    LoanUtil loanUtil;

    @Value("${enable.bl.tagging:true}")
    Boolean blTaggingEnabled;

    @Value("${bl.eligible.lenders:IIFL}")
    String blEligibleLendersList;

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
        }
        else lendingStateDTO.setLendingViewStates(LendingViewStates.KYC_PAGE);
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
                if(LenderAssociationStages.LENDER_CHANGE.name().equals(lendingApplicationDetails.getStage()) && !ObjectUtils.isEmpty(loanUtil.getLenderAggregationScreen(lendingApplication.getId()))){
                    return new LendingStateDTO<>(shopPicturesStateDTO , LendingViewStates.LENDER_AGGREGATION, LendingViewStates.SHOP_PICTURES_PAGE);
                }
                if(blTaggingEnabled && blEligibleLendersList.contains(lendingApplication.getLender()) && !lendingApplicationDetails.getIsDocSkip()) {
                    return new LendingStateDTO<>(shopPicturesStateDTO , LendingViewStates.BL_DOC_UPLOAD_PAGE, LendingViewStates.SHOP_PICTURES_PAGE);
                }
            }
            shopPicturesStateDTO.setLenderKycPipe(kycUtils.isELigibleForLenderKyc(lendingApplication.getLender(), lendingApplication.getMerchantId()));

            if(easyLoanUtil.isDummyMerchant(scopeDataArgs.getMerchant().getId()))shopPicturesStateDTO.setDummyMerchant(true);
            scopeDataArgs.setApplicationId(lendingApplication.getId());
            shopPicturesStateDTO.setApplicationId(lendingApplication.getId());
            shopPicturesStateDTO.setLender(lendingApplication.getLender());

            LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationId(lendingApplication.getId());
            if(lendingResubmitTask != null && lendingResubmitTask.getResubmit() && !lendingResubmitTask.getResubmitDone()){
                shopPicturesStateDTO.setResubmitState(true);
            }

            return new LendingStateDTO<>(shopPicturesStateDTO , LendingViewStates.SHOP_PICTURES_PAGE, LendingViewStates.SHOP_PICTURES_PAGE);
        } catch (Exception e) {
            log.info("Error while fetching KFS stage data for {}, {}, {}", scopeDataArgs.getMerchant().getMobile(),
                    e.getMessage(), Arrays.asList(e.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
    }
}
