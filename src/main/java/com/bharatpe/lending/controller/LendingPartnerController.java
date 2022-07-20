//package com.bharatpe.lending.controller;
//
//import com.bharatpe.lending.dto.PartnerDetailsRequestDTO;
//import com.bharatpe.lending.dto.ResponseDTO;
//import com.bharatpe.lending.service.LendingPartnerService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestMethod;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequestMapping("partner")
//public class LendingPartnerController {
//
//    Logger logger = LoggerFactory.getLogger(LendingPartnerController.class);
//
//    @Autowired
//    LendingPartnerService lendingPartnerService;
//
//    @RequestMapping(value="/details", method = RequestMethod.POST, consumes="application/json", produces="application/json")
//    public ResponseEntity<ResponseDTO> merchantDetails(@RequestBody PartnerDetailsRequestDTO requestDTO) {
//        try {
//            if (requestDTO == null || requestDTO.getPartner() == null || requestDTO.getMobile() == null) {
//                return new ResponseEntity<>(new ResponseDTO(false, "Invalid request", null,null), HttpStatus.OK);
//            }
//            logger.info("Saving details for partner: {} with mobile: {}", requestDTO.getPartner(), requestDTO.getMobile());
//            lendingPartnerService.saveDetails(requestDTO);
//            return new ResponseEntity<>(new ResponseDTO(true, null, null,null), HttpStatus.OK);
//        } catch (Exception e) {
//            logger.error("Exception while saving partner details", e);
//            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong", null,null), HttpStatus.OK);
//        }
//    }
//}
