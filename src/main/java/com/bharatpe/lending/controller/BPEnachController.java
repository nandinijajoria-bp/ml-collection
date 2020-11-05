package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.BankPayoutRequest;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.dao.BPEnachRawRequestDao;
import com.bharatpe.lending.dao.BPEnachSkipDao;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.entity.BPEnachRawRequest;
import com.bharatpe.lending.service.BPEnachService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.RequestScope;

import javax.servlet.http.HttpServletRequest;
//import javax.xml.ws.RequestWrapper;

@RestController
@RequestMapping("bpenach")
public class BPEnachController {

    Logger logger = LoggerFactory.getLogger(ENachController.class);

    @Value("${enach.provider}")
    private String enachServiceToUse;

    @Autowired
    private BPEnachService bpEnachService;


    @Autowired
    BPEnachRawRequestDao bpEnachRawRequestDao;

    @RequestMapping(value = "/initiate", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
    public ResponseEntity<ENachIntitiationResponseDTO> initiateEnach(HttpServletRequest httpServletRequest, @RequestAttribute Merchant merchant, @RequestParam(name = "app_version", required = false) String appVersion, @RequestParam(name = "platform", required = true) String module, @RequestParam(name = "loan_amount", required = true) String amount, @RequestParam(name = "type", required = true) String type, @RequestParam(name = "reference_number", required = true) String referenceNumber) {
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setResponse(false);

        BPEnachRawRequest bpEnachRawRequest = new BPEnachRawRequest(merchant.getId(), "INITIATE");
        bpEnachRawRequest.setRequest(httpServletRequest.getQueryString());
        bpEnachRawRequest = bpEnachRawRequestDao.save(bpEnachRawRequest);
        ResponseEntity<ENachIntitiationResponseDTO> finalResponse;
        try {
            Double loanAmount = Double.parseDouble(amount);
            //logger.error(enachServiceToUse);
            if (enachServiceToUse == null || (!enachServiceToUse.equals("digio") && !enachServiceToUse.equals("techprocess"))) {
                responseDTO.setMessage("Incorrect Enach service provider mentioned");
                finalResponse = new ResponseEntity<>(responseDTO, HttpStatus.OK);
            } else {
                finalResponse = new ResponseEntity<>(bpEnachService.eNachInitiate(merchant, appVersion, module, loanAmount, type, referenceNumber), HttpStatus.OK);
            }
//            disabled for now
//            if (enachServiceToUse.equals("techprocess")) {
//                return new ResponseEntity<>(bpEnachService.eNachInitiate(merchant, appVersion, module, loanAmount), HttpStatus.OK);
//            } else {
//                return new ResponseEntity<>(BPEnachService.enachInititateForDigio(merchant), HttpStatus.OK);
//            }
        } catch (Exception e) {
            logger.error("Exception while initiating enach", e);
            responseDTO.setMessage("Something went wrong");
            finalResponse = new ResponseEntity<>(responseDTO, HttpStatus.OK);
        }
        bpEnachRawRequest.setResponse(String.valueOf(finalResponse.getBody()));
        bpEnachRawRequest.setStatus(String.valueOf(finalResponse.getStatusCodeValue()));
        bpEnachRawRequestDao.save(bpEnachRawRequest);
        return finalResponse;
    }


    @RequestMapping(value = "/submit", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public ResponseEntity<ENachIntitiationResponseDTO> submit(@RequestAttribute Merchant merchant, @RequestBody ENachSubmitRequestDTO body) {
        logger.info("Enach Submit request : {}", body);
        ResponseEntity<ENachIntitiationResponseDTO> finalResponse;
        BPEnachRawRequest bpEnachRawRequest = new BPEnachRawRequest(merchant.getId(), "SUBMIT");
        bpEnachRawRequest.setRequest(body.toString());
        bpEnachRawRequest.setReferenceNumber(String.valueOf(body.getApplicationId()));
        bpEnachRawRequest = bpEnachRawRequestDao.save(bpEnachRawRequest);
        if (enachServiceToUse.equals("techprocess")) {
            finalResponse = new ResponseEntity<>(bpEnachService.submitEnach(merchant, body), HttpStatus.OK);
        }
        //disabled for now
//        else if(enachServiceToUse.equals("digio")){
//            return new ResponseEntity<>(bpEnachService.submitEnachForDigio(merchant, body), HttpStatus.OK);
//        }
        else {
            logger.error("Mentioned wrong enach service provider");
            ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
            responseDTO.setResponse(false);
            responseDTO.setMessage("Wrong enach serive provider");
            finalResponse = new ResponseEntity<>(responseDTO, HttpStatus.OK);
        }
        bpEnachRawRequest.setResponse(String.valueOf(finalResponse.getBody()));
        bpEnachRawRequest.setStatus(String.valueOf(finalResponse.getStatusCodeValue()));
        bpEnachRawRequestDao.save(bpEnachRawRequest);
        return finalResponse;
    }

    @RequestMapping(value = "/skip", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO> skipEnach(@RequestAttribute Merchant merchant, @RequestParam(name = "reference_number", required = true) String referenceNumber) {
        return new ResponseEntity<>(bpEnachService.setEnachSkipStatus(merchant, referenceNumber), HttpStatus.OK);
    }

}
