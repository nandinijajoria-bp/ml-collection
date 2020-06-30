package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.service.BPEnachService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("bpenach")
public class BPEnachController {

    Logger logger = LoggerFactory.getLogger(ENachController.class);

    @Value("${enach.provider}")
    private String enachServiceToUse;

    @Autowired
    private BPEnachService bpEnachService;

    @RequestMapping(value = "/initiate", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
    public ResponseEntity<ENachIntitiationResponseDTO> initiateEnach(@RequestAttribute Merchant merchant, @RequestParam(name = "app_version", required = false) String appVersion, @RequestParam(name = "platform", required = true) String module, @RequestParam(name = "loan_amount", required = true) String amount) {
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setResponse(false);
        try {
            Double loanAmount = Double.parseDouble(amount);
            logger.error(enachServiceToUse);
            if (enachServiceToUse == null || (!enachServiceToUse.equals("digio") && !enachServiceToUse.equals("techprocess"))) {
                responseDTO.setMessage("Incorrect Enach service provider mentioned");
                return new ResponseEntity<>(responseDTO, HttpStatus.OK);
            }
            return new ResponseEntity<>(bpEnachService.eNachInitiate(merchant, appVersion, module, loanAmount), HttpStatus.OK);
//            if (enachServiceToUse.equals("techprocess")) {
//                return new ResponseEntity<>(bpEnachService.eNachInitiate(merchant, appVersion, module, loanAmount), HttpStatus.OK);
//            }

//            else {
////                return new ResponseEntity<>(BPEnachService.enachInititateForDigio(merchant), HttpStatus.OK);
//            }
        } catch (Exception e) {
            logger.error("Exception while initiating enach", e);
            responseDTO.setMessage("Something went wrong");
            return new ResponseEntity<>(responseDTO, HttpStatus.OK);
        }
    }


    @RequestMapping(value = "/submit", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public ResponseEntity<ENachIntitiationResponseDTO> submit(@RequestAttribute Merchant merchant, @RequestBody ENachSubmitRequestDTO body) {
        logger.info("Enach Submit request : {}", body);

        if (enachServiceToUse.equals("techprocess")) {
            return new ResponseEntity<>(bpEnachService.submitEnach(merchant, body), HttpStatus.OK);
        }
//        else if(enachServiceToUse.equals("digio")){
//            return new ResponseEntity<>(eNachService.submitEnachForDigio(merchant, body), HttpStatus.OK);
//        }
        else {
            logger.error("Mentioned wrong enach service provider");
            ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
            responseDTO.setResponse(false);
            responseDTO.setMessage("Wrong enach serive provider");
            return new ResponseEntity<>(responseDTO, HttpStatus.OK);
        }
    }

    @RequestMapping(value = "/skip", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO> skipEnach(@RequestAttribute Merchant merchant, @RequestParam(name = "reference_number", required = false) String referenceNumber) {
        return new ResponseEntity<>(bpEnachService.setEnachSkipStatus(merchant, referenceNumber), HttpStatus.OK);
    }

}
