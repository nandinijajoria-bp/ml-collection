package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.enums.MandateType;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import com.bharatpe.lending.loanV3.services.VKycService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;


@Service
@RequiredArgsConstructor
public class KfsStageHelper {

    private final VKycService vkycService;

    private final List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());

    public LendingViewStates getNextViewState(LendingApplication lendingApplication, LendingApplicationDetails lendingApplicationDetails) {
        return getNextViewState(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getLender(),
                lendingApplicationDetails.getMandateType(),
                NachStatus.APPROVED.name().equals(lendingApplication.getNachStatus()),
                NachStatus.APPROVED.name().equals(lendingApplication.getUpiAutopayStatus()),
                topupLoans.contains(lendingApplication.getLoanType()));
    }


    public LendingViewStates getNextViewState(Long merchantId, Long applicationId, String lender,
                                               MandateType mandateType, boolean isNachDone, boolean isUpiAutoPayDone, boolean isTopup) {
        if(isTopup){
            return vkycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, merchantId, lender, true);
        }
        if(MandateType.BOTH.equals(mandateType)){
            if(isUpiAutoPayDone && isNachDone){
                return vkycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, merchantId, lender, false);
            }else if(isUpiAutoPayDone){
                return LendingViewStates.ENACH_PAGE;
            }else {
                return LendingViewStates.UPI_AUTOPAY_PAGE;
            }
        }else if(MandateType.UPIAUTOPAY.equals(mandateType)) {
            if (isUpiAutoPayDone) {
                return vkycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, merchantId, lender, false);
            } else {
                return LendingViewStates.UPI_AUTOPAY_PAGE;
            }
        }else if(MandateType.DIGIO_UPI.equals(mandateType)){
            // as no skip case as of now for digio upi
            // TODO write digio upi next page logic
            return LendingViewStates.ENACH_PAGE;
        }else {
            return LendingViewStates.ENACH_PAGE;
        }
    }
}
