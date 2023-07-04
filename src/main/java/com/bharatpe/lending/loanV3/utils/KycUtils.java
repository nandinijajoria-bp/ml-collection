package com.bharatpe.lending.loanV3.utils;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class KycUtils {

    @Autowired
    KycHandler kycHandler;

    @Autowired
    MerchantService merchantService;

    @Autowired
    ConverterUtils converterUtils;

    public CKycResponseDto getKycData(Long merchantId) {
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchantId);
        if (!basicDetailsDto.isPresent()) {
            return cKycResponseDto;
        }
        List<KycDoc> kycDocs = kycHandler.getKycDoc(merchantId);
        if (ObjectUtils.isEmpty(kycDocs)){
            return cKycResponseDto;
        }
        cKycResponseDto.setMobile(basicDetailsDto.get().getMobile());
        cKycResponseDto.setEmail(basicDetailsDto.get().getEmail());
        for (KycDoc doc : kycDocs) {
            try {
                if ("POA".equalsIgnoreCase(doc.getDocType().name())) {
                    cKycResponseDto.setAddress(doc.getAddress());
                    cKycResponseDto.setCity(doc.getCity());
                    cKycResponseDto.setGender(doc.getGender());
                    cKycResponseDto.setPincode(doc.getPincode());
                    cKycResponseDto.setState(doc.getState());
                    cKycResponseDto.setPoAXml(ConverterUtils.convertXmlToBase64String(doc.getXml()));
                    cKycResponseDto.setPoAXml(ObjectUtils.isEmpty(cKycResponseDto.getPoAXml()) ? ConverterUtils.convertXmlToBase64String(doc.getDigioXml()) : cKycResponseDto.getPoAXml());
                    cKycResponseDto.setName(doc.getName());
                    cKycResponseDto.setDob(doc.getDob());
                    cKycResponseDto.setAadharNumber(doc.getDocIdentifier());
                    cKycResponseDto.setPoaString(ConverterUtils.convertXmlToString(doc.getXml()));
                    cKycResponseDto.setPoaString(ObjectUtils.isEmpty(cKycResponseDto.getPoaString()) ? ConverterUtils.convertXmlToString(doc.getDigioXml()) : cKycResponseDto.getPoAXml());

                } else if ("PAN_NO".equalsIgnoreCase(doc.getDocType().name())) {
                    cKycResponseDto.setPanNumber(doc.getDocIdentifier());
                    cKycResponseDto.setFirstName(doc.getFirstName());
                    cKycResponseDto.setMiddleName(doc.getMiddleName());
                    cKycResponseDto.setLastName(doc.getLastName());
                } else if ("SELFIE".equalsIgnoreCase(doc.getDocType().name())) {
                    cKycResponseDto.setSelfieBase64(ConverterUtils.convertPreSignedUrlToBase64String(doc.getDocFrontImageUrl()));
                    cKycResponseDto.setSelfieString(doc.getDocFrontImageUrl());
                }
            } catch (Exception e) {
                log.info("error in processing kyc doc {} {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
            }
        }
        log.info("ckyc response {}", cKycResponseDto);
        return cKycResponseDto;
    }

    public String getFirstName(CKycResponseDto cKycResponseDto) {
        String name = Optional.ofNullable(cKycResponseDto.getName()).orElse("").trim();
        String firstName = !ObjectUtils.isEmpty(cKycResponseDto.getName()) ?
                (name.indexOf(" ") == -1 ? name :
                        name.substring(0, name.indexOf(" ")).trim())
                : cKycResponseDto.getFirstName().trim();
        log.info("first name of name: {} {}", firstName, cKycResponseDto.getName());
        return converterUtils.parseNameData(firstName);
    }

    public String getMiddleName(CKycResponseDto cKycResponseDto) {
        String middleName = "";
        if (ObjectUtils.isEmpty(cKycResponseDto.getName())) {
            return converterUtils.parseNameData(cKycResponseDto.getMiddleName().trim());
        }
        String name = cKycResponseDto.getName();

        int firstOccurence = name.indexOf(" ");
        int lastOccurence = name.lastIndexOf(" ");
        if (firstOccurence == lastOccurence) {
            return middleName;
        }
        return converterUtils.parseNameData(name.substring(firstOccurence + 1, lastOccurence).trim());
    }

    public String getLastName(CKycResponseDto cKycResponseDto) {
        String name = Optional.ofNullable(cKycResponseDto.getName()).orElse("").trim();
        String lastName = !ObjectUtils.isEmpty(cKycResponseDto.getName()) ?
                (name.lastIndexOf(" ") == -1 ? name :
                        name.substring(name.lastIndexOf(" ") + 1).trim())
                : cKycResponseDto.getLastName().trim();
        log.info("last name for name: {}", lastName);
        return converterUtils.parseNameData(lastName);
    }
}
