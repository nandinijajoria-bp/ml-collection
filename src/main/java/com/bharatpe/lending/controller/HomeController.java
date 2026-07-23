package com.bharatpe.lending.controller;
// doc-sync acceptance test: api-reference section — 23 Jul 2026
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

	@RequestMapping(value = "/")
	public ResponseEntity<String> ping() {
		return new ResponseEntity<>("Ok", HttpStatus.OK);
	}
}
