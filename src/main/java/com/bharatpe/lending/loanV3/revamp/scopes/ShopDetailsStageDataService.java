package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingMerchantDetailsDao;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.entity.LendingMerchantDetails;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.loanV3.revamp.dto.*;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class ShopDetailsStageDataService implements IStageDataService<ShopDetailsStateDTO>{

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LendingMerchantDetailsDao lendingMerchantDetailsDao;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Autowired
    LoanDetailsV3Service loanDetailsV3Service;

    @Override
    public LendingStateDTO<ShopDetailsStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<ShopDetailsStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        if(Objects.nonNull(scopeDataArgs.getLoanDetailsV3Request().getResubmitReason())){
            lendingStateDTO.setLendingViewStates(LendingViewStates.SHOP_DETAILS_PAGE);
        }
        else lendingStateDTO.setLendingViewStates(LendingViewStates.SHOP_PICTURES_PAGE);
        if(Objects.nonNull(scopeDataArgs.getApplicationId())){
            loanDetailsV3Service.saveApplicationViewState(null, scopeDataArgs.getApplicationId(), LendingViewStates.SHOP_DETAILS_PAGE);
        }
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<ShopDetailsStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        ShopDetailsStateDTO shopDetailsStateDTO = new ShopDetailsStateDTO();
        shopDetailsStateDTO.setDummyMerchant(easyLoanUtil.isDummyMerchant(scopeDataArgs.getMerchant().getId()));
        LendingMerchantDetails lendingMerchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(scopeDataArgs.getMerchant().getId());
        if (Objects.nonNull(lendingMerchantDetails)) {
            shopDetailsStateDTO.setBusinessName(lendingMerchantDetails.getBusinessName());
            shopDetailsStateDTO.setBusinessCategory(lendingMerchantDetails.getBusinessCategory());
            shopDetailsStateDTO.setBusinessSubCategory(lendingMerchantDetails.getBusinessSubCategory());
        }

        Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
        if (experian != null) {
            shopDetailsStateDTO.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
        }

        LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
            return new LendingStateDTO<>(shopDetailsStateDTO , LendingViewStates.SHOP_DETAILS_PAGE, LendingViewStates.SHOP_DETAILS_PAGE);
        }
        shopDetailsStateDTO.setApplicationId(lendingApplication.getId());
        shopDetailsStateDTO.setApplicationStatus(lendingApplication.getStatus().toLowerCase());

        return new LendingStateDTO<>(shopDetailsStateDTO , LendingViewStates.SHOP_DETAILS_PAGE, LendingViewStates.SHOP_DETAILS_PAGE);
    }
}
