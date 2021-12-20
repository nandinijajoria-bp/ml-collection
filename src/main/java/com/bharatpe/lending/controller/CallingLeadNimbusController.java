package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.dto.CallingLeadResponseNimbusDto;
import com.bharatpe.lending.common.service.CallingLeadNimbusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("lending")
public class CallingLeadNimbusController {

    Logger logger = LoggerFactory.getLogger(MerchantDetailsController.class);

    @Autowired
    CallingLeadNimbusService callingLeadNimbusService;

    @PostMapping(value = "/push_lead_response", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Object> addLeadResponse(@RequestBody List<CallingLeadResponseNimbusDto> requestDTO) {
        logger.info("processing leads from nimbus");
        if (ObjectUtils.isEmpty(requestDTO)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(callingLeadNimbusService.processNimbusLeadResponse(requestDTO), HttpStatus.OK);
    }
}
