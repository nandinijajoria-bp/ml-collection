package com.bharatpe.lending.loanV3.utils;

import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.service.APIGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Period;
import java.time.ZoneId;
import java.util.*;

import static com.bharatpe.lending.enums.KycDocType.PAN_CARD;

@Component
@Slf4j
public class KycUtils {

    @Autowired
    KycHandler kycHandler;

    @Autowired
    MerchantService merchantService;

    @Autowired
    ConverterUtils converterUtils;

    @Autowired
    APIGatewayService apiGatewayService;

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
                    cKycResponseDto.setPoAXml(Objects.isNull(doc.getXml()) ? null: ConverterUtils.convertXmlToBase64String(doc.getXml()));
                    cKycResponseDto.setPoAXml(ObjectUtils.isEmpty(cKycResponseDto.getPoAXml()) ? ConverterUtils.convertXmlToBase64String(doc.getDigioXml()) : cKycResponseDto.getPoAXml());
                    cKycResponseDto.setName(doc.getName());
                    cKycResponseDto.setDob(doc.getDob());
                    cKycResponseDto.setAadharNumber(doc.getDocIdentifier());
                    cKycResponseDto.setPoaString(Objects.isNull(doc.getXml()) ? null : ConverterUtils.convertXmlToString(doc.getXml()));
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

    public static String getDOB(KycDoc kycDoc) {
        if (ObjectUtils.isEmpty(kycDoc)) {
            return null;
        }
        return kycDoc.getDob();
    }

    public static Date getFormattedDate(String dateString) {
        try {
            if (StringUtils.isBlank(dateString)) {
                return null;
            }
            DateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            sdf.setLenient(false);
            return sdf.parse(dateString);
        } catch (ParseException e) {
            try {
                DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                sdf.setLenient(false);
                return sdf.parse(dateString);
            } catch (ParseException ex) {
                try {
                    DateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
                    sdf.setLenient(false);
                    return sdf.parse(dateString);
                } catch (ParseException exception) {
                    log.info("Unable to parse date" + dateString);
                    return null;
                }
            }
        }
    }

    public String getGender(String gender) {
        if (Objects.nonNull(gender) && ("M".equalsIgnoreCase(gender) || "MALE".equalsIgnoreCase(gender))) {
            return "MALE";
        }
        if (Objects.nonNull(gender) && ("F".equalsIgnoreCase(gender) || "FEMALE".equalsIgnoreCase(gender))) {
            return "FEMALE";
        }
        return "OTHERS";
    }

    public String getMobileFromKycData(CKycResponseDto cKycResponseDto){
        return ObjectUtils.isEmpty(cKycResponseDto.getMobile()) ? "" : cKycResponseDto.getMobile().substring(2);
    }

    public Integer getAgeFromDob(String dob) {
        if(ObjectUtils.isEmpty(dob)) {
            return 0;
        }
        Date birthDate = apiGatewayService.parseKycDob(dob);
        if(ObjectUtils.isEmpty(birthDate)) {
            return 0;
        }
        Date currentDate = new Date();
        return Period.between(
                birthDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                currentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        ).getYears();
    }
}
