package com.bharatpe.lending.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("lending")
public class HandshakeController {

	@RequestMapping(value="/ping")
    public ResponseEntity<HttpStatus> ineligibleDetails() {
           return new ResponseEntity<>(HttpStatus.OK);
    }
}
