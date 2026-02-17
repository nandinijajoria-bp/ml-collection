package com.bharatpe.lending.loanV3.revamp.controller;

import com.bharatpe.lending.exception.CustomLendingException;
import com.bharatpe.lending.loanV3.revamp.dto.EnachStatusUpdateRequestDto;
import com.bharatpe.lending.loanV3.revamp.services.EnachCallbackHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/lending/enach")
@RequiredArgsConstructor
public class EnachCallbackController {

    private final EnachCallbackHandlerService enachCallbackHandlerService;

    @PostMapping("/status-callback")
    public ResponseEntity<Void> mandateStatusUpdateCallback(@RequestBody EnachStatusUpdateRequestDto requestDto) {
        log.info("Received eNACH Mandate Status Update Callback: {}", requestDto);
        try {
            enachCallbackHandlerService.updateMandateStatus(requestDto);
        }catch (Exception exception){
            log.error("exception occurred while processing mandate-status-update request for applicationId: {}. message: {}"
                    ,requestDto.getApplicationId(), exception.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        log.info("Processed eNACH Mandate Status Update Callback for Mandate ID: {}", requestDto.getMandateId());
        return ResponseEntity.ok().build();
    }
}
