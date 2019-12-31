package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.service.IneligibleDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending")
public class IneligibleController {

    Logger logger = LoggerFactory.getLogger(IneligibleController.class);

    @Autowired
    private IneligibleDetailsService ineligibleDetailsService;

    @RequestMapping(value="/ineligibleDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public Object ineligibleDetails(@RequestAttribute Merchant merchant, @RequestBody CommonAPIRequest commonAPIRequest) {
        Object details = ineligibleDetailsService.fetchIneligibleLoanDetails(merchant, commonAPIRequest);
        logger.debug("ineligibleDetails response : {}", details);
        return details;
    }
}
