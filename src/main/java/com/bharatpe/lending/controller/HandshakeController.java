package com.bharatpe.lending.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("lending/handshake/*")
public class HandshakeController {

	@RequestMapping(value="ping")
    public ResponseEntity<HttpStatus> ineligibleDetails() {
           return new ResponseEntity<>(HttpStatus.OK);
    }
}
