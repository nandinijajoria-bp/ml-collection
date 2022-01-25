package com.bharatpe.lending.controller;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.service.FosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("fos")
public class FosController {

    Logger logger = LoggerFactory.getLogger(FosController.class);

    @Autowired
    FosService fosService;

    @RequestMapping(value="/loan", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<ResponseDTO> fosLoanDetails(@RequestParam Long merchantId) {
        return new ResponseEntity<>(fosService.fosLoan(merchantId), HttpStatus.OK);
    }

    @RequestMapping(value="/v2/loan", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<ResponseDTO> fosnewLoanDetails(@RequestParam Long merchantId) {
        return new ResponseEntity<>(fosService.fosnewLoan(merchantId), HttpStatus.OK);
    }

    @RequestMapping(value="/nach/update", method = RequestMethod.POST, produces="application/json")
    public ResponseEntity<ResponseDTO> fosUpdate(@RequestBody Map<String,Object> requestDTO) {
        return new ResponseEntity<>(fosService.fosUpdate(requestDTO), HttpStatus.OK);
    }

    @RequestMapping(value="/get-address", method = RequestMethod.GET)
    public ResponseEntity<ResponseDTO> getMerchantAddress(@RequestParam Long merchantId){

        return new ResponseEntity<>(fosService.getMerchantAddress(merchantId), HttpStatus.OK);


    }

    @RequestMapping(value="/get_fos_attributes", method = RequestMethod.POST, produces="application/json", consumes = "application/json")
    public ResponseEntity<FosResponseDTO> fosSalaryAttributes(@RequestBody FosAttributionRequestDTO requestDTO) {
        return new ResponseEntity<>(fosService.getFosSalaryAttribution(requestDTO), HttpStatus.OK);
    }

    @RequestMapping(value = "/merchant_eligibility",method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<ResponseDTO> getMerchantEligibilityForLoan(@RequestParam Long merchantId) {
        return new ResponseEntity<>(fosService.checkMerchantEligibilty(merchantId),HttpStatus.OK);
    }

}
