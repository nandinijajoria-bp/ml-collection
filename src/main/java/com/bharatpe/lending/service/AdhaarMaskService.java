package com.bharatpe.lending.service;

import com.amazonaws.util.IOUtils;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.bpnewmaster.dao.DocumentsIdProofDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocumentsIdProofMaster;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.AdhaarMaskRequest;
import com.bharatpe.lending.dto.CommonResponse;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AdhaarMaskService {

    private final Logger logger = LoggerFactory.getLogger(AdhaarMaskService.class);

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    DocumentsIdProofDaoMaster documentsIdProofDaoMaster;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Value("${aws.s3.bucket}")
    String imageBucket;

//    public CommonResponse maskAdhaar(AdhaarMaskRequest requestDTO) {
//        try {
//            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(requestDTO.getApplicationId(), requestDTO.getMerchantId());
//            if (lendingApplication == null) {
//                return new CommonResponse(false, "Application not found");
//            }
//            DocumentsIdProofMaster documentsIdProof = documentsIdProofDaoMaster.findByMerchantIdApplicationIdAndProofType(requestDTO.getMerchantId(), lendingApplication.getId(), "adhaarcard");
//            if (documentsIdProof != null && documentsIdProof.getProofFrontSide() != null) {
//                logger.info("Masking adhaar for application:{}", lendingApplication.getId());
//                String frontUrl = getImageUrl(documentsIdProof.getProofFrontSide());
//                String backUrl = getImageUrl(documentsIdProof.getProofBackSide());
//                JsonNode frontImageResponse = getAdhaarMaskResponse(requestDTO.getMerchantId(), frontUrl);
//                JsonNode backImageResponse = getAdhaarMaskResponse(requestDTO.getMerchantId(), backUrl);
//                if (frontImageResponse != null) {
//                    updateAdhaarImage(frontImageResponse, documentsIdProof, requestDTO.getMerchantId(), false);
//                }
//                if (backImageResponse != null) {
//                    updateAdhaarImage(backImageResponse, documentsIdProof, requestDTO.getMerchantId(), true);
//                }
//                return new CommonResponse(true, "success");
//            } else {
//                return new CommonResponse(false, "Adhaarcard not found for this application");
//            }
//        } catch (Exception e) {
//            logger.error("Exception while masking adhaar", e);
//            return new CommonResponse(false, "Something went wrong");
//        }
//    }

//    private JsonNode getAdhaarMaskResponse(Long merchantId, String url) throws IOException {
//        Map<String, String> identityDetail = apiGatewayService.signzyIdentityDetails("aadhaar", merchantId, "KYC", "AADHAR_MASK", new ArrayList<String>() {{
//            add(url);
//        }});
//        if (identityDetail == null) {
//            logger.info("Failed to get signzy identity details for merchant:{}", merchantId);
//            return null;
//        }
//        String adhaarMaskResponse = apiGatewayService.signzySnoop(identityDetail.get("itemId"), identityDetail.get("accessToken"), "aadhaarMasker", merchantId, identityDetail.get("module"),
//                new HashMap<String, String>(){{
//                    put("url", url);
//                }});
//        if (adhaarMaskResponse == null) {
//            logger.info("Failed to mask adhaar image for merchant:{}", merchantId);
//            return null;
//        }
//        return objectMapper.readTree(adhaarMaskResponse);
//    }

    private void updateAdhaarImage(JsonNode result, DocumentsIdProofMaster documentsIdProof, Long merchantId, boolean isBackImage) {
        if (result.hasNonNull("response") && result.get("response").hasNonNull("result") && result.get("response").get("result").hasNonNull("maskedImages") && result.get("response").get("result").get("isMasked") != null && result.get("response").get("result").get("isMasked").textValue().equalsIgnoreCase("yes")) {
            logger.info("Updating masked adhaar image for application:{}", documentsIdProof.getLendingApplicationId());
            try {
                String url = result.get("response").get("result").get("maskedImages").get(0).textValue();
                String fileName = merchantId + "_" + UUID.randomUUID().toString() + ".jpeg";
                File file = new File("/tmp/" + fileName);
                FileUtils.copyURLToFile(new URL(url), file);
                s3BucketHandler.uploadFileToS3(file, imageBucket, fileName);
                if (isBackImage) {
                    documentsIdProof.setProofBackSide(fileName);
                } else {
                    documentsIdProof.setProofFrontSide(fileName);
                }
                documentsIdProofDaoMaster.save(documentsIdProof);
            } catch (Exception e) {
                logger.error("Exception while updating adhaar image", e);
            }
        }
    }

    private String getImageUrl(String imagePath) {
        try {
            logger.info("fetching image object stream from bucket {} for image {}", imageBucket, imagePath);
            InputStream imageUrlStream = s3BucketHandler.getObject(imagePath, imageBucket);
            String base64File = getBase64(imageUrlStream);
            if(base64File!=null) {
                return apiGatewayService.getTemporarySignzyURL(base64File);
            }
        } catch (Exception e) {
            logger.error("Exception while fetching image url", e);
        }
        return null;
    }

    public String getBase64(InputStream stream) {
        try {
            byte[] imageBytes;
            imageBytes = IOUtils.toByteArray(stream);
            stream.close();
            return Base64.encodeBase64String(imageBytes);
        } catch (IOException e) {
            logger.error("Error occured while getting base64 string from s3 object",e);
        }
        return null;
    }
}
