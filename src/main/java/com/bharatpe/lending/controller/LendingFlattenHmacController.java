package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.bharatpe.lending.common.util.LendingHmacCalculator;

import java.util.Map;

/**
 * THIS API IS USED FOR CASES WHERE FLATTEN PAYLOAD IS SENT FOR HMAC CALCULATION
 */

@RestController
@RequestMapping("lending")
public class LendingFlattenHmacController {

    @Autowired
    LendingHmacCalculator lendingHmacCalculator;

    @Autowired
    AesEncryptionUtil aesEncryptionUtil;

    @Autowired
    InternalClientDaoSlave internalClientDaoSlave;

    @RequestMapping(value="/hash", method= RequestMethod.POST)
    public ResponseEntity<String> generateHash(@RequestBody Map<String, Object> requestMap, @RequestHeader(name = "clientName") String clientName){
        InternalClientSlave internalClient = internalClientDaoSlave.findByClientName(clientName);
        if (internalClient != null) {
//			logger.info("lending secret:{} header:{}", aesEncryptionUtil.decrypt(internalClient.getSecret()), clientName);
            String hash = lendingHmacCalculator.calculateHmac(lendingHmacCalculator.getNestedPayload(requestMap), aesEncryptionUtil.decrypt(internalClient.getSecret()));
            return new ResponseEntity<>(hash, HttpStatus.OK);
        }
        return null;
    }

}
