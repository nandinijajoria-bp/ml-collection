package com.bharatpe.lending.controller;

import com.bharatpe.lending.dto.LoanEligibilityDTO;
import com.bharatpe.lending.dto.UnderwritingPostProcessingRequest;

import com.bharatpe.lending.loanV3.dto.TopupEligibilityResponseData;
import com.bharatpe.lending.service.UnderwritingPostProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * REST Controller for underwriting post-processing operations
 * Exposes the post-processing logic extracted from AdditionalTopupRuleEngine
 */
@RestController
@RequestMapping("/Lending")
public class UnderwritingPostProcessingController {

    private static final Logger logger = LoggerFactory.getLogger(UnderwritingPostProcessingController.class);

    @Autowired
    private UnderwritingPostProcessingService underwritingPostProcessingService;

    /**
     * POST endpoint to trigger underwriting post-processing logic
     *
     * @param request Contains merchantId and GlobalLimitResponse
     * @return List of LoanEligibilityDTO objects
     */
    @PostMapping("/post-risk-process")
    public ResponseEntity<TopupEligibilityResponseData> postProcess(@RequestBody UnderwritingPostProcessingRequest request) {

        logger.info("Received underwriting post-processing request for merchant: {}", request.getMerchantId());

        try {
            TopupEligibilityResponseData result = underwritingPostProcessingService.postUnderwritingProcess(request,true,null);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error processing underwriting post-processing request for merchant: {}",
                        request.getMerchantId(), e);
            
            return ResponseEntity.status(500).build();
        }
    }
    
    /**
     * Health check endpoint for the underwriting post-processing service
     */
    @GetMapping("/post-process/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Underwriting Post-Processing Service is running");
    }
}
