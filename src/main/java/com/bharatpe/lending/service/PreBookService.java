package com.bharatpe.lending.service;

import com.bharatpe.common.dao.LendingPrebookLoansDao;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPrebookLoans;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.PreBookResponseDTO;
import com.bharatpe.lending.handlers.BharatPeOtpHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PreBookService {

    @Autowired
    LendingPrebookLoansDao lendingPrebookLoansDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    BharatPeOtpHandler bharatPeOtpHandler;

    @Autowired
    MerchantService merchantService;

    private final String DEEP_LINK = "bharatpe://dynamic?key=loan";

    public PreBookResponseDTO getDetails(BasicDetailsDto merchant) {
        LendingPrebookLoans lendingPrebookLoans = lendingPrebookLoansDao.findByMerchantId(merchant.getId());
        if (lendingPrebookLoans == null || lendingPrebookLoans.getOtpVerified()) {
            PreBookResponseDTO preBookResponseDTO = new PreBookResponseDTO();
            preBookResponseDTO.setDeepLink(DEEP_LINK);
            return preBookResponseDTO;
        }
        LendingApplication lendingApplication =
          lendingApplicationDao.findByIdAndMerchantId(lendingPrebookLoans.getApplicationId(), merchant.getId());
        if (lendingApplication == null) {
            PreBookResponseDTO preBookResponseDTO = new PreBookResponseDTO();
            preBookResponseDTO.setDeepLink(DEEP_LINK);
            return preBookResponseDTO;
        }
        final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
        BankDetailsDto merchantBankDetail = null;
        if (bankDetailsDtoOptional.isPresent())
            merchantBankDetail = bankDetailsDtoOptional.get();
        if (merchantBankDetail == null) {
            return new PreBookResponseDTO(false, "Merchant Bank Details not found");
        }
        return new PreBookResponseDTO(merchantBankDetail.getBankName(), merchantBankDetail.getAccountNumber(), lendingApplication.getLoanAmount(), lendingApplication.getEdi(), null);
    }

    public PreBookResponseDTO verifyOTP(BasicDetailsDto merchant, String otp, String uuid) {
        Boolean isOTPVerified = bharatPeOtpHandler.verifyOtp(merchant, otp,uuid);
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
