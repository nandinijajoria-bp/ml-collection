package com.bharatpe.lending.service;

import com.bharatpe.common.entities.ExperianDetails;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.controller.ExperianController;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.handlers.GupShupOTPHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GenericOTPVerifyService {

    Logger logger = LoggerFactory.getLogger(GenericOTPVerifyService.class);

    @Autowired
    GupShupOTPHandler gupShupOTPHandler;

    public ResponseDTO sendOTp(String mobile, BasicDetailsDto merchant){
        String message = "BharatPe: %code% is your OTP to register yourself on BharatPe Merchant App. BharatPe.com";
        Boolean otp = gupShupOTPHandler.sendOTP(mobile, message);
        if (otp) {
            logger.info("OTP sent on mobile: {} for merchant: {}", mobile, merchant.getId());
        }
        return new ResponseDTO(otp, null, null, null);
    }

    public ResponseDTO verifyOtp(String mobile, BasicDetailsDto merchant, String otp) {
        Boolean isOTPVerified = gupShupOTPHandler.verifyOTP(mobile, otp);
        if(isOTPVerified) {
            logger.info("OTP successfully verified for merchant: {}", merchant);
            return new ResponseDTO(true, "Valid OTP", null, null);
        }
        return new ResponseDTO(false, "Invalid OTP", null,null);
    }
}
