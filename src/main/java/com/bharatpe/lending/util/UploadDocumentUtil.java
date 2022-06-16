package com.bharatpe.lending.util;

import com.amazonaws.util.IOUtils;
import com.bharatpe.common.dao.DocKycDetailsDao;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.entities.DocKycDetails;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.slave.dao.SignzyCredentialDaoSlave;
import com.bharatpe.lending.common.slave.entity.SignzyCredentialSlave;
import com.bharatpe.lending.controller.LendingApplicationController;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.service.APIGatewayService;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class UploadDocumentUtil {

    Logger logger = LoggerFactory.getLogger(LendingApplicationController.class);

    @Value("${aws.s3.bucket}")
    private String lendingBucket;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DocKycDetailsDao docKycDetailsDao;

    @Autowired
    SignzyCredentialDaoSlave signzyCredentialDaoSlave;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    MerchantService merchantService;

    @Autowired
    DocumentsIdProofDao documentsIdProofDao;

    @Autowired
    APIGatewayService apiGatewayService;

    public Map<String,String> getDetailsOfSignzyApi(){
        SignzyCredentialSlave signzyCredential = signzyCredentialDaoSlave.findByModule("LENDING");
        if (signzyCredential == null) {
            logger.info("Signzy credentials not found for Lending");
            return null;
        }
        Map<String, String> cred = new HashMap<>();
        cred.put("accessToken", signzyCredential.getAccessId());
        cred.put("patronId", signzyCredential.getUserId());
        return cred;
    }

    public String getUrl(String imageName) {
        try {
            if(imageName!=null && !imageName.isEmpty()) {
                String bucket=lendingBucket;
                logger.info("fetching image object stream from bucket {} for image {}",bucket,imageName);
                InputStream imageUrlStream= s3BucketHandler.getObject(imageName, bucket);
                String base64File=getBase64(imageUrlStream);
                if(base64File!=null) {
                    return getTemporaryPublicURL(base64File);
                }
            }
        } catch (Exception e) {
            logger.error("Error occured while fetching image url",e);
        }
        return null;
    }


    public String getBase64(InputStream stream) {
        try {
            logger.info("converting input stream to base 64");
            byte[] imageBytes;
            imageBytes = IOUtils.toByteArray(stream);
            stream.close();
            return Base64.encodeBase64String(imageBytes);
        } catch (IOException e) {
            logger.error("Error occured while getting base64 string from s3 object",e);
        }
        return null;
    }


    public String getTemporaryPublicURL(String base64File) throws JsonParseException, JsonMappingException, IOException {
        String response= null;
        Map<String, Object> body = new HashMap<>();
        body.put( "base64String",base64File);

        body.put("mimetype","image/jpeg");
        body.put("ttl","7 days");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);


        Instant start = Instant.now();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
//	    logger.info("signzy image URL request : {}", request);
        try {
            response = restTemplate.postForObject("https://persist.signzy.tech/api/base64", request, String.class);
            logger.info("signzy KYC response : {}", response);
            Instant end = Instant.now();
            logger.info("Time Taken by signzy KYC API : {} miliseconds", Duration.between(start, end).toMillis());
        }
        catch(Exception e) {
            logger.info("exception while signzy KYC API, file exchange : {}, Exception is {}", base64File, e);
        }
        if(Objects.nonNull(response)){
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> responseMap = mapper.readValue(response, new TypeReference<Map<String, Object>>(){});
        Map<String,Object>res= ( Map<String,Object>)responseMap.get("file");
        String url=(String)res.get("directURL");
        return url;
        }

        return null;
    }

    public DocKycDetails insertPanOcrDetailsInDocKycDetails(String response, DocumentsIdProof documentsIdProof, String proofType) {
        try {
            logger.info("Storing pancard ocr detail {}",response);
            JsonNode responseNode=objectMapper.readTree(response);
            if(responseNode!=null && responseNode.has("response") && !responseNode.get("response").isNull() && responseNode.get("response").has("result")) {
                JsonNode resultNode=responseNode.get("response").get("result");
                if(resultNode!=null) {
                    JsonNode summaryNode=resultNode.get("summary");
                    if(summaryNode!=null) {
                        DocKycDetails docKycDetails=new DocKycDetails();
                        docKycDetails.setMerchantId(documentsIdProof.getMerchantId());
                        docKycDetails.setDocType(proofType);
                        docKycDetails.setDob((resultNode.has("dob") && !resultNode.get("dob").isNull())? StringUtils.substring(resultNode.get("dob").asText(), 0, 12):null);
                        docKycDetails.setDocumentsIdProof(documentsIdProof);
                        docKycDetails.setGender((summaryNode.has("gender") && !summaryNode.get("gender").isNull())?summaryNode.get("gender").asText():null);
                        docKycDetails.setStatus("SUCCESS");
                        docKycDetails.setPersonName((resultNode.has("name") && !resultNode.get("name").isNull())?StringUtils.substring(resultNode.get("name").asText(), 0, 30):null);
//						docKycDetails.setResponse(response);
                        docKycDetails.setModule("LENDING");
                        docKycDetails.setDocSide("FRONT");
                        docKycDetails.setDocNo((summaryNode.has("number") && !summaryNode.get("number").isNull())?summaryNode.get("number").asText():null);
                        docKycDetails.setFatherName((summaryNode.has("guardianName") && !summaryNode.get("guardianName").isNull())? StringUtils.substring(summaryNode.get("guardianName").asText(), 0, 30):null);
                        docKycDetailsDao.save(docKycDetails);
                        return docKycDetails;
                    }
                }
            }
        }
        catch(Exception e) {
            logger.error("Error occured while inserting data into DocKycDetails table",e);
        }
        return null;
    }

    public DocKycDetails processOcrResponse(String response, DocumentsIdProof documentsIdProof) {
        if(response!=null) {
            logger.info("Inserting proof details for proof type {}",documentsIdProof.getProofType());
            DocKycDetails docKycDetails=insertPanOcrDetailsInDocKycDetails(response, documentsIdProof, "pancard");
            return docKycDetails;
        }
        return null;
    }

    public DocKycDetails doOcrForPan(DocumentsIdProof documentsIdProof, Map<String,String> signzyApiDetails, String proofType, Long merchantId, Long applicationId) {
        DocKycDetails docKyc=null;
        try {
            if(documentsIdProof.getProofType().equalsIgnoreCase("pancard") || documentsIdProof.getProofType().equalsIgnoreCase("votercard")  || documentsIdProof.getProofType().equalsIgnoreCase("passport")  || documentsIdProof.getProofType().equalsIgnoreCase("adhaarcard") || documentsIdProof.getProofType().equalsIgnoreCase("driving_license")) {
                String frontImageUrl=getUrl(documentsIdProof.getProofFrontSide());
                String backImageUrl=null;
                if(frontImageUrl!=null) {
                    List<String> images;
                    if(documentsIdProof.getProofBackSide()!=null && !documentsIdProof.getProofBackSide().isEmpty()) {
                        backImageUrl=getUrl(documentsIdProof.getProofBackSide());
                    }
                    if(backImageUrl!=null) {
                        images =  Arrays.asList(frontImageUrl,backImageUrl);
                    }
                    else {
                        images =  Collections.singletonList(frontImageUrl);
                    }
                    Map<String,String> identityDetails = apiGatewayService.signzyIdentityDetails("individualPan", merchantId, "LENDING", "LENDING", images);
//
                    String response=apiGatewayService.getOcrResponse(documentsIdProof.getMerchantId(), identityDetails,"OCR",applicationId);
                    docKyc = processOcrResponse(response, documentsIdProof);
                    if(Objects.nonNull(docKyc) && Objects.nonNull(docKyc.getPersonName())){
                        String authorization=signzyApiDetails.get("accessToken");
                        String patronId=signzyApiDetails.get("patronId");

                        final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
                        BankDetailsDto merchantBankDetail = null;
                        if (bankDetailsDtoOptional.isPresent())
                            merchantBankDetail = bankDetailsDtoOptional.get();
                        String benificiaryName =  merchantBankDetail!= null ? (merchantBankDetail.getBeneficiaryName()!= null ? merchantBankDetail.getBeneficiaryName() : "") : "";

                        Double getMatchPercentage = apiGatewayService.getNameMatchPercentage(authorization, patronId, benificiaryName, docKyc.getPersonName(), merchantId, applicationId);

                        if(getMatchPercentage >= 0.5D){
                            documentsIdProof.setPanNameMatch("YES");
                        }else{
                            documentsIdProof.setPanNameMatch("NO");

                        }
                        documentsIdProof.setPanNamePercentage(getMatchPercentage.toString());
                        documentsIdProofDao.save(documentsIdProof);
                    }
                }
            }
        }
        catch(Exception e) {
            logger.error("Error occured while doing ocr",e);
        }
        return docKyc;
    }
}
