package com.bharatpe.lending.service;

import com.amazonaws.util.IOUtils;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.entities.LendingApplication;
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

@Service
public class AdhaarMaskService {

    private final Logger logger = LoggerFactory.getLogger(AdhaarMaskService.class);

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    DocumentsIdProofDao documentsIdProofDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Value("${aws.s3.bucket}")
    String imageBucket;

    public CommonResponse maskAdhaar(AdhaarMaskRequest requestDTO) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(requestDTO.getApplicationId(), requestDTO.getMerchantId());
            if (lendingApplication == null) {
                return new CommonResponse(false, "Application not found");
            }
            DocumentsIdProof documentsIdProof = documentsIdProofDao.findByMerchantIdApplicationIdAndProofType(requestDTO.getMerchantId(), lendingApplication.getId(), "adhaarcard");
            if (documentsIdProof != null && documentsIdProof.getProofFrontSide() != null) {
                logger.info("Masking adhaar for application:{}", lendingApplication.getId());
                String url = getImageUrl(documentsIdProof.getProofFrontSide());
                Map<String, String> identityDetail = apiGatewayService.signzyIdentityDetails("aadhaar", requestDTO.getMerchantId(), "KYC", "AADHAR_MASK", new ArrayList<String>() {{
                    add(url);
                }});
                if (identityDetail == null) {
                    return new CommonResponse(false, "Failed to get signzy identity details");
                }
                String adhaarMaskResponse = apiGatewayService.signzySnoop(identityDetail.get("itemId"), identityDetail.get("accessToken"), "aadhaarMasker", requestDTO.getMerchantId(), identityDetail.get("module"),
                        new HashMap<String, String>(){{
                            put("url", url);
                        }});
                if (adhaarMaskResponse != null) {
                    JsonNode result = objectMapper.readTree(adhaarMaskResponse);
                    if (updateAdhaarImage(result, documentsIdProof, requestDTO.getMerchantId())) {
                        return new CommonResponse(true, "success");
                    }
                }
            } else {
                return new CommonResponse(false, "Adhaarcard not found for this application");
            }
        } catch (Exception e) {
            logger.error("Exception while masking adhaar", e);
            return new CommonResponse(false, "Something went wrong");
        }
        return new CommonResponse(false, "Invalid image");
    }

    private boolean updateAdhaarImage(JsonNode result, DocumentsIdProof documentsIdProof, Long merchantId) {
        boolean success = false;
        if (result.hasNonNull("response") && result.get("response").hasNonNull("result") && result.get("response").get("result").hasNonNull("maskedImages") && result.get("response").get("result").get("isMasked") != null && result.get("response").get("result").get("isMasked").textValue().equalsIgnoreCase("yes")) {
            logger.info("Updating masked adhaar image for application:{}", documentsIdProof.getLendingApplication().getId());
            try {
                String url = result.get("response").get("result").get("maskedImages").get(0).textValue();
                String fileName = merchantId + "" + ((int) (Math.random() * ((100000 - 1) + 1)) + 1) + ".jpeg";
                File file = new File("/tmp/" + fileName);
                FileUtils.copyURLToFile(new URL(url), file);
                s3BucketHandler.uploadFileToS3(file, imageBucket, fileName);
                documentsIdProof.setProofFrontSide(fileName);
                documentsIdProofDao.save(documentsIdProof);
                success = true;
            } catch (Exception e) {
                logger.error("Exception while updating adhaar image", e);
            }
        }
        return success;
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
