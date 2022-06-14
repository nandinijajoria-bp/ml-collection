package com.bharatpe.lending.controller;


import com.bharatpe.lending.common.entity.LendingPullPayment;
import com.bharatpe.lending.dto.LendingPullPaymentResponseDTO;
import com.bharatpe.lending.dto.UpdateLendingPullPaymentDto;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.ILendingPullPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/lending/pullPayment")
public class LendingPullPaymentController {

    @Autowired
    ILendingPullPaymentService iLendingPullPaymentService;

    @RequestMapping(method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> getLendingPullPaymentByMerchantIdAndMode(@RequestParam(name = "merchantId") Long merchantId,
                                                                      @RequestParam(name = "mode") String mode) {

        log.info("lendingPullPayment request with merchantId : {} and mode : {}", merchantId, mode);

        if (ObjectUtils.isEmpty(merchantId) || ObjectUtils.isEmpty(mode)) {
            return new ResponseEntity<>(new ApiResponse<>(false, "Required fields merchantId/mode not sent"),
              HttpStatus.BAD_REQUEST);
        }

        LendingPullPaymentResponseDTO lendingPullPaymentResponseDTO =
          iLendingPullPaymentService.getLendingPullPaymentByMerchantIdAndMode(merchantId, mode);

        if (ObjectUtils.isEmpty(lendingPullPaymentResponseDTO)) {
            return new ResponseEntity<>(new ApiResponse<>(false, "Lending pull payment not found"),
              HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(new ApiResponse<>(lendingPullPaymentResponseDTO), HttpStatus.OK);
    }

    @RequestMapping(value = "/byOwner", method = RequestMethod.GET, consumes = "application/json", produces =
      "application/json")
    public ResponseEntity<?> getLendingPullPaymentByMerchantIdAndOwnerIdAndMode(@RequestParam(name = "merchantId") Long merchantId,
                                                                                @RequestParam(name = "ownerId") Long ownerId,
                                                                                @RequestParam(name = "mode") String mode) {

        log.info("lendingPullPayment request with merchantId : {} ownerId : {} mode : {}", merchantId, ownerId, mode);

        if (ObjectUtils.isEmpty(merchantId) || ObjectUtils.isEmpty(mode) || ObjectUtils.isEmpty(ownerId)) {
            return new ResponseEntity<>(new ApiResponse<>(false, "Required fields merchantId/mode/ownerId not sent"),
              HttpStatus.BAD_REQUEST);
        }

        LendingPullPaymentResponseDTO lendingPullPaymentResponseDTO = iLendingPullPaymentService.getLendingPullPaymentByMerchantIdAndModeAndOwnerId(merchantId, mode, ownerId);

        if (ObjectUtils.isEmpty(lendingPullPaymentResponseDTO)) {
            return new ResponseEntity<>(new ApiResponse<>(false, "Lending pull payment not found"),
              HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(new ApiResponse<>(lendingPullPaymentResponseDTO), HttpStatus.OK);
    }

    @RequestMapping(value = "/dueAmountGreaterThan", method = RequestMethod.GET, consumes = "application/json", produces =
      "application/json")
    public ResponseEntity<?> getLendingPullPaymentWithDueAmountGreaterThan(@RequestParam(name = "merchantId") Long merchantId,
                                                                           @RequestParam(name = "ownerId") Long ownerId,
                                                                           @RequestParam(name = "mode") String mode,
                                                                           @RequestParam(name = "dueAmount") Double dueAmount) {

        log.info("lendingPullPayment request with merchantId : {} ownerId : {} mode : {} dueAmount : {}", merchantId, ownerId, mode, dueAmount);

        if (ObjectUtils.isEmpty(merchantId) || ObjectUtils.isEmpty(mode) || ObjectUtils.isEmpty(ownerId) || ObjectUtils.isEmpty(dueAmount)) {
            return new ResponseEntity<>(new ApiResponse<>(false, "Required fields merchantId/mode/ownerId/dueAmount not sent"),
              HttpStatus.BAD_REQUEST);
        }

        List<LendingPullPaymentResponseDTO> lendingPullPaymentResponseDTOList =
          iLendingPullPaymentService.getLendingPullPaymentWithDueAmountGreaterThan(merchantId, mode, ownerId, dueAmount);


        return new ResponseEntity<>(new ApiResponse<>(lendingPullPaymentResponseDTOList), HttpStatus.OK);
    }

    @RequestMapping(value = "/update", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> updateLendingPullPayment(@RequestBody UpdateLendingPullPaymentDto updateLendingPullPaymentDto) {
        log.info("update lendingPullPayment request with updateLendingPullPaymentDto : {} ", updateLendingPullPaymentDto.toString());

        if (ObjectUtils.isEmpty(updateLendingPullPaymentDto) || ObjectUtils.isEmpty(updateLendingPullPaymentDto.getId())) {
            return new ResponseEntity<>(new ApiResponse<>(false, "Required fields id not sent"),
              HttpStatus.BAD_REQUEST);
        }

        LendingPullPaymentResponseDTO lendingPullPaymentResponseDTO =  iLendingPullPaymentService.updateLendingPullPayment(updateLendingPullPaymentDto);

        if (ObjectUtils.isEmpty(lendingPullPaymentResponseDTO)) {
            return new ResponseEntity<>(new ApiResponse<>(false, "Lending pull payment not found"),
              HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(new ApiResponse<>(lendingPullPaymentResponseDTO), HttpStatus.OK);
    }
}
