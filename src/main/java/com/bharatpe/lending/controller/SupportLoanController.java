package com.bharatpe.lending.controller;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.dto.SupportResponseDTO;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.FLDGReportService;
import com.bharatpe.lending.service.SupportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("support")
public class SupportLoanController {
    Logger logger = LoggerFactory.getLogger(SupportLoanController.class);

    @Autowired
    SupportService supportService;

    @Autowired
    FLDGReportService fldgReportService;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    @RequestMapping(value="/loan", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<SupportResponseDTO> supportLoanDetails(@RequestParam Long merchantId) {
        logger.info("Request received to get loan details for merchantId: {}", merchantId);
        return new ResponseEntity<>(supportService.supportLoan(merchantId), HttpStatus.OK);
    }

//    @RequestMapping(value="/lenderchange", method = RequestMethod.POST, produces="application/json")
//    public ResponseEntity<SupportResponseDTO> supportBulkLenderChange(@RequestParam Long merchantId,@RequestParam Long applicationId,@RequestParam Long fileId,@RequestParam Boolean flag,@RequestParam String lender){
//        logger.info("Request received to lender change for merchantId:{},applicationId:{} ,fileId:{},flag:{}", merchantId,applicationId,fileId,flag);
//
//        return new ResponseEntity<>(supportService.bulkLenderchange(merchantId,applicationId,fileId,flag,lender), HttpStatus.OK);
//    }


    @RequestMapping(value="/lender", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<SupportResponseDTO> supportLenderChange(@RequestParam String lender,@RequestParam Long fileId , @RequestParam Integer lines){
        logger.info("Request received to lender change for fileName:{},lender:{},lines:{} ", fileId,lender, lines);

        return new ResponseEntity<>(supportService.changeLender(lender, fileId, lines), HttpStatus.OK);
    }

    @RequestMapping(value="/fldg/{fileName}", method = RequestMethod.POST, produces="application/json")
    public ResponseDTO uploadFLDG(@PathVariable(value = "fileName") String fileName){
        logger.info("Uploading FLDG Report File : {}", fileName);
        executorService.execute(()->fldgReportService.uploadFLDGReportEntries(fileName));
        return new ResponseDTO(true,"FLDG Report Upload Successfully!");
    }

    @RequestMapping(value="/nbfcRetry/{fileName}", method = RequestMethod.POST, produces="application/json")
    public ResponseDTO retryNbfc(@PathVariable(value = "fileName") String fileName){
        logger.info("Nbfc Retry File : {}", fileName);
        return fldgReportService.nbfcRetry(fileName);
    }

    @RequestMapping(value="/createAgreement/{applicationId}", method = RequestMethod.POST, produces="application/json")
    public ResponseDTO createAgreement(@PathVariable(value = "applicationId") Long applicationId){
        return supportService.createAgreement(applicationId);
    }

    @RequestMapping(value="/fetchBulkContacts/{fileName}", method = RequestMethod.POST, produces="application/json")
    public ResponseDTO fetchBulkContact(@PathVariable(value = "fileName") String fileName){
        logger.info("Fetching bulk contacts for File : {}", fileName);
        return  supportService.getBulkContacts(fileName);
    }

    @RequestMapping(value="/showBulkContacts", method = RequestMethod.GET, produces="application/json")
    public ResponseDTO showBulkContacts(){
        logger.info("Fetching bulk contacts");
        return supportService.showBulkContacts();
    }

    @RequestMapping(value="/cancelApplication", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity cancelApplication(@RequestParam Long merchantId, @RequestBody CommonAPIRequest commonAPIRequest) {
        logger.info("cancelApplication request : {}",commonAPIRequest);
        Long applicationId =  commonAPIRequest.getPayload().get("applicationId") != null ? Long.parseLong(commonAPIRequest.getPayload().get("applicationId").toString()) : null;
        String reason = commonAPIRequest.getPayload().get("reason") != null ? commonAPIRequest.getPayload().get("reason").toString() : null;
        if(applicationId == null || applicationId <=0) {
            logger.info("CancelApplicationService invalid applicationId");
            Map<String, Boolean> resp = new HashMap<>();
            resp.put("success",false);
            return new ResponseEntity<>(resp, HttpStatus.BAD_REQUEST);
        }

        Map<String, Boolean> resp = supportService.cancelApplication(merchantId, applicationId, reason);

        logger.info("cancelApplication response : {}", resp);
        return new ResponseEntity<>(resp, HttpStatus.OK);
    }
}

