package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dtos.IneligibleResponseDTO;
import com.bharatpe.lending.service.IneligibleDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending")
public class IneligibleController {

    Logger logger = LoggerFactory.getLogger(IneligibleController.class);

    @Autowired
    private IneligibleDetailsService ineligibleDetailsService;

    @RequestMapping(value="/ineligibleDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<IneligibleResponseDTO> ineligibleDetails(@RequestAttribute Merchant merchant, @RequestBody(required = false) CommonAPIRequest commonAPIRequest, @RequestParam(required = false) Integer requestedLoanAmount) {
        try {
            IneligibleResponseDTO ineligibleResponseDTO = ineligibleDetailsService.fetchIneligibleLoanDetails(merchant, requestedLoanAmount);
            logger.debug("ineligibleDetails response : {}", ineligibleResponseDTO);
            return new ResponseEntity<>(ineligibleResponseDTO, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while fetching Ineligible Loan Details", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
