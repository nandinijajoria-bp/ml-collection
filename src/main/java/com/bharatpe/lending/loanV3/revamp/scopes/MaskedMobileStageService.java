package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.lending.dao.MerchantOtpRetryDao;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.entity.MerchantOtpRetry;
import com.bharatpe.lending.enums.EligibilityRequestSource;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.MaskedMobileDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.util.DateUtils;
import com.bharatpe.lending.service.APIGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Date;

@Slf4j
@Service
public class MaskedMobileStageService implements IStageDataService<MaskedMobileDTO>{
    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    MerchantOtpRetryDao merchantOtpRetryDao;

    @Value("${masked.otp.threshold:3}")
    int otpThreshold;

    @Override
    public LendingStateDTO<MaskedMobileDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        MaskedMobileDTO maskedMobileDTO = new MaskedMobileDTO();

        GlobalLimitResponse scenapticGlobalLimit = apiGatewayService.getScenapticGlobalLimit(scopeDataArgs.getMerchant().getId(),
                null,scopeDataArgs.getLoanDetailsV3Request().getAppVersion(),
                false, false,false,
                null,false, EligibilityRequestSource.EASY_LOANS);
        if(!ObjectUtils.isEmpty(scenapticGlobalLimit)
                && !ObjectUtils.isEmpty(scenapticGlobalLimit.getData())
                && !ObjectUtils.isEmpty(scenapticGlobalLimit.getData().getMaskedMobiles())){
            maskedMobileDTO.setMaskedMobileList(scenapticGlobalLimit.getData().getMaskedMobiles());
        }

        MerchantOtpRetry merchantOtpRetry = merchantOtpRetryDao.findTop1ByUserIdOrderByIdDesc(scopeDataArgs.getMerchant().getId());
        if(!ObjectUtils.isEmpty(merchantOtpRetry) && merchantOtpRetry.getRetries()>=otpThreshold && DateUtils.isSameDay(new Date(), merchantOtpRetry.getUpdatedAt())){
            maskedMobileDTO.setRetryLimitExhausted(true);
        }

        return new LendingStateDTO<>(maskedMobileDTO, LendingViewStates.OFFER_PAGE, LendingViewStates.MASKED_MOBILE);
    }

    @Override
    public LendingStateDTO<MaskedMobileDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        return fetchScopedData(scopeDataArgs);
    }



}
