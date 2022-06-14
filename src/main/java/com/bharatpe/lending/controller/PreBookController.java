package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.PreBookResponseDTO;
import com.bharatpe.lending.service.PreBookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending")
public class PreBookController {

    Logger logger = LoggerFactory.getLogger(PreBookController.class);

    @Autowired
    PreBookService preBookService;

    @RequestMapping(value="/prebook", method = RequestMethod.GET, consumes="application/json", produces="application/json")
    public ResponseEntity<PreBookResponseDTO> experianDetails(@RequestAttribute BasicDetailsDto merchant) {
        try {
            return new ResponseEntity<>(preBookService.getDetails(merchant), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while getting prebook loan details---", e);
            return new ResponseEntity<>(new PreBookResponseDTO(false, "Something went wrong"), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/prebook/verifyOTP", method = RequestMethod.GET, consumes="application/json", produces="application/json")
    public ResponseEntity<PreBookResponseDTO> verifyOTP(@RequestAttribute BasicDetailsDto merchant, @RequestParam String otp, @RequestParam String uuid) {
        try {
            return new ResponseEntity<>(preBookService.verifyOTP(merchant, otp, uuid),HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while sending otp---", e);
            return new ResponseEntity<>(new PreBookResponseDTO(false, "Something went wrong"), HttpStatus.OK);
        }
    }
}
