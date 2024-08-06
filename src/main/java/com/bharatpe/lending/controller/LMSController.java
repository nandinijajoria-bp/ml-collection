package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.PaymentDetailsResponseDTO;
import com.bharatpe.lending.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class LMSController {

    @Autowired
    private PaymentService paymentService;

    @RequestMapping(value="/lms/payment/details", method = RequestMethod.GET, produces="application/json")
    public ResponseEntity<PaymentDetailsResponseDTO> getPaymentDetails(@RequestParam(name = "merchant_id", required = false) Long merchant_id,
                                                                       @RequestParam(required = false, defaultValue = "true") Boolean showForeClosureDetails) {
        if (ObjectUtils.isEmpty(merchant_id)) {
            PaymentDetailsResponseDTO paymentDetailsResponseDTO = new PaymentDetailsResponseDTO();
            paymentDetailsResponseDTO.setSuccess(false);
            paymentDetailsResponseDTO.setMessage("invalid merchant id received");
            return new ResponseEntity<>(paymentDetailsResponseDTO, HttpStatus.BAD_REQUEST);
        } else {
            BasicDetailsDto basicDetailsDto = new BasicDetailsDto();
            basicDetailsDto.setId(merchant_id);
            return new ResponseEntity<>(paymentService.getPaymentDetails(basicDetailsDto, showForeClosureDetails), HttpStatus.OK);
        }
    }

}
