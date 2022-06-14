package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.service.APIGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("lending")
public class SdkController {

    Logger logger = LoggerFactory.getLogger(SdkController.class);

//    @Autowired
//    MerchantDao merchantDao;

    @Autowired
    APIGatewayService apiGatewayService;
    @Autowired
    MerchantService merchantService;

    @RequestMapping(value = "/sdkInvoke/{merchantId}", method = RequestMethod.GET, produces = "application/json")
    ResponseEntity<Map<String,Object>> getSdkInvoke(@PathVariable(value = "merchantId") Long merchantId) {
        logger.info("Get SDK Status Api Called for merchant:{}", merchantId);
//        Optional<Merchant> merchant = merchantDao.findById(merchantId);
        Optional<BasicDetailsDto> merchant = merchantService.fetchMerchantBasicDetails(merchantId);
        Map<String, Object> response = new HashMap<>();
        if(merchant.isPresent()){
            Boolean isInvoke = apiGatewayService.isSdkInvoke(merchant.get());
            response.put("isInvoke",isInvoke);
            return new ResponseEntity<>(response,HttpStatus.OK);
        }
        response.put("message","Merchant doesn't exist");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @GetMapping(value = "/sdkInvoke")
    ResponseEntity<Map<String,Object>> sdkInvode(@RequestAttribute BasicDetailsDto merchant) {
        logger.info("Get SDK Status Api Called for merchant:{}", merchant.getId());
        Map<String, Object> response = new HashMap<>();
        Boolean isInvoke = apiGatewayService.isSdkInvoke(merchant);
        response.put("isInvoke",isInvoke);
        return new ResponseEntity<>(response,HttpStatus.OK);
    }
}
