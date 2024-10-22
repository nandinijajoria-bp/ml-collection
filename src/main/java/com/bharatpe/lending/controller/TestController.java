package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.dao.NBFCPayoutDao;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.dto.AbflDigiSignResponseDTO;
import com.bharatpe.lending.loanV3.dto.ManualDigiSignInvoke;
import com.bharatpe.lending.loanV3.services.associations.ABFLDigiSignService;
import com.bharatpe.lending.service.NBFCPayoutService;
import com.bharatpe.lending.common.service.PennyDropService;
import com.itextpdf.text.DocumentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("test")
@Slf4j
public class TestController {

    @Autowired
    private NBFCPayoutDao nbfcPayoutDao;

    @Autowired
    private NBFCPayoutService nbfcPayoutService;

    @Autowired
    private PennyDropService pennyDropService;

    @RequestMapping(value = "/triggerNBFCPayout", method = RequestMethod.GET)
    public ResponseEntity<String> initiateEnach(@RequestParam Long nbfcPayoutId) {
        nbfcPayoutService.notifyLender(nbfcPayoutDao.findById(nbfcPayoutId).get());
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }

    @RequestMapping(value = "/initiatePennyDrop", method = RequestMethod.GET)
    public ResponseEntity<String> initiatePennyDrop(@RequestParam Long merchantId) {
        pennyDropService.initiateNewPennyDrop(merchantId);
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }


    @Autowired
    ABFLDigiSignService abflDigiSignService2;

    @PostMapping("manual-abfl-digisign-invoke")
    public ResponseEntity<ApiResponse<?>> manualDigiSignInvoke(@RequestBody ManualDigiSignInvoke manualDigiSignInvoke) throws IOException, DocumentException {
        log.info("manualDigiSignInvoke hit received via controller {}", manualDigiSignInvoke);
        AbflDigiSignResponseDTO abflDigiSignResponseDTO=abflDigiSignService2.invoke(manualDigiSignInvoke.getApplicationId(),manualDigiSignInvoke.getArgs());
        return ResponseEntity.ok(new ApiResponse<>(true,abflDigiSignResponseDTO.toString()));
    }
}
