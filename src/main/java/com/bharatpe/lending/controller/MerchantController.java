package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.dao.LendingMerchantDetailsDao;
import com.bharatpe.lending.common.dao.LendingPincodesDao;
import com.bharatpe.lending.common.entity.LendingMerchantDetails;
import com.bharatpe.lending.common.entity.LendingPincodes;
import com.bharatpe.lending.common.service.merchant.dto.*;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.constant.ErrorMessages;
import com.bharatpe.lending.loanV2.dto.BusinessDetailsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Slf4j
@RequestMapping("lending/merchant")
public class MerchantController {

    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingPincodesDao lendingPincodesDao;

    @Autowired
    LendingMerchantDetailsDao lendingMerchantDetailsDao;

    /**
     * Add Merchant Address
     * @param merchant
     * @param payload
     * @return
     */
    // return standard response
    @PutMapping("/address")
    public ResponseEntity<Object> addAddress(@RequestAttribute BasicDetailsDto merchant,
                                             @RequestBody ReqAddAddress payload) {
        log.info("Received request to add address for merchant: {}", merchant.getId());

        if (payload.getPincode() == null || payload.getState() == null || payload.getCity() == null ||
                payload.getAddress1() == null || payload.getAddress2() == null || payload.getArea() == null) {
            return buildErrorResponse("ERR_INVALID_ADDRESS", ErrorMessages.ADDRESS_REQUIRED);
        }

        MerchantDto merchantDto = convertToMerchantDto(merchant);

       AddAddressRes response = merchantService.addAddress(merchantDto.getMerchantId(), payload);

        if (ObjectUtils.isEmpty(response)) {
            return buildErrorResponse("ERR_ADDRESS_VALIDATION", ErrorMessages.ADDRESS_VALIDATION_ERROR);
        }

        return ResponseEntity.ok(Map.of("data", response, "status", true));
    }

    /**
     * Get Merchant Address List
     * @param merchant
     * @return
     */
    @GetMapping("/address")
    public ResponseEntity<Object> fetchAddress(@RequestAttribute("merchant") BasicDetailsDto merchant,  @RequestHeader("token") String token) {
        log.info("Fetching Address List for merchant: {}", merchant.getId());

        MerchantDto merchantDto = convertToMerchantDto(merchant);
        ListMerchantAddressResponseDto response = merchantService.getAddress(merchantDto,token);

        if (ObjectUtils.isEmpty(response) || ObjectUtils.isEmpty(response.getAddress())) {
            log.warn("No addresses found for merchant: {}", merchantDto.getMerchantId());
            return buildErrorResponse("ERR_NO_ADDRESSES", ErrorMessages.NO_ADDRESS_FOUND);
        }

        return ResponseEntity.ok(Map.of("data", response, "status", true));
    }

    @PostMapping("/updateBusinessName")
    public ResponseEntity<Object> updateBusinessName(@RequestAttribute("merchant") BasicDetailsDto merchant,
                                                     @RequestBody BusinessDetailsDTO businessDetailsDTO) {
        if (merchant == null || merchant.getId() == null) {
            return buildErrorResponse("ERR_INVALID_MERCHANT", ErrorMessages.MERCHANT_ID_MISSING);
        }

        if (businessDetailsDTO.getBusinessName() == null) {
            return buildErrorResponse("ERR_INVALID_BUSINESS_NAME", ErrorMessages.BUSINESS_NAME_REQUIRED);
        }

        log.info("Received request to update business name for merchant: {} and BusinessName : {}", merchant.getId(), businessDetailsDTO.getBusinessName());


        boolean isUpdated = merchantService.updateMerchantBusinessName(merchant.getId(), businessDetailsDTO.getBusinessName());
        LendingMerchantDetails lendingMerchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        lendingMerchantDetails.setBusinessName(businessDetailsDTO.getBusinessName());
        lendingMerchantDetailsDao.save(lendingMerchantDetails);

        if (!isUpdated) {
            return buildErrorResponse("ERR_BUSINESS_NAME_UPDATE", ErrorMessages.BUSINESS_NAME_UPDATE_FAILED);
        }

        return ResponseEntity.ok(Map.of("status", true));
    }

    @GetMapping("/pincode/{pincode}")
    public ResponseEntity<?> getLendingPincodeDetails(@PathVariable("pincode") Integer pincode) {
        try {
            LendingPincodes lendingPincodes = lendingPincodesDao.findByPincode(pincode);
            if (ObjectUtils.isEmpty(lendingPincodes)) {
                return ResponseEntity.status(404).body("Pincode not found.");
            }
            return ResponseEntity.ok(lendingPincodes);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("An error occurred while fetching pincode details.");
        }
    }

    private MerchantDto convertToMerchantDto(BasicDetailsDto basicDetailsDto) {
        MerchantDto merchantDto = new MerchantDto();
        merchantDto.setMerchantId(basicDetailsDto.getId());
        return merchantDto;
    }

    private ResponseEntity<Object> buildErrorResponse(String errorCode, String errorMessage) {
        log.warn("Error occurred: {} - {}", errorCode, errorMessage);
        return ResponseEntity.badRequest().body(Map.of("errorCode", errorCode, "errorMessage", errorMessage));
    }
}