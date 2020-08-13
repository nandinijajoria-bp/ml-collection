package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dto.IneligibleAPIResponseDto;
import com.bharatpe.lending.dto.IneligibleRequestDTO;
import com.bharatpe.lending.dto.IneligibleResponseDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.service.IneligibleDetailsService;
import com.bharatpe.lending.service.NotifyEligibleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("lending")
public class IneligibleController {

    Logger logger = LoggerFactory.getLogger(IneligibleController.class);

    @Autowired
    private IneligibleDetailsService ineligibleDetailsService;

    @Autowired
    NotifyEligibleService notifyEligibleService;

  //  @RequestMapping(value="/ineligibleDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
//    public ResponseEntity<IneligibleResponseDTO> ineligibleDetails(@RequestAttribute Merchant merchant, @RequestBody(required = false) RequestDTO<IneligibleRequestDTO> requestDTO) {
//        try {
//            IneligibleResponseDTO ineligibleResponseDTO = ineligibleDetailsService.fetchIneligibleLoanDetails(merchant, requestDTO.getPayload());
//            logger.debug("ineligibleDetails response : {}", ineligibleResponseDTO);
//            return new ResponseEntity<>(ineligibleResponseDTO, HttpStatus.OK);
//        } catch (Exception e) {
//            logger.error("Exception while fetching Ineligible Loan Details", e);
//            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
//        }
//    }

    @RequestMapping(value="/notifyEligible", method = RequestMethod.GET, produces="application/json")
    public Object notifyEligible(@RequestAttribute Merchant merchant, HttpServletResponse response, @RequestParam String type) {

        Object resp = notifyEligibleService.notifyEligible(merchant, response, type);

        logger.info("notifyEligible response : {}", response);
        return resp;
    }
    
    @RequestMapping(value="/ineligibleDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public IneligibleAPIResponseDto getIneligibleDetails(@RequestAttribute Merchant merchant, @RequestBody(required = false) RequestDTO<IneligibleRequestDTO> requestDTO) {
    	return ineligibleDetailsService.getIneligibleDetails(merchant, requestDTO.getPayload());
    }
}
