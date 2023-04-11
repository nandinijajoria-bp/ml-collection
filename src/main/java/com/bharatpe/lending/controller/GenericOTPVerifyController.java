package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.dto.SendOtpDTO;
import com.bharatpe.lending.service.GenericOTPVerifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("genericOtpVerify")
public class GenericOTPVerifyController {

    Logger logger = LoggerFactory.getLogger(GenericOTPVerifyController.class);

    @Autowired
    GenericOTPVerifyService genericOTPVerifyService;
    @RequestMapping(value="/sendOTP", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<ResponseDTO> sendOTP(@RequestAttribute BasicDetailsDto merchant, @RequestBody SendOtpDTO requestDTO) {
        if (requestDTO.getMobile() == null) {
            logger.info("Mobile number not provided");
            return new ResponseEntity<>(new ResponseDTO(false, "Invalid Request", null,null), HttpStatus.OK);
        }
        try {
            return new ResponseEntity<>(genericOTPVerifyService.sendOTp(requestDTO.getMobile(), merchant),HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while sending otp---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong", null,null), HttpStatus.OK);
        }
    }
    @RequestMapping(value="/verifyOTP", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<ResponseDTO> verifyOTP(@RequestAttribute BasicDetailsDto merchant, @RequestBody SendOtpDTO requestDTO) {
        if (requestDTO.getOtp() == null) {
            logger.info("Empty otp provided");
            return new ResponseEntity<>(new ResponseDTO(false, "Invalid Request", null,null), HttpStatus.OK);
        }
        try {
            return new ResponseEntity<>(genericOTPVerifyService.verifyOtp(requestDTO.getMobile(), merchant, requestDTO.getOtp()),HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while verifying otp---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong", null,null), HttpStatus.OK);
        }
    }
}
