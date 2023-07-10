package com.bharatpe.lending.loanV2.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.Gst3bSessionCallbackDto;
import com.bharatpe.lending.loanV2.dto.Gst3bSessionRequestDTO;
import com.bharatpe.lending.loanV2.service.Gst3bService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/lending")
@Slf4j
public class Gst3bController {
    @Autowired
    Gst3bService gst3bService;

    @PostMapping(value = "/gst3b/send-otp")
    public ResponseEntity<?> gst3bSendOtp(
            @RequestBody Gst3bSessionRequestDTO gst3bSessionRequestDTO,
            @RequestAttribute(name = "merchant", required = false) BasicDetailsDto merchant) {

        ApiResponse apiResponse = gst3bService.sendOtpGst3b(gst3bSessionRequestDTO, merchant.getId());
        if (!apiResponse.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
        }
        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping(value = "/gst3b/verify-otp")
    public ResponseEntity<?> gst3bVerifyOtp(
            @RequestBody Gst3bSessionRequestDTO gst3bSessionRequestDTO,
            @RequestAttribute(name = "merchant", required = false) BasicDetailsDto merchant) {

        ApiResponse apiResponse = gst3bService.verifyOtpGst3b(gst3bSessionRequestDTO);
        if (!apiResponse.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
        }
        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping(value = "/gst3b/upload")
    public ResponseEntity<?> gst3bUpload(
            @RequestBody Gst3bSessionRequestDTO gst3bSessionRequestDTO,
            @RequestAttribute(name = "merchant", required = false) BasicDetailsDto merchant) {
        ApiResponse apiResponse = gst3bService.uploadGst3bFile(gst3bSessionRequestDTO, merchant.getId());
        if (!apiResponse.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
        }
        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping(value = "/gst3b/session-list")
    public ResponseEntity<?> gst3bSessionList(
            @RequestAttribute(name = "merchant", required = false) BasicDetailsDto merchant) {
        return ResponseEntity.ok(gst3bService.getGst3bSessionList(merchant.getId()));
    }

    @PostMapping(value = "/gst3b/session/callback")
    public ResponseEntity<?> gst3bCallback(
            @RequestBody Gst3bSessionCallbackDto sessionCallbackDto) {
        log.info("Received callback for gst3b session details : {}", sessionCallbackDto);
        gst3bService.gst3bSessionCallback(sessionCallbackDto);
        return ResponseEntity.ok("Successfully consume gst3b callback");
    }

}
