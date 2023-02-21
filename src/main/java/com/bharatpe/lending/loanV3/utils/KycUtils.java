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

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class KycUtils {

    @Autowired
    KycHandler kycHandler;

    @Autowired
    MerchantService merchantService;

    public CKycResponseDto getKycData(Long merchantId) {
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchantId);
        if (!basicDetailsDto.isPresent()) {
            return cKycResponseDto;
        }
        List<KycDoc> kycDocs = kycHandler.getKycDoc(merchantId);
        cKycResponseDto.setMobile(basicDetailsDto.get().getMobile());
        cKycResponseDto.setEmail(basicDetailsDto.get().getEmail());
        for (KycDoc doc : kycDocs) {
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
            } else if ("PAN_NO".equalsIgnoreCase(doc.getDocType().name())) {
                cKycResponseDto.setPanNumber(doc.getDocIdentifier());
                cKycResponseDto.setFirstName(doc.getFirstName());
                cKycResponseDto.setMiddleName(doc.getMiddleName());
                cKycResponseDto.setLastName(doc.getLastName());
            } else if ("SELFIE".equalsIgnoreCase(doc.getDocType().name())) {
                cKycResponseDto.setSelfieBase64(ConverterUtils.convertPreSignedUrlToBase64String(doc.getDocFrontImageUrl()));
            }
        }
        log.info("ckyc response {}", cKycResponseDto);
        return cKycResponseDto;
    }
}
