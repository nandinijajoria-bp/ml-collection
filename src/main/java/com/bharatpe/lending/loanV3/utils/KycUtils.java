package com.bharatpe.lending.loanV3.utils;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.dao.LmsFieldValuesDao;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.entity.LmsFieldValues;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingPancardDetailsDao;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.dto.PanFetchKYCResponseDto;
import com.bharatpe.lending.entity.LendingPancardDetails;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV2.dto.BureauDataResponseDTO;
import com.bharatpe.lending.loanV2.handlers.BureauHandler;
import com.bharatpe.lending.loanV3.config.TrillionLoansConfig;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.enums.KycMode;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import com.bharatpe.lending.service.APIGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Period;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.bharatpe.lending.constant.LendingConstants.BUSINESS_CATEGORY_LMS_FIELD_ID;
import static com.bharatpe.lending.constant.LendingConstants.BUSINESS_SUBCATEGORY_LMS_FIELD_ID;

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

    @Value("${piramal.lender.kyc.rollout.percent:1}")
    Integer piramalLenderKycRolloutPercent;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    @Autowired
    LmsFieldValuesDao lmsFieldValuesDao;

    @Autowired
    DsHandler dsHandler;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    BureauHandler bureauHandler;

    @Autowired
    @Lazy
    TrillionLoansConfig trillionLoansConfig;

    @Autowired
    @Lazy
    LoanUtilV3 loanUtilV3;

    @Value("${skip.kyc.enabled.lenders:}")
    String skipKycEnabledLenders;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    public CKycResponseDto getKycData(Long merchantId) {
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchantId);
        if (!basicDetailsDto.isPresent()) {
            return cKycResponseDto;
        }
        List<KycDoc> kycDocs = kycHandler.getKycDoc(merchantId, false, true, "PAN_NO,SELFIE,POA");
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
                    cKycResponseDto.setAadhaarPanNameMatchPer(doc.getNameMatchPer());

                } else if ("PAN_NO".equalsIgnoreCase(doc.getDocType().name())) {
                    cKycResponseDto.setPanNumber(doc.getDocIdentifier());
                    cKycResponseDto.setFirstName(doc.getFirstName());
                    cKycResponseDto.setMiddleName(doc.getMiddleName());
                    cKycResponseDto.setLastName(doc.getLastName());
                    cKycResponseDto.setPanDob(doc.getDob());
                    cKycResponseDto.setPanName(doc.getName());
                    cKycResponseDto.setBankBenePanNameMatchPer(doc.getNameMatchPer());
                } else if ("SELFIE".equalsIgnoreCase(doc.getDocType().name())) {
                    cKycResponseDto.setSelfieBase64(ConverterUtils.convertPreSignedUrlToBase64String(doc.getDocFrontImageUrl()));
                    cKycResponseDto.setSelfieString(doc.getDocFrontImageUrl());
                    cKycResponseDto.setSelfieLivelinessScore(doc.getLivelinessScore());
                    cKycResponseDto.setSelfieAadhaarFaceMatchPer(getParsedFaceMatchPer(doc.getFaceMatchPer()));
                }
            } catch (Exception e) {
                log.info("error in processing kyc doc {} {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
            }
        }
        BureauDataResponseDTO bureauDataResponse = bureauHandler.getBureauData(merchantId, cKycResponseDto.getMobile());
        if(!ObjectUtils.isEmpty(bureauDataResponse) && !ObjectUtils.isEmpty(bureauDataResponse.getBureauMobile())) {
            String bureauMobile = bureauDataResponse.getBureauMobile();
            if(bureauMobile.length() == 12) {
                bureauMobile = bureauMobile.substring(2);
            }
            cKycResponseDto.setBureauMobile(bureauMobile);
        }
        log.info("ckyc response {}", cKycResponseDto);
        return cKycResponseDto;
    }

    public CKycResponseDto getPanAndAadhaarData(Long merchantId) {
        CKycResponseDto cKycResponseDto = new CKycResponseDto();
        try{
            List<KycDoc> kycDocs = kycHandler.getKycDoc(merchantId, false, true, "PAN_NO,POA");
            if (ObjectUtils.isEmpty(kycDocs)){
                return cKycResponseDto;
            }

            for (KycDoc doc : kycDocs) {
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
                    cKycResponseDto.setAadhaarPanNameMatchPer(doc.getNameMatchPer());

                }else if ("PAN_NO".equalsIgnoreCase(doc.getDocType().name())) {
                    cKycResponseDto.setPanNumber(doc.getDocIdentifier());
                    cKycResponseDto.setFirstName(doc.getFirstName());
                    cKycResponseDto.setMiddleName(doc.getMiddleName());
                    cKycResponseDto.setLastName(doc.getLastName());
                    cKycResponseDto.setPanDob(doc.getDob());
                    cKycResponseDto.setPanName(doc.getName());
                    cKycResponseDto.setBankBenePanNameMatchPer(doc.getNameMatchPer());
                }
            }
        }catch (Exception e){
            log.info("error in processing poa doc {} {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
        }

        log.info("ckyc poa response {}", cKycResponseDto);
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

    public static String getFatherName(String careOf) {
        try {
            if (ObjectUtils.isEmpty(careOf) || !careOf.contains("S/O")) {
                return null;
            }
            careOf = careOf.toUpperCase();
            careOf = careOf.replaceAll("S/O", "").replaceAll("\\.", "").replaceAll(":", "").replaceFirst(" ", "");
            return careOf.substring(0, careOf.indexOf(","));
        } catch (Exception e) {
            log.info("Exception in fetching father name from kyc address {}", Arrays.asList(e.getStackTrace()));
        }
        return null;
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
        BureauDataResponseDTO bureauDataResponse = bureauHandler.getBureauData(merchantId, cKycResponseDto.getMobile());
        if(!ObjectUtils.isEmpty(bureauDataResponse) && !ObjectUtils.isEmpty(bureauDataResponse.getBureauMobile())) {
            String bureauMobile = bureauDataResponse.getBureauMobile();
            if(bureauMobile.length() == 12) {
                bureauMobile = bureauMobile.substring(2);
            }
            cKycResponseDto.setBureauMobile(bureauMobile);
        }
        log.info("ckyc response for Pan data {}", cKycResponseDto);
        return cKycResponseDto;
    }

    public CKycResponseDto parsePoaXML(String poaXml, Long merchantId, CKycResponseDto cKycResponseDto) {
        try {
            if (!ObjectUtils.isEmpty(poaXml)) {
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
                        cKycResponseDto.setAddress(constructKycAddress(poa));
                        cKycResponseDto.setCity(poa.getDist());
                        cKycResponseDto.setState(poa.getState());
                        cKycResponseDto.setPincode(poa.getPc());
                        cKycResponseDto.setPoAXml(ConverterUtils.convertXmlToBase64String(poaXml));
                        cKycResponseDto.setPoaString(poaXml);
                        cKycResponseDto.setCareOf(poa.getCo());
                        cKycResponseDto.setLm(poa.getLm());
                        cKycResponseDto.setVtc(poa.getVtc());
                        cKycResponseDto.setHouse(poa.getHouse());
                        cKycResponseDto.setStreet(poa.getStreet());
                        cKycResponseDto.setLoc(poa.getLoc());
                        cKycResponseDto.setPo(poa.getPo());
                        cKycResponseDto.setSubdist(poa.getSubdist());
                        cKycResponseDto.setDist(poa.getDist());
                    }
                }
            }
        } catch (Exception e) {
            log.info("Exception in parsing poaXml for merchantId {} {}", merchantId, Arrays.asList(e.getStackTrace()));
        }
        return cKycResponseDto;
    }

    private String constructKycAddress(PoaXmlDTO.Poa poa) {
        String address = Stream.of(poa.getHouse(), poa.getLoc(), poa.getStreet(), poa.getLm(),
                        poa.getVtc(), poa.getDist(), poa.getPc(), poa.getState(), poa.getCountry())
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        return address;
    }

    public void savePoaDetailsForKyc(LendingApplication application, String kycMode, CKycResponseDto cKycResponseDto) {
        LendingApplicationKycDetails prevKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndConsentDateNotNull(application.getId());
        LendingApplicationKycDetails lendingApplicationKycDetails = Optional.ofNullable(lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderAndKycModeOrderByIdDesc(application.getId(), application.getLender(), kycMode)).orElse(new LendingApplicationKycDetails());
        if (!ObjectUtils.isEmpty(lendingApplicationKycDetails)) {
            lendingApplicationKycDetails.setApplicationId(application.getId());
            lendingApplicationKycDetails.setLender(application.getLender());
            lendingApplicationKycDetails.setMerchantId(application.getMerchantId());
            lendingApplicationKycDetails.setKycMode(kycMode);
            lendingApplicationKycDetails.setAadharAddress(cKycResponseDto.getAddress());
            lendingApplicationKycDetails.setAadharName(cKycResponseDto.getName());
            lendingApplicationKycDetails.setFatherName(getFatherName(cKycResponseDto.getCareOf() + ","));
            lendingApplicationKycDetails.setDob(cKycResponseDto.getDob());
            lendingApplicationKycDetails.setAadharIdentifier(cKycResponseDto.getAadharNumber());
            lendingApplicationKycDetails.setGender(cKycResponseDto.getGender());
            lendingApplicationKycDetails.setAadharState(cKycResponseDto.getState());
            lendingApplicationKycDetails.setAadharCity(cKycResponseDto.getCity());
            lendingApplicationKycDetails.setAadharPinCode(cKycResponseDto.getPincode());
            lendingApplicationKycDetails.setAadharApprovedAt(!KycMode.SKIP_KYC.name().equalsIgnoreCase(kycMode) ? new Date() : lendingApplicationKycDetails.getAadharApprovedAt());
            lendingApplicationKycDetails.setKycInitiatedAt(KycMode.SKIP_KYC.name().equalsIgnoreCase(kycMode) ? new Date() : lendingApplicationKycDetails.getKycInitiatedAt());
            if(!ObjectUtils.isEmpty(prevKycDetails)) {
                lendingApplicationKycDetails.setSelfieUrl(prevKycDetails.getSelfieUrl());
                lendingApplicationKycDetails.setSelfieApprovedAt(prevKycDetails.getSelfieApprovedAt());
                lendingApplicationKycDetails.setPan(prevKycDetails.getPan());
                lendingApplicationKycDetails.setPanApprovedAt(prevKycDetails.getPanApprovedAt());
                lendingApplicationKycDetails.setKycInitiatedAt(prevKycDetails.getKycInitiatedAt());
                lendingApplicationKycDetails.setConsentDate(prevKycDetails.getConsentDate());
                lendingApplicationKycDetails.setSelfieImage(prevKycDetails.getSelfieImage());
                lendingApplicationKycDetails.setPanUrl(prevKycDetails.getPanUrl());
            }
            lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
        }
    }

    public Boolean isEligibleForLenderKyc(String lender, Long merchantId, boolean isTopup) {
        if(lenderKycPipeLenders.contains(lender)) {
            switch (lender) {
                case "ABFL" :
                    return easyLoanUtil.percentScaleUp(merchantId, abflLenderKycRolloutPercent);
                case "PIRAMAL":
                    return easyLoanUtil.percentScaleUp(merchantId, piramalLenderKycRolloutPercent);
                case "TRILLIONLOANS":
                    return !isTopup && (!trillionLoansConfig.getTrillionEkycPhaseRollout() || loanUtilV3.eKycPhaseRollout(lender, merchantId));
                default:
                    return false;
            }
        }
        return false;
    }

    public Map<String, String> getShopAddressLatLong(LendingApplication lendingApplication) {
        try {
            Map<String, String> latLong = new HashMap<>();
            if (!ObjectUtils.isEmpty(lendingApplication.getLatitude()) && !ObjectUtils.isEmpty(lendingApplication.getLongitude())) {
                latLong.put("lat", lendingApplication.getLatitude());
                latLong.put("long", lendingApplication.getLongitude());
                return latLong;
            }
            List<LendingShopDocuments> lendingShopDocumentsList = lendingShopDocumentsDao.findByMerchantIdAndLendingApplicationId(lendingApplication.getMerchantId(), lendingApplication.getId());
            for (LendingShopDocuments lendingShopDocuments : lendingShopDocumentsList) {
                if (!ObjectUtils.isEmpty(lendingShopDocuments) && !ObjectUtils.isEmpty(lendingShopDocuments.getLatitude()) && !ObjectUtils.isEmpty(lendingShopDocuments.getLongitude())) {
                    latLong.put("lat", lendingShopDocuments.getLatitude());
                    latLong.put("long", lendingShopDocuments.getLongitude());
                    return latLong;
                }
            }
            Map<String, Double> dsResponse = dsHandler.fetchDsLocation(lendingApplication.getMerchantId());
            if (!ObjectUtils.isEmpty(dsResponse) || dsResponse.containsKey("latitude") || !ObjectUtils.isEmpty(dsResponse.get("latitude")) || dsResponse.containsKey("longitude") || !ObjectUtils.isEmpty(dsResponse.get("longitude"))) {
                latLong.put("lat", String.valueOf(dsResponse.get("latitude")));
                latLong.put("long", String.valueOf(dsResponse.get("longitude")));
                return latLong;
            }

        } catch (Exception e) {
            log.error("error while getting latitude and longitude for application Id {} and merchantId {}, {}, {}", lendingApplication.getId(), lendingApplication.getMerchantId(), e, Arrays.asList(e.getStackTrace()));
        }

        log.info("couldn't get lat long for applicationId {} and merchantId {} from shopDocs and DS API", lendingApplication.getId(), lendingApplication.getMerchantId());
        return null;
    }

    public Map<String, String> getBusinessCategoryAndSubCategory(Long merchantId) {
        Map<String, String> categories = new HashMap<>();
        categories.put("businessCategory", null);
        categories.put("businessSubcategory", null);
        try {
            List<Long> prevLoansIds = lendingPaymentScheduleDaoSlave.findAllLatestClosedLoans(merchantId, "CLOSED");
            if (!ObjectUtils.isEmpty(prevLoansIds)) {
                LmsFieldValues businessCategoryField = lmsFieldValuesDao.findCategoryByApplicationId(prevLoansIds, BUSINESS_CATEGORY_LMS_FIELD_ID);
                if (!ObjectUtils.isEmpty(businessCategoryField)) {
                    categories.put("businessCategory", businessCategoryField.getFieldDropdownValue());
                    LmsFieldValues subBusinessCategoryField = lmsFieldValuesDao.findByFieldIdAndLendingApplicationId(BUSINESS_SUBCATEGORY_LMS_FIELD_ID, businessCategoryField.getLendingApplicationId());
                    categories.put("businessSubcategory", ObjectUtils.isEmpty(subBusinessCategoryField) ? "OTHERS" : subBusinessCategoryField.getFieldDropdownValue());
                }
            }
        } catch (Exception e) {
            log.info("Exception in fetching business category for merchantId {} {}", merchantId, Arrays.asList(e.getStackTrace()));
        }
        return categories;
    }

    public PriorityQueue<BusinessDocsDTO> getBusinessDocData(Long merchantId, String lender, String docs) {
        PriorityQueue<BusinessDocsDTO> businessDocs = new PriorityQueue<>(Comparator.comparingInt(BusinessDocsDTO::getPriority));
        Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchantId);
        if (!basicDetailsDto.isPresent()) {
            return businessDocs;
        }
        List<KycDoc> kycDocs = kycHandler.getKycDoc(merchantId, false, false, "BUSINESSDOCS");
        for (KycDoc doc : kycDocs) {
            if (ObjectUtils.isEmpty(docs) || docs.contains(doc.getSubDocType().name())) {
                String url = !ObjectUtils.isEmpty(doc.getDocPdfUrl()) ? doc.getDocPdfUrl() : doc.getDocFrontImageUrl();
                businessDocs.add(new BusinessDocsDTO(doc.getSubDocType(), url, BusinessDocsDTO.getDocPriorityForLender(doc.getSubDocType(), lender), doc.getDocIdentifier()));
            }
        }
        return businessDocs;
    }

    private Double getParsedFaceMatchPer(String faceMatchPer) {
        if (!ObjectUtils.isEmpty(faceMatchPer)) {
            try {
                return Double.parseDouble(faceMatchPer.replace("%", "").trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid faceMatchPer value: " + faceMatchPer, e);
            }
        }
        return null;
    }

    public CKycResponseDto getGstData(Long merchantId) {
        CKycResponseDto cKycResponseDto = new CKycResponseDto();

        List<KycDoc> kycDocs = kycHandler.getKycDoc(merchantId, false, true, "GST_NO");
        if (ObjectUtils.isEmpty(kycDocs)){
            return cKycResponseDto;
        }

        for (KycDoc doc : kycDocs) {
            try {
                if ("GST_NO".equalsIgnoreCase(doc.getDocType().name())) {
                    cKycResponseDto.setGstNumber(doc.getDocIdentifier());
                    cKycResponseDto.setName(doc.getName());
                    cKycResponseDto.setTradeName(ObjectUtils.isEmpty(doc.getTradeName()) ? null : doc.getTradeName());
                }
            } catch (Exception e) {
                log.error("error in processing kyc doc {} {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
            }
        }
        log.info("gst no ckyc response {} for merchant id {}", cKycResponseDto, merchantId);
        return cKycResponseDto;
    }

    public Map<String, String> getBusinessCategoryAndSubCategoryByApplicationId(Long applicationId) {
        Map<String, String> categories = new HashMap<>();
        categories.put("businessCategory", null);
        categories.put("businessSubcategory", null);
        try {
            LmsFieldValues businessCategoryField = lmsFieldValuesDao.findByFieldIdAndLendingApplicationId(BUSINESS_CATEGORY_LMS_FIELD_ID, applicationId);
            if (!ObjectUtils.isEmpty(businessCategoryField)) {
                categories.put("businessCategory", businessCategoryField.getFieldDropdownValue());
                LmsFieldValues subBusinessCategoryField = lmsFieldValuesDao.findByFieldIdAndLendingApplicationId(BUSINESS_SUBCATEGORY_LMS_FIELD_ID, applicationId);
                categories.put("businessSubcategory", ObjectUtils.isEmpty(subBusinessCategoryField) ? "OTHERS" : subBusinessCategoryField.getFieldDropdownValue());
            }
        } catch (Exception e) {
            log.info("Exception in fetching business category for applicationId {} {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
        return categories;
    }

    public String getCareOfName(String careOf, String prefix) {
        try {
            if (ObjectUtils.isEmpty(careOf) || !careOf.contains(prefix)){
                return null;
            }
            careOf = careOf.toUpperCase();
            careOf = careOf.replaceAll(prefix, "").replaceAll("\\.", "").replaceAll(":", "").replaceFirst(" ", "");
            return careOf.substring(0, careOf.indexOf(","));
        } catch (Exception e) {
            log.info("Exception in fetching careOf name from kyc address {}", Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public Boolean isEligibleForSkipKyc(Long applicationId, Lender lender, Long merchantId, boolean isTopup) {
        if(skipKycEnabledLenders.contains(lender.name())) {
            if(bharatPeKycLenderAlreadyAssigned(applicationId)) {
                log.info("Skip Kyc not eligible, as BP kyc lender already assigned for application {}", applicationId);
                return false;
            }
            if(skipKycCaseAlreadyFailedForLender(applicationId, lender.name())) {
                log.info("Skip Kyc not eligible, as validation already failed with skip kyc on {} for application {}", lender, applicationId);
                return false;
            }
            switch (lender) {
                case TRILLIONLOANS:
                    return easyLoanUtil.percentScaleUp(merchantId, isTopup ? trillionLoansConfig.getTopUpSkipKycRolloutPercent() : trillionLoansConfig.getSkipKycRolloutPercent());
                default:
                    return false;
            }
        }
        return false;
    }

    public Boolean isEligibleForSkipKycOrLenderKyc(LendingApplication application) {
        if(isEligibleForLenderKyc(application.getLender(), application.getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(application.getLoanType()))) {
            return true;
        }
        LendingApplicationKycDetails kycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(application.getId());
        return !(ObjectUtils.isEmpty(kycDetails)) && LenderAssociationStages.SKIP_KYC.name().equalsIgnoreCase(kycDetails.getKycMode());
    }

    public Boolean bharatPeKycLenderAlreadyAssigned(Long applicationId) {
        LendingApplicationKycDetails kycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndKycModeOrderByIdDesc(applicationId, KycMode.BP_KYC.name());
        if(!ObjectUtils.isEmpty(kycDetails)) {
            log.info("BP kyc lender already assigned for application {}", applicationId);
            return true;
        }
        return false;
    }

    public Boolean skipKycCaseAlreadyFailedForLender(Long applicationId, String lender) {
        LendingApplicationLenderDetails prevLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, Status.INACTIVE.name(), lender);
        if(!ObjectUtils.isEmpty(prevLenderDetails) && LenderAssociationStages.SKIP_KYC.name().equalsIgnoreCase(prevLenderDetails.getKycMode())) {
            log.info("Skip kyc case already failed with lender {} for application {}", lender, applicationId);
            return true;
        }
        return false;
    }

}
