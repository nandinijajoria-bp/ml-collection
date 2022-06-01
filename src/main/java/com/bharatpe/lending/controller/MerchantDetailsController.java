package com.bharatpe.lending.controller;

import com.bharatpe.lending.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.MerchantDetailsDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.UploadImageDTO;
import com.bharatpe.lending.service.MerchantDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending/documents")
public class MerchantDetailsController {

    Logger logger = LoggerFactory.getLogger(MerchantDetailsController.class);

    @Autowired
    MerchantDetailsService merchantDetailsService;

    @RequestMapping(value="/personalDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<MerchantDetailsDTO> updatePersonalDetails(@RequestAttribute BasicDetailsDto merchant, @RequestBody RequestDTO<MerchantDetailsDTO> requestDTO) {
        try {
            if (requestDTO.getPayload() == null || requestDTO.getPayload().getApplicationId() == null || requestDTO.getPayload().getPersonalDetails() == null) {
                logger.info("Invalid request to update merchant personal details");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(merchantDetailsService.updatePersonalDetails(requestDTO.getPayload(), merchant, requestDTO.getMeta().getLatitude(), requestDTO.getMeta().getLongitude()), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while updating merchant personal details", e);
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
    }

    @RequestMapping(value="/shopDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<MerchantDetailsDTO> updateShopDetails(@RequestAttribute BasicDetailsDto merchant, @RequestBody RequestDTO<MerchantDetailsDTO> requestDTO) {
        try {
            if (requestDTO.getPayload() == null || requestDTO.getPayload().getApplicationId() == null || requestDTO.getPayload().getShopDetails() == null) {
                logger.info("Invalid request to update merchant shop details");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(merchantDetailsService.updateShopDetails(requestDTO.getPayload(), merchant, requestDTO.getMeta().getLatitude(), requestDTO.getMeta().getLongitude()), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while updating merchant shop details", e);
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
    }

    @RequestMapping(value="/businessDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<MerchantDetailsDTO> updateBusinessDetails(@RequestAttribute BasicDetailsDto merchant, @RequestBody RequestDTO<MerchantDetailsDTO> requestDTO) {
        try {
            if (requestDTO.getPayload() == null || requestDTO.getPayload().getApplicationId() == null || requestDTO.getPayload().getBusinessDetails() == null) {
                logger.info("Invalid request to update merchant business details");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(merchantDetailsService.updateBusinessDetails(requestDTO.getPayload(), merchant, requestDTO.getMeta().getLatitude(), requestDTO.getMeta().getLongitude()), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while updating merchant business details", e);
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
    }

    @RequestMapping(value="/uploadImage", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<UploadImageDTO> uploadImage(@RequestAttribute BasicDetailsDto merchant, @RequestBody RequestDTO<UploadImageDTO> requestDTO) {
        try {
            if (requestDTO.getPayload() == null || requestDTO.getPayload().getApplicationId() == null || requestDTO.getPayload().getImageData() == null) {
                logger.info("Invalid request to upload image");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            UploadImageDTO uploadImageDTO = merchantDetailsService.uploadImage(requestDTO.getPayload(), merchant, requestDTO.getMeta().getLatitude(), requestDTO.getMeta().getLongitude());
            if (uploadImageDTO == null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            } else {
                return new ResponseEntity<>(uploadImageDTO, HttpStatus.OK);
            }
        } catch (Exception e) {
            logger.error("Exception while uploading image", e);
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
    }

    @RequestMapping(value="/deleteImage", method = RequestMethod.DELETE, consumes="application/json", produces="application/json")
    public ResponseEntity<UploadImageDTO> deleteImage(@RequestAttribute BasicDetailsDto merchant, @RequestBody RequestDTO<UploadImageDTO> requestDTO) {
        try {
            if (requestDTO.getPayload() == null || requestDTO.getPayload().getApplicationId() == null || requestDTO.getPayload().getImageData() == null || requestDTO.getPayload().getImageData().getImageId() == null) {
                logger.info("Invalid request to delete image");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            int deleteImage = merchantDetailsService.deleteImage(merchant, requestDTO.getPayload());
            if (deleteImage == 0) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            } else {
                return new ResponseEntity<>(requestDTO.getPayload(), HttpStatus.OK);
            }
        } catch (Exception e) {
            logger.error("Exception while deleting image", e);
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
    }

   @RequestMapping(value="/merchantDetails", method = RequestMethod.GET, consumes="application/json", produces="application/json")
    public ResponseEntity<MerchantDetailsDTO> merchantDetails(@RequestAttribute BasicDetailsDto merchant, @RequestParam Long applicationId,@RequestParam(required = false)String module) {
        try {
            logger.info("Fetching Merchant Details for merchant: {}", merchant.getId());
            return new ResponseEntity<>(merchantDetailsService.getMerchantDetails(applicationId,module), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while fetching merchant details", e);
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
    }
}
