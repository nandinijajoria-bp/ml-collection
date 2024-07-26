package com.bharatpe.lending.loanV3.utils;

import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingPancardDetailsDao;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.dto.PanFetchKYCResponseDto;
import com.bharatpe.lending.entity.LendingPancardDetails;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.dto.NameAndDobDetailsDto;
import com.bharatpe.lending.loanV3.dto.PoaXmlDTO;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.service.APIGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Period;
import java.time.ZoneId;
import java.util.*;

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

    @Autowired
    LendingPancardDetailsDao lendingPancardDetailsDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Value("${aws.s3.bucket}")
    String bucketName;

    @Value("${lender.kyc.pipe.lenders:}")
    String lenderKycPipeLenders;

    @Value("${abfl.lender.kyc.rollout.percent:1}")
    Integer abflLenderKycRolloutPercent;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    public CKycResponseDto getKycData(Long merchantId) {
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchantId);
        if (!basicDetailsDto.isPresent()) {
            return cKycResponseDto;
        }
        List<KycDoc> kycDocs = kycHandler.getKycDoc(merchantId, false, true);
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
                    cKycResponseDto.setPanDob(doc.getDob());
                    cKycResponseDto.setPanName(doc.getName());
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

    public NameAndDobDetailsDto getNameAndDobValues(CKycResponseDto cKycResponseDto, Long merchantId){
        NameAndDobDetailsDto nameAndDobDetailsDto = new NameAndDobDetailsDto();
        nameAndDobDetailsDto.setFirstName("");
        nameAndDobDetailsDto.setMiddleName("");
        nameAndDobDetailsDto.setLastName("");
        nameAndDobDetailsDto.setDob("");

        String fullName = "";
        String dob = "";

        fullName =!ObjectUtils.isEmpty(cKycResponseDto.getPanName()) ? cKycResponseDto.getPanName() : cKycResponseDto.getName();
        dob =!ObjectUtils.isEmpty(cKycResponseDto.getPanDob()) ? cKycResponseDto.getPanDob() : cKycResponseDto.getDob();
        if(ObjectUtils.isEmpty(cKycResponseDto.getPanDob())) {
            LendingPancardDetails lendingPancardDetails = lendingPancardDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if (!ObjectUtils.isEmpty(lendingPancardDetails)
                    && !ObjectUtils.isEmpty(lendingPancardDetails.getDob())
                    && !ObjectUtils.isEmpty(lendingPancardDetails.getName())
            ) {
                dob = lendingPancardDetails.getDob();
                fullName = lendingPancardDetails.getName();
            }
        }
        nameAndDobDetailsDto.setFullName(fullName);
        nameAndDobDetailsDto.setDob(dob);

        if(ObjectUtils.isEmpty(fullName)){
            return nameAndDobDetailsDto;
        }
        fullName = fullName.trim();
        if(fullName.contains(" ")){
            String temp = fullName;
            String firstName = temp.substring(0, fullName.indexOf(" ")).trim();
            temp = temp.substring(firstName.length()).trim();
            String middleName = "";
            if(temp.contains(" ")){
                middleName = temp.substring(0, temp.indexOf(" ")).trim();
            }
            temp = temp.substring(middleName.length()).trim();
            String lastName = temp;
            nameAndDobDetailsDto.setFirstName(firstName);
            nameAndDobDetailsDto.setMiddleName(middleName);
            nameAndDobDetailsDto.setLastName(lastName);
            return nameAndDobDetailsDto;
        }
        nameAndDobDetailsDto.setFirstName(fullName);
        return nameAndDobDetailsDto;
    }

    public CKycResponseDto getPanData(Long merchantId) {
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchantId);
        if (!basicDetailsDto.isPresent()) {
            return cKycResponseDto;
        }
        cKycResponseDto.setMobile(basicDetailsDto.get().getMobile());
        cKycResponseDto.setEmail(basicDetailsDto.get().getEmail());
        LendingPancardDetails lendingPanCard = lendingPancardDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
        if (ObjectUtils.isEmpty(lendingPanCard)) {
            return cKycResponseDto;
        }
        PanFetchKYCResponseDto panFetchKYCResponseDto = kycHandler.panFetch(lendingPanCard.getPancardNumber(), merchantId);
        if(!ObjectUtils.isEmpty(panFetchKYCResponseDto) && !ObjectUtils.isEmpty(panFetchKYCResponseDto.getData())) {
            cKycResponseDto.setPanNumber(panFetchKYCResponseDto.getData().getPanNumber());
            cKycResponseDto.setPanName(panFetchKYCResponseDto.getData().getName());
            cKycResponseDto.setPanDob(panFetchKYCResponseDto.getData().getDateOfBirth());
            cKycResponseDto.setGender(panFetchKYCResponseDto.getData().getGender());
        }
        log.info("ckyc response for Pan data {}", cKycResponseDto);
        return cKycResponseDto;
    }

    public CKycResponseDto parsePoaXML(String poaXml, Long merchantId, CKycResponseDto cKycResponseDto, Long applicationId, Boolean saveKycDetails) {
        try {
            if(!ObjectUtils.isEmpty(poaXml)) {
                log.info("poaXml {}", poaXml);
                XmlMapper xmlMapper = new XmlMapper();
                Map<String, Object> poaXmlMap = xmlMapper.readValue(poaXml, Map.class);
                if (poaXmlMap.containsKey("CertificateData")) {
                    PoaXmlDTO poaData = objectMapper.readValue(objectMapper.writeValueAsString(poaXmlMap.get("CertificateData")), PoaXmlDTO.class);
                    if (!ObjectUtils.isEmpty(poaData) && !ObjectUtils.isEmpty(poaData.getKycRes().getUidData())
                            && !ObjectUtils.isEmpty(poaData.getKycRes().getUidData().getPoi()) && !ObjectUtils.isEmpty(poaData.getKycRes().getUidData().getPoa())) {
                        PoaXmlDTO.Poi poi = poaData.getKycRes().getUidData().getPoi();
                        PoaXmlDTO.Poa poa = poaData.getKycRes().getUidData().getPoa();
                        cKycResponseDto.setAadharNumber(poaData.getKycRes().getUidData().getUid());
                        cKycResponseDto.setDob(DateTimeUtil.formatDate(poi.getDob(), "dd-MM-yyyy", "dd/MM/yyyy"));
                        cKycResponseDto.setName(poi.getName());
                        cKycResponseDto.setGender(poi.getGender());
                        String address = poa.getCo() + "," + poa.getHouse() + "," + poa.getPo() + "," + poa.getVtc() + "," + poa.getSubdist() + ","
                                + poa.getDist() + "," + poa.getLoc() + "," + poa.getState() + "," + poa.getPc();
                        cKycResponseDto.setAddress(address);
                        cKycResponseDto.setCity(poa.getDist());
                        cKycResponseDto.setState(poa.getState());
                        cKycResponseDto.setPincode(poa.getPc());
                        cKycResponseDto.setPoAXml(ConverterUtils.convertXmlToBase64String(poaXml));
                        cKycResponseDto.setPoaString(poaXml);
                        if (saveKycDetails) {
                            LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
                            if (!ObjectUtils.isEmpty(lendingApplicationKycDetails)) {
                                lendingApplicationKycDetails.setAadharAddress(address);
                                lendingApplicationKycDetails.setAadharApprovedAt(new Date());
                                lendingApplicationKycDetails.setAadharName(poi.getName());
                                lendingApplicationKycDetails.setFatherName(poa.getCo().contains("S/O") ? poa.getCo().substring(poa.getCo().indexOf(":") + 1) : null);
                                lendingApplicationKycDetails.setAadharXml(poaXml);
                                lendingApplicationKycDetails.setDob(poi.getDob());
                                lendingApplicationKycDetails.setAadharIdentifier(poaData.getKycRes().getUidData().getUid());
                                String fileName = applicationId + "_" + UUID.randomUUID() + ".jpeg";
                                lendingApplicationKycDetails.setAadharImage(s3BucketHandler.uploadToS3Bucket(poaData.getKycRes().getUidData().getPht(), fileName, bucketName));
                                lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.info("Exception in parsing poaXml for merchantId {} {}", merchantId, Arrays.asList(e.getStackTrace()));
        }
        return cKycResponseDto;
    }

    public Boolean isELigibleForLenderKyc(String lender, Long merchantId) {
        if(lenderKycPipeLenders.contains(lender)) {
            switch (lender) {
                case "ABFL" :
                    return easyLoanUtil.percentScaleUp(merchantId, abflLenderKycRolloutPercent);
                default:
                    return false;
            }
        }
        return false;
    }
}
