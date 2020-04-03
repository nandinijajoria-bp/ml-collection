package com.bharatpe.lending.service;

import com.bharatpe.common.dao.LendingPrebookLoansDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPrebookLoans;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.PreBookResponseDTO;
import com.bharatpe.lending.handlers.GupShupOTPHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PreBookService {

    @Autowired
    LendingPrebookLoansDao lendingPrebookLoansDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    @Autowired
    GupShupOTPHandler gupShupOTPHandler;

    private final String DEEP_LINK = "bharatpe://dynamic?key=loan";

    public PreBookResponseDTO getDetails(Merchant merchant) {
        LendingPrebookLoans lendingPrebookLoans = lendingPrebookLoansDao.findByMerchantId(merchant.getId());
        if (lendingPrebookLoans == null) {
            PreBookResponseDTO responseDTO = new PreBookResponseDTO(false, "Merchant not applicable for pre book loan");
            responseDTO.setDeepLink(DEEP_LINK);
            return responseDTO;
        }
        if (lendingPrebookLoans.getOtpVerified()) {
            PreBookResponseDTO preBookResponseDTO = new PreBookResponseDTO();
            preBookResponseDTO.setDeepLink(DEEP_LINK);
            return preBookResponseDTO;
        }
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchant(lendingPrebookLoans.getApplicationId(), merchant);
        if (lendingApplication == null) {
            return new PreBookResponseDTO(false, "Loan Application not found");
        }
        MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
        if (merchantBankDetail == null) {
            return new PreBookResponseDTO(false, "Merchant Bank Details not found");
        }
        return new PreBookResponseDTO(merchantBankDetail.getBankName(), merchantBankDetail.getAccountNumber(), lendingApplication.getLoanAmount(), lendingApplication.getEdi(), null);
    }

    public PreBookResponseDTO verifyOTP(Merchant merchant, String otp) {
        Boolean isOTPVerified = gupShupOTPHandler.verifyOTP(merchant.getMobile(), otp);
        LendingPrebookLoans lendingPrebookLoans = lendingPrebookLoansDao.findByMerchantId(merchant.getId());
        if (lendingPrebookLoans == null) {
            return new PreBookResponseDTO(false, "Merchant not applicable for pre book loan");
        }
        if (isOTPVerified) {
            lendingPrebookLoans.setOtpVerified(true);
            lendingPrebookLoansDao.save(lendingPrebookLoans);
            PreBookResponseDTO preBookResponseDTO = new PreBookResponseDTO();
            preBookResponseDTO.setDeepLink(DEEP_LINK);
            return preBookResponseDTO;
        }
        return new PreBookResponseDTO(false, "Invalid OTP");
    }
}
