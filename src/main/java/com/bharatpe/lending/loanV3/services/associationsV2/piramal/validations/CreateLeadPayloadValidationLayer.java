package com.bharatpe.lending.loanV3.services.associationsV2.piramal.validations;

import com.bharatpe.lending.loanV3.NameDetailsDto;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.time.Period;
import java.time.ZoneId;
import java.util.Date;

@Component
@Slf4j
public class CreateLeadPayloadValidationLayer {

    @Autowired
    KycUtils kycUtils;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LoanUtil loanUtil;

    public boolean isInValidPayload(CKycResponseDto cKycResponseDto) {
        NameDetailsDto nameDetailsDto = loanUtil.getSegregatedNameDetails(cKycResponseDto);
        return ( ObjectUtils.isEmpty(nameDetailsDto.getFirstName()) ||
                ObjectUtils.isEmpty(nameDetailsDto.getLastName()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getMobile()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getDob()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getCity()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getAadharNumber()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getAddress()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getPincode()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getState()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getPanNumber()) ||
                isInValidAge(cKycResponseDto.getDob())
        );
    }

    private Boolean isInValidAge(String dob) {
       if(ObjectUtils.isEmpty(dob)) {
           return true;
       }
       Date birthDate = apiGatewayService.parseKycDob(dob);
       if(ObjectUtils.isEmpty(birthDate)) {
           return true;
       }
       Date currentDate = new Date();
       int age = Period.between(
               birthDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
               currentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
       ).getYears();
       if(age < 21 || age > 65) {
           return true;
       }
       return false;
    }
}
