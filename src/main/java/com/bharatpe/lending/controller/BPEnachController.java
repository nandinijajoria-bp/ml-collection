package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.service.BPEnachService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("bpenach")
public class BPEnachController {

    Logger logger = LoggerFactory.getLogger(ENachController.class);

    @Value("${enach.provider}")
    private String enachServiceToUse;

    @Autowired
    private BPEnachService bpEnachService;

    @RequestMapping(value = "/initiate", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
    public ResponseEntity<ENachIntitiationResponseDTO> initiateEnach(HttpServletRequest httpServletRequest, @RequestAttribute BasicDetailsDto merchant,
                                                                     @RequestHeader("token") String token,
                                                                     @RequestParam(name = "app_version", required = false) String appVersion,
                                                                     @RequestParam(name = "platform", required = true) String module,
                                                                     @RequestParam(name = "loan_amount", required = true) String amount,
                                                                     @RequestParam(name = "type", required = true) String type,
                                                                     @RequestParam(name = "reference_number", required = true) String referenceNumber,
                                                                     @RequestParam(name ="owner_id", required = false) String ownerId,
                                                                     @RequestParam(name ="client_name", required =
                                                                         true) String clientName) {
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setResponse(false);

        ResponseEntity<ENachIntitiationResponseDTO> finalResponse;
        try {
            Double loanAmount = Double.parseDouble(amount);
            //logger.error(enachServiceToUse);
            if (enachServiceToUse == null || (!enachServiceToUse.equals("DIGIO") && !enachServiceToUse.equals("TECHPROCESS"))) {
                responseDTO.setMessage("Incorrect Enach service provider mentioned");
                finalResponse = new ResponseEntity<>(responseDTO, HttpStatus.OK);
            } else {
                finalResponse = new ResponseEntity<>(bpEnachService.eNachInitiate(merchant, token,
                    appVersion, module, loanAmount, type, referenceNumber, ownerId, clientName),
                    HttpStatus.OK);
            }
        } catch (Exception e) {
            logger.error("Exception while initiating enach", e);
            responseDTO.setMessage("Something went wrong");
            finalResponse = new ResponseEntity<>(responseDTO, HttpStatus.OK);
        }
        return finalResponse;
    }


    @RequestMapping(value = "/submit", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public ResponseEntity<ENachIntitiationResponseDTO> submit(@RequestAttribute BasicDetailsDto merchant, @RequestHeader("token") String token, @RequestBody ENachSubmitRequestDTO body) {
        logger.info("Enach Submit request : {}", body);
        ResponseEntity<ENachIntitiationResponseDTO> finalResponse;
        if (enachServiceToUse.equals("TECHPROCESS")) {
            finalResponse = new ResponseEntity<>(bpEnachService.submitEnach(merchant, body, token), HttpStatus.OK);
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
        return finalResponse;
    }
//
//    @RequestMapping(value = "/skip", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
//    public ResponseEntity<ResponseDTO> skipEnach(@RequestAttribute BasicDetailsDto merchant, @RequestParam(name = "reference_number", required = true) String referenceNumber) {
//        return new ResponseEntity<>(bpEnachService.setEnachSkipStatus(merchant, referenceNumber), HttpStatus.OK);
//    }

}
