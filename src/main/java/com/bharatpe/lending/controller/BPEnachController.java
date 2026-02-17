package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.enums.LoanStatus;
import com.bharatpe.lending.loanV3.revamp.services.businessLoan.proxy.EdiEmiProxyHelper;
import com.bharatpe.lending.service.BPEnachService;
import com.bharatpe.lending.service.ENachService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.DISBURSED_LOAN;

@RestController
@RequestMapping("bpenach")
public class BPEnachController {

    Logger logger = LoggerFactory.getLogger(ENachController.class);

    @Value("${enach.provider}")
    private String enachServiceToUse;

    @Autowired
    private BPEnachService bpEnachService;

    @Autowired
    ENachService eNachService;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    @Autowired
    private LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    @Autowired
    @Qualifier("nachInitiateProxy")
    private EdiEmiProxyHelper<Map<String,String>, Map<String,String>, Map<String,String>, ENachIntitiationResponseDTO> nachInitiateProxy;

    @Autowired
    @Qualifier("nachSubmitProxy")
    private EdiEmiProxyHelper<Map<String,String>, Map<String,String>, ENachSubmitRequestDTO, ENachIntitiationResponseDTO> nachSubmitProxy;


    @RequestMapping(value = "/initiate", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<ENachIntitiationResponseDTO> initiateEnach(HttpServletRequest httpServletRequest, @RequestAttribute BasicDetailsDto merchant,
                                                                     @RequestHeader("token") String token,
                                                                     @RequestParam(name = "app_version", required = false) String appVersion,
                                                                     @RequestParam(name = "platform", required = false) String module,
                                                                     @RequestParam(name = "loan_amount", required = false) String amount,
                                                                     @RequestParam(name = "type", required = false) String type,
                                                                     @RequestParam(name = "reference_number", required = false) String referenceNumber,
                                                                     @RequestParam(name ="owner_id", required = false) String ownerId,
                                                                     @RequestParam(name ="client_name", required = false) String clientName,
                                                                     @RequestParam(name ="nach_mode", required = false) String nachMode,
                                                                     @RequestHeader Map<String,String> headers,
                                                                     @RequestParam Map<String, String> params) {
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setResponse(false);

        ResponseEntity<ENachIntitiationResponseDTO> finalResponse;
        try {
            //logger.error(enachServiceToUse);
            if (enachServiceToUse == null || (!enachServiceToUse.equals("DIGIO") && !enachServiceToUse.equals("TECHPROCESS"))) {
                responseDTO.setMessage("Incorrect Enach service provider mentioned");
                finalResponse = new ResponseEntity<>(responseDTO, HttpStatus.OK);
            } else {
                LendingPaymentScheduleSlave activeLoan =  lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(
                        merchant.getId(), Collections.singletonList(LoanStatus.ACTIVE.name()));
                if(Objects.isNull(activeLoan) && nachInitiateProxy.isNotEdiRequest(params, merchant, headers, null)){
                    logger.info("sending initiate request to bl for merchant: {}",merchant.getId());
                    return new ResponseEntity<>(nachInitiateProxy.getResponse(params, merchant, headers, null), HttpStatus.OK);
                }
                finalResponse = new ResponseEntity<>(bpEnachService.eNachInitiate(merchant, token,
                    appVersion, module, amount, type, referenceNumber, ownerId, clientName, nachMode, activeLoan),
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
    public ResponseEntity<ENachIntitiationResponseDTO> submit(@RequestAttribute BasicDetailsDto merchant,
                                                              @RequestHeader("token") String token,
                                                              @RequestHeader Map<String, String> headers,
                                                              @RequestBody ENachSubmitRequestDTO body) {
        logger.info("Enach Submit request : {}", body);

        ResponseEntity<ENachIntitiationResponseDTO> finalResponse = null;

        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(body.getApplicationId(), merchant.getId());
        boolean isActiveLoan = Objects.nonNull(lendingApplication) && DISBURSED_LOAN.equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus());
        if(!isActiveLoan && nachSubmitProxy.isNotEdiRequest(null, merchant, headers, body)){
            logger.info("sending enach submit request to bl for merchant: {}", merchant.getId());
            return new ResponseEntity<>(nachSubmitProxy.getResponse(null, merchant, headers, body), HttpStatus.OK);
        }

        BharatPeEnachResponseDTO bharatPeEnach = enachHandler.findByMerchantIdAndApplicationIdV2(merchant.getId(), body.getApplicationId());

        if (ObjectUtils.isEmpty(bharatPeEnach)) {
            ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
            responseDTO.setResponse(false);
            responseDTO.setMessage("Enach not initiated");
            finalResponse = new ResponseEntity<>(responseDTO, HttpStatus.OK);
            return finalResponse;
        }

        if ( bharatPeEnach.getClientName().equalsIgnoreCase("LENDING")) {
            finalResponse = new ResponseEntity<>(eNachService.submitEnach(merchant, body, token), HttpStatus.OK);
        } else {
            finalResponse = new ResponseEntity<>(bpEnachService.submitEnach(merchant, body, token), HttpStatus.OK);
        }
        logger.info("Enach submit response for merchant: {} is: {}", merchant.getId(),finalResponse.getBody());
        return finalResponse;
    }
//
//    @RequestMapping(value = "/skip", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
//    public ResponseEntity<ResponseDTO> skipEnach(@RequestAttribute BasicDetailsDto merchant, @RequestParam(name = "reference_number", required = true) String referenceNumber) {
//        return new ResponseEntity<>(bpEnachService.setEnachSkipStatus(merchant, referenceNumber), HttpStatus.OK);
//    }

}
