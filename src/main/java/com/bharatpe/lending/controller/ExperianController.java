package com.bharatpe.lending.controller;



import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.ExperianDetailsDTO;
import com.bharatpe.lending.dto.ExperianResponseDTO;
import com.bharatpe.lending.dto.InsertExperianRequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.dto.SendOtpDTO;
import com.bharatpe.lending.dto.UpdateExperianRequestDTO;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.ExperianService;
import com.bharatpe.lending.service.IExperianService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("experian")
public class ExperianController {

    Logger logger = LoggerFactory.getLogger(ExperianController.class);

    @Autowired
    ExperianService experianService;

    @Autowired
    IExperianService iExperianService;

    @RequestMapping(value="/details", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<ResponseDTO> experianDetails(

      @RequestAttribute BasicDetailsDto merchant, @RequestBody ExperianDetailsDTO experianDetailsDTO) {
        try {
            return new ResponseEntity<>(experianService.updateDetails(experianDetailsDTO, merchant.getId(), merchant.getMobile()),HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while updating experian details---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong", null,null), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/sendOTP", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<ResponseDTO> sendOTP(@RequestAttribute BasicDetailsDto merchant, @RequestBody SendOtpDTO requestDTO) {
        if (requestDTO.getMobile() == null) {
            return new ResponseEntity<>(new ResponseDTO(false, "Invalid Request", null,null), HttpStatus.OK);
        }
        try {
            return new ResponseEntity<>(experianService.sendOtp(requestDTO.getMobile(), merchant),HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while sending otp---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong", null,null), HttpStatus.OK);
        }
    }

    @RequestMapping(value="/verifyOTP", method = RequestMethod.POST, consumes="application/json", produces="application/json")
    public ResponseEntity<ResponseDTO> verifyOTP(@RequestAttribute BasicDetailsDto merchant, @RequestBody SendOtpDTO requestDTO) {
        if (requestDTO.getMobile() == null || requestDTO.getOtp() == null) {
            return new ResponseEntity<>(new ResponseDTO(false, "Invalid Request", null,null), HttpStatus.OK);
        }
        try {
            return new ResponseEntity<>(experianService.verifyOtp(requestDTO.getMobile(), merchant, requestDTO.getOtp(), requestDTO.isRetry()),HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Exception while sending otp---", e);
            return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong", null,null), HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<?> getByMerchantId(@RequestParam(name = "merchantId") Long merchantId) {
        logger.info("experian getByMerchantId request with merchantId : {} ", merchantId);

        if (ObjectUtils.isEmpty(merchantId) ) {
            return new ResponseEntity<>(new ApiResponse<>(false, "Required fields merchantId not sent"),
              HttpStatus.BAD_REQUEST);
        }

        final ExperianResponseDTO experianResponseDTO = iExperianService.findByMerchantId(merchantId);

        if (ObjectUtils.isEmpty(experianResponseDTO)) {
            return new ResponseEntity<>(new ApiResponse<>(false, "Experian not found"),
              HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(new ApiResponse<>(experianResponseDTO), HttpStatus.OK);
    }

    @RequestMapping(value = "/update", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> updateExperian(@RequestBody UpdateExperianRequestDTO updateExperianRequestDTO) {
        logger.info("update experian request with updateExperianRequestDTO : {} ", updateExperianRequestDTO.toString());

        if (ObjectUtils.isEmpty(updateExperianRequestDTO) || ObjectUtils.isEmpty(updateExperianRequestDTO.getId())) {
            return new ResponseEntity<>(new ApiResponse<>(false, "Required fields id not sent"),
              HttpStatus.BAD_REQUEST);
        }


        final ExperianResponseDTO experianResponseDTO = iExperianService.updateExperian(updateExperianRequestDTO);

        if (ObjectUtils.isEmpty(experianResponseDTO)) {
            return new ResponseEntity<>(new ApiResponse<>(false, "Experian not found"),
              HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(new ApiResponse<>(experianResponseDTO), HttpStatus.OK);

    }

    @RequestMapping(value = "/insert", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> insertExperian(@RequestBody InsertExperianRequestDTO insertExperianRequestDTO) {
        logger.info("insert experian request with insertExperianRequestDTO : {} ", insertExperianRequestDTO.toString());

        if (ObjectUtils.isEmpty(insertExperianRequestDTO) || ObjectUtils.isEmpty(insertExperianRequestDTO.getMerchantId())) {
            return new ResponseEntity<>(new ApiResponse<>(false, "Required fields merchantId not sent"),
              HttpStatus.BAD_REQUEST);
        }

        final ExperianResponseDTO experianResponseDTO = iExperianService.insertExperian(insertExperianRequestDTO);

        return new ResponseEntity<>(new ApiResponse<>(experianResponseDTO), HttpStatus.OK);
    }

}
