package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.dao.NBFCPayoutDao;
import com.bharatpe.lending.service.NBFCPayoutService;
import com.bharatpe.lending.common.service.PennyDropService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("test")
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
}
