package com.bharatpe.lending.loanV2.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class HealthCheckController {

    @GetMapping(value = "/health-check")
    public void healthCheck()
    {
        log.info("OK");
    }
}
