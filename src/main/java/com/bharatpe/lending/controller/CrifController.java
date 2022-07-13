//package com.bharatpe.lending.controller;
//
//import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
//import com.bharatpe.lending.dto.CrifResponseDTO;
//import com.bharatpe.lending.service.CrifService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("lending")
//public class CrifController {
//
//    Logger logger = LoggerFactory.getLogger(CrifController.class);
//
//    @Autowired
//    CrifService crifService;
//
//    @GetMapping(value = "/crif", produces = "application/json")
//    public ResponseEntity<CrifResponseDTO> getCrif(@RequestAttribute BasicDetailsDto merchant, @RequestParam String pancard) {
//        logger.info("Get Crif request for merchant:{} and pancard:{}", merchant.getId(), pancard);
//        return new ResponseEntity<>(crifService.getCrif(merchant, pancard), HttpStatus.OK);
//    }
//
//    @GetMapping(value = "/crif/question", produces = "application/json")
//    public ResponseEntity<CrifResponseDTO> crifAnswer(@RequestAttribute BasicDetailsDto merchant, @RequestParam String answer) {
//        logger.info("Crif answer request for merchant:{} and answer:{}", merchant.getId(), answer);
//        return new ResponseEntity<>(crifService.crifAnswer(merchant, answer), HttpStatus.OK);
//    }
//}
