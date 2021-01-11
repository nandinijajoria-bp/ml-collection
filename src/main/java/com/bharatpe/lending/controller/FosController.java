package com.bharatpe.lending.controller;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.service.FosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("fos")
public class FosController {

    Logger logger = LoggerFactory.getLogger(FosController.class);

    @Autowired
    FosService fosService;

    @RequestMapping(value="/loan", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<ResponseDTO> fosLoanDetails(@RequestParam Long merchantId) {
        return new ResponseEntity<>(fosService.fosLoan(merchantId), HttpStatus.OK);
    }

    @RequestMapping(value="/v2/loan", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<ResponseDTO> fosnewLoanDetails(@RequestParam Long merchantId) {
        return new ResponseEntity<>(fosService.fosnewLoan(merchantId), HttpStatus.OK);
    }

    @RequestMapping(value="/nach/update", method = RequestMethod.POST, produces="application/json")
    public ResponseEntity<ResponseDTO> fosUpdate(@RequestBody Map<String,Object> requestDTO) {
        return new ResponseEntity<>(fosService.fosUpdate(requestDTO), HttpStatus.OK);
    }
}
