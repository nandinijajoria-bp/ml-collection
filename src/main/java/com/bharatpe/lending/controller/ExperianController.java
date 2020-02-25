package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.dto.ExperianDetailsDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.dto.SendOtpDTO;
import com.bharatpe.lending.service.ExperianService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("experian")
public class ExperianController {

    Logger logger = LoggerFactory.getLogger(ExperianController.class);

    @Autowired
    ExperianService experianService;

    @RequestMapping(value="/details", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<ResponseDTO> experianDetails(@RequestAttribute Merchant merchant, @RequestBody RequestDTO<ExperianDetailsDTO> requestDTO) {
        try {
            return new ResponseEntity<>(experianService.updateDetails(requestDTO.getPayload(), merchant.getId(), merchant.getMobile()),HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while updating experian details---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong", null), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/sendOTP", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<ResponseDTO> sendOTP(@RequestAttribute Merchant merchant, @RequestBody RequestDTO<SendOtpDTO> requestDTO) {
        if (requestDTO.getPayload() == null || requestDTO.getPayload().getMobile() == null) {
            return new ResponseEntity<>(new ResponseDTO(false, "Invalid Request", null), HttpStatus.OK);
        }
        try {
            return new ResponseEntity<>(experianService.sendOtp(requestDTO.getPayload().getMobile(), merchant),HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while sending otp---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong", null), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/verifyOTP", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<ResponseDTO> verifyOTP(@RequestAttribute Merchant merchant, @RequestBody RequestDTO<SendOtpDTO> requestDTO) {
        if (requestDTO.getPayload() == null || requestDTO.getPayload().getMobile() == null || requestDTO.getPayload().getOtp() == null) {
            return new ResponseEntity<>(new ResponseDTO(false, "Invalid Request", null), HttpStatus.OK);
        }
        try {
            return new ResponseEntity<>(experianService.verifyOtp(requestDTO.getPayload().getMobile(), merchant, requestDTO.getPayload().getOtp()),HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while sending otp---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong", null), HttpStatus.OK);
        }
    }
}
