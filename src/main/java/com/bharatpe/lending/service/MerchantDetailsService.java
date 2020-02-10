package com.bharatpe.lending.service;

import com.bharatpe.common.dao.CpvDocumentsIdProofDao;
import com.bharatpe.common.dao.LendingCpvDetailsDao;
import com.bharatpe.common.entities.CpvDocumentsIdProof;
import com.bharatpe.common.entities.LendingCpvDetails;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.dto.ImageDTO;
import com.bharatpe.lending.dto.MerchantDetailsDTO;
import com.bharatpe.lending.dto.UploadImageDTO;
import com.bharatpe.lending.handlers.S3BucketHandler;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.util.*;

@Service
public class MerchantDetailsService {

    Logger logger = LoggerFactory.getLogger(MerchantDetailsService.class);

    @Autowired
    LendingCpvDetailsDao lendingCpvDetailsDao;

    @Autowired
    CpvDocumentsIdProofDao cpvDocumentsIdProofDao;

    @Autowired
    UploadDocumentService uploadDocumentService;

    @Autowired
    S3BucketHandler s3BucketHandler;

    private static final String S3_BUCKET = "loan-document";

    Map<String, Integer> imageSize = new HashMap<String, Integer>() {{
        put("SHOP_FRONT", 1);
        put("QR_CODE", 1);
        put("STOCK", 1);
        put("CHEQUE", 1);
        put("NACH", 1);
        put("OWNERSHIP", 5);
        put("RELATIONSHIP", 5);
        put("ADDRESS", 2);
        put("IDENTITY", 2);
        put("OTHER", 10);
    }};

    public MerchantDetailsDTO updatePersonalDetails(MerchantDetailsDTO merchantDetailsDTO, Merchant merchant, String lat, String lng) {
        String module = "MERCHANT_APP";
        LendingCpvDetails lendingCpvDetails = lendingCpvDetailsDao.findByApplicationIdAndModule(merchantDetailsDTO.getApplicationId(), module);
        if (lendingCpvDetails == null) {
            lendingCpvDetails = new LendingCpvDetails();
        }
        if (lat != null && lng != null && !lat.trim().equalsIgnoreCase("") && !lng.trim().equalsIgnoreCase("")) {
            lendingCpvDetails.setLatitude(Double.parseDouble(lat));
            lendingCpvDetails.setLongitude(Double.parseDouble(lng));
        }
        lendingCpvDetails.setMerchantId(merchant.getId());
        lendingCpvDetails.setApplicationId(merchantDetailsDTO.getApplicationId());
        lendingCpvDetails.setMobile(merchantDetailsDTO.getPersonalDetails().getAlternateMobile() != null ? Long.parseLong(merchantDetailsDTO.getPersonalDetails().getAlternateMobile()) : null);
        lendingCpvDetails.setEmail(merchantDetailsDTO.getPersonalDetails().getEmail());
        lendingCpvDetails.setMaritalStatus(merchantDetailsDTO.getPersonalDetails().getMaritalStatus());
        lendingCpvDetails.setAddress(merchantDetailsDTO.getPersonalDetails().getResidentialAddress());
        lendingCpvDetails.setPin(merchantDetailsDTO.getPersonalDetails().getPincode() != null ? Integer.parseInt(merchantDetailsDTO.getPersonalDetails().getPincode()) : null);
        lendingCpvDetails.setState(merchantDetailsDTO.getPersonalDetails().getState());
        lendingCpvDetails.setCity(merchantDetailsDTO.getPersonalDetails().getCity());
        lendingCpvDetails.setLandmark(merchantDetailsDTO.getPersonalDetails().getLandmark());
        lendingCpvDetails.setModule(module);
        lendingCpvDetailsDao.save(lendingCpvDetails);
        return merchantDetailsDTO;
    }

    public MerchantDetailsDTO updateShopDetails(MerchantDetailsDTO merchantDetailsDTO, Merchant merchant, String lat, String lng) {
        String module = "MERCHANT_APP";
        LendingCpvDetails lendingCpvDetails = lendingCpvDetailsDao.findByApplicationIdAndModule(merchantDetailsDTO.getApplicationId(), module);
        if (lendingCpvDetails == null) {
            lendingCpvDetails = new LendingCpvDetails();
        }
        if (lat != null && lng != null && !lat.trim().equalsIgnoreCase("") && !lng.trim().equalsIgnoreCase("")) {
            lendingCpvDetails.setLatitude(Double.parseDouble(lat));
            lendingCpvDetails.setLongitude(Double.parseDouble(lng));
        }
        lendingCpvDetails.setMerchantId(merchant.getId());
        lendingCpvDetails.setApplicationId(merchantDetailsDTO.getApplicationId());
        lendingCpvDetails.setModule(module);
        lendingCpvDetails.setBusinessType(merchantDetailsDTO.getShopDetails().getBusinessType());
        lendingCpvDetails.setOwnership(merchantDetailsDTO.getShopDetails().getShopOwnership());
        lendingCpvDetails.setShopName(merchantDetailsDTO.getShopDetails().getShopName());
        lendingCpvDetailsDao.save(lendingCpvDetails);
        getShopDetailsImages(merchantDetailsDTO, lendingCpvDetails, module);
        return merchantDetailsDTO;
    }

    private void getShopDetailsImages(MerchantDetailsDTO merchantDetailsDTO, LendingCpvDetails lendingCpvDetails, String module) {
        Map<String, List<CpvDocumentsIdProof>> cpvDocumentTypeMap = new HashMap<>();
        List<CpvDocumentsIdProof> cpvDocumentsIdProofList = cpvDocumentsIdProofDao.getByProofTypesCpvIdMerchantModule(Arrays.asList("SHOP_FRONT", "QR_CODE", "STOCK"), lendingCpvDetails.getId(), lendingCpvDetails.getMerchantId(), module);
        for (CpvDocumentsIdProof cpvDocumentsIdProof : cpvDocumentsIdProofList) {
            cpvDocumentTypeMap.putIfAbsent(cpvDocumentsIdProof.getProofType(), new ArrayList<>());
            cpvDocumentTypeMap.get(cpvDocumentsIdProof.getProofType()).add(cpvDocumentsIdProof);
        }
        merchantDetailsDTO.getShopDetails().setShopImage(new ArrayList<>());
        merchantDetailsDTO.getShopDetails().setBharatpeQrImage(new ArrayList<>());
        merchantDetailsDTO.getShopDetails().setStockImage(new ArrayList<>());
        String url;
        try {
            if (cpvDocumentTypeMap.containsKey("SHOP_FRONT")) {
                for (CpvDocumentsIdProof cpvDocumentsIdProof : cpvDocumentTypeMap.get("SHOP_FRONT")) {
                    url = s3BucketHandler.getTemporaryPublicURL(cpvDocumentsIdProof.getProofFrontSide(), S3_BUCKET);
                    merchantDetailsDTO.getShopDetails().getShopImage().add(new ImageDTO(cpvDocumentsIdProof.getId(), url));
                }
            }
            if (cpvDocumentTypeMap.containsKey("QR_CODE")) {
                for (CpvDocumentsIdProof cpvDocumentsIdProof : cpvDocumentTypeMap.get("QR_CODE")) {
                    url = s3BucketHandler.getTemporaryPublicURL(cpvDocumentsIdProof.getProofFrontSide(), S3_BUCKET);
                    merchantDetailsDTO.getShopDetails().getBharatpeQrImage().add(new ImageDTO(cpvDocumentsIdProof.getId(), url));
                }
            }
            if (cpvDocumentTypeMap.containsKey("STOCK")) {
                for (CpvDocumentsIdProof cpvDocumentsIdProof : cpvDocumentTypeMap.get("STOCK")) {
                    url = s3BucketHandler.getTemporaryPublicURL(cpvDocumentsIdProof.getProofFrontSide(), S3_BUCKET);
                    merchantDetailsDTO.getShopDetails().getStockImage().add(new ImageDTO(cpvDocumentsIdProof.getId(), url));
                }
            }
        } catch (FileNotFoundException e) {
            logger.error("Image not found for merchant: {}, cpvId: {}", lendingCpvDetails.getMerchantId(), lendingCpvDetails.getId());
        }
    }

    public MerchantDetailsDTO updateBusinessDetails(MerchantDetailsDTO merchantDetailsDTO, Merchant merchant, String lat, String lng) {
        String module = "MERCHANT_APP";
        LendingCpvDetails lendingCpvDetails = lendingCpvDetailsDao.findByApplicationIdAndModule(merchantDetailsDTO.getApplicationId(), module);
        if (lendingCpvDetails == null) {
            lendingCpvDetails = new LendingCpvDetails();
        }
        if (lat != null && lng != null && !lat.trim().equalsIgnoreCase("") && !lng.trim().equalsIgnoreCase("")) {
            lendingCpvDetails.setLatitude(Double.parseDouble(lat));
            lendingCpvDetails.setLongitude(Double.parseDouble(lng));
        }
        lendingCpvDetails.setMerchantId(merchant.getId());
        lendingCpvDetails.setApplicationId(merchantDetailsDTO.getApplicationId());
        lendingCpvDetails.setModule(module);
        lendingCpvDetails.setBusinessStartedAt(merchantDetailsDTO.getBusinessDetails().getBusinessStartDate());
        lendingCpvDetails.setDailySales(merchantDetailsDTO.getBusinessDetails().getDailySales());
        lendingCpvDetailsDao.save(lendingCpvDetails);
        getBusinessDetailsImages(merchantDetailsDTO, lendingCpvDetails, module);
        return merchantDetailsDTO;
    }

    private void getBusinessDetailsImages(MerchantDetailsDTO merchantDetailsDTO, LendingCpvDetails lendingCpvDetails, String module) {
        Map<String, List<CpvDocumentsIdProof>> cpvDocumentTypeMap = new HashMap<>();
        List<CpvDocumentsIdProof> cpvDocumentsIdProofList = cpvDocumentsIdProofDao.getByProofTypesCpvIdMerchantModule(Arrays.asList("RELATIONSHIP", "OWNERSHIP", "CHEQUE"), lendingCpvDetails.getId(), lendingCpvDetails.getMerchantId(), module);
        for (CpvDocumentsIdProof cpvDocumentsIdProof : cpvDocumentsIdProofList) {
            cpvDocumentTypeMap.putIfAbsent(cpvDocumentsIdProof.getProofType(), new ArrayList<>());
            cpvDocumentTypeMap.get(cpvDocumentsIdProof.getProofType()).add(cpvDocumentsIdProof);
        }
        merchantDetailsDTO.getBusinessDetails().setRelationshipImage(new ArrayList<>());
        merchantDetailsDTO.getBusinessDetails().setOwnershipImage(new ArrayList<>());
        merchantDetailsDTO.getBusinessDetails().setRepaymentDocImage(new ArrayList<>());
        String url;
        try {
            if (cpvDocumentTypeMap.containsKey("RELATIONSHIP")) {
                for (CpvDocumentsIdProof cpvDocumentsIdProof : cpvDocumentTypeMap.get("RELATIONSHIP")) {
                    url = s3BucketHandler.getTemporaryPublicURL(cpvDocumentsIdProof.getProofFrontSide(), S3_BUCKET);
                    merchantDetailsDTO.getBusinessDetails().getRelationshipImage().add(new ImageDTO(cpvDocumentsIdProof.getId(), url));
                }
            }
            if (cpvDocumentTypeMap.containsKey("OWNERSHIP")) {
                for (CpvDocumentsIdProof cpvDocumentsIdProof : cpvDocumentTypeMap.get("OWNERSHIP")) {
                    url = s3BucketHandler.getTemporaryPublicURL(cpvDocumentsIdProof.getProofFrontSide(), S3_BUCKET);
                    merchantDetailsDTO.getBusinessDetails().getOwnershipImage().add(new ImageDTO(cpvDocumentsIdProof.getId(), url));
                }
            }
            if (cpvDocumentTypeMap.containsKey("CHEQUE")) {
                for (CpvDocumentsIdProof cpvDocumentsIdProof : cpvDocumentTypeMap.get("CHEQUE")) {
                    url = s3BucketHandler.getTemporaryPublicURL(cpvDocumentsIdProof.getProofFrontSide(), S3_BUCKET);
                    merchantDetailsDTO.getBusinessDetails().getRepaymentDocImage().add(new ImageDTO(cpvDocumentsIdProof.getId(), url));
                }
            }
        } catch (FileNotFoundException e) {
            logger.error("Image not found for merchant: {}, cpvId: {}", lendingCpvDetails.getMerchantId(), lendingCpvDetails.getId());
        }
    }

    public UploadImageDTO uploadImage(UploadImageDTO uploadImageDTO, Merchant merchant, String lat, String lng) {
        String module = "MERCHANT_APP";
        LendingCpvDetails lendingCpvDetails = lendingCpvDetailsDao.findByApplicationIdAndModule(uploadImageDTO.getApplicationId(), module);
        if (lendingCpvDetails == null) {
            logger.info("lending cpv details not found for merchant: {}, for application: {}", merchant.getId(), uploadImageDTO.getApplicationId());
            return null;
        }
        List<CpvDocumentsIdProof> cpvDocumentsIdProofList = cpvDocumentsIdProofDao.findByProofTypeAndCpvIdAndModule(uploadImageDTO.getImageData().getImageName(), lendingCpvDetails.getId(), module);
        int count = 0;
        for (CpvDocumentsIdProof cpvDocumentsIdProof : cpvDocumentsIdProofList) {
            if (cpvDocumentsIdProof.getDeletedAt() == null) {
                count++;
            }
        }
        if (uploadImageDTO.getImageData().getImageId() == null && imageSize.containsKey(uploadImageDTO.getImageData().getImageName()) && count >= imageSize.get(uploadImageDTO.getImageData().getImageName())) {
            logger.info("image size constraint failed for merchant: {}, for application: {}", merchant.getId(), uploadImageDTO.getApplicationId());
            return null;
        }
        int index;
        if (uploadImageDTO.getImageData().getImageId() != null) {
            CpvDocumentsIdProof cpvDocumentsIdProof = cpvDocumentsIdProofDao.findByIdAndCpvIdAndMerchantIdAndModule(uploadImageDTO.getImageData().getImageId(), lendingCpvDetails.getId(), merchant.getId(), module);
            if (cpvDocumentsIdProof == null || cpvDocumentsIdProof.getDeletedAt() != null) {
                logger.info("Image not found/already deleted for id: {}, cpvId: {}, merchant: {}", uploadImageDTO.getImageData().getImageId(), lendingCpvDetails.getId(), merchant.getId());
                return null;
            }
            cpvDocumentsIdProof.setDeletedAt(new Date());
            cpvDocumentsIdProofDao.save(cpvDocumentsIdProof);
            index = cpvDocumentsIdProof.getImgIndex();
        } else {
            index = cpvDocumentsIdProofList.size() == 0 ? 0 : cpvDocumentsIdProofList.get(cpvDocumentsIdProofList.size() - 1).getImgIndex() + 1;
        }
        double latitude = 0D;
        double longitude = 0D;
        if (lat != null && lng != null && !lat.trim().equalsIgnoreCase("") && !lng.trim().equalsIgnoreCase("")) {
            latitude = Double.parseDouble(lat);
            longitude = Double.parseDouble(lng);
        }
        String base64Encoded = uploadDocumentService.processBase64String(uploadImageDTO.getImageData().getImageUrl());
        String mime = uploadImageDTO.getImageData().getImageUrl().replaceAll(";base64,", "").split("/")[1];
        String filename = "cpv/" + uploadImageDTO.getImageData().getImageName() + "_" + lendingCpvDetails.getId() + "_" + DateTime.now().getMillis() + "_" + merchant.getId() + "." + mime;
        s3BucketHandler.uploadToS3Bucket(base64Encoded, filename, S3_BUCKET);
        String imageUrl = "";
        try {
             imageUrl = s3BucketHandler.getTemporaryPublicURL(filename, S3_BUCKET);
        } catch (FileNotFoundException e) {
            logger.info("Exception while fetching file from S3---", e);
        }
        CpvDocumentsIdProof cpvDocumentsIdProof = new CpvDocumentsIdProof(merchant.getId(), uploadImageDTO.getImageData().getImageName(), lendingCpvDetails.getId(), filename, index, uploadImageDTO.getApplicationId(), null, "PENDING_VERIFICATION", uploadImageDTO.getImageData().getImageDescription(), null, latitude, longitude, module);
        cpvDocumentsIdProof = cpvDocumentsIdProofDao.save(cpvDocumentsIdProof);
        uploadImageDTO.getImageData().setImageUrl(imageUrl);
        uploadImageDTO.getImageData().setImageId(cpvDocumentsIdProof.getId());
        return uploadImageDTO;
    }

    public int deleteImage(Long imageId, Merchant merchant, Long applicationId) {
        String module = "MERCHANT_APP";
        LendingCpvDetails lendingCpvDetails = lendingCpvDetailsDao.findByApplicationIdAndModule(applicationId, module);
        if (lendingCpvDetails == null) {
            logger.info("lending cpv details not found for merchant: {}, for application: {}", merchant.getId(), applicationId);
            return 0;
        }
        CpvDocumentsIdProof cpvDocumentsIdProof = cpvDocumentsIdProofDao.findByIdAndCpvIdAndMerchantIdAndModule(imageId, lendingCpvDetails.getId(), merchant.getId(), module);
        if (cpvDocumentsIdProof == null || cpvDocumentsIdProof.getDeletedAt() != null) {
            logger.info("Image not found/already deleted for id: {}, cpvId: {}, merchant: {}", imageId, lendingCpvDetails.getId(), merchant.getId());
            return 0;
        }
        cpvDocumentsIdProof.setDeletedAt(new Date());
        cpvDocumentsIdProofDao.save(cpvDocumentsIdProof);
        return 1;
    }

    public MerchantDetailsDTO getMerchantDetails(Long applicationId) {
        String module = "MERCHANT_APP";
        LendingCpvDetails lendingCpvDetails = lendingCpvDetailsDao.findByApplicationIdAndModule(applicationId, module);
        MerchantDetailsDTO merchantDetailsDTO = new MerchantDetailsDTO();
        merchantDetailsDTO.setApplicationId(applicationId);
        setPersonalDetails(lendingCpvDetails, merchantDetailsDTO);
        setShopDetails(lendingCpvDetails, merchantDetailsDTO, module);
        setBusinessDetails(lendingCpvDetails, merchantDetailsDTO, module);
        return merchantDetailsDTO;
    }

    private void setBusinessDetails(LendingCpvDetails lendingCpvDetails, MerchantDetailsDTO merchantDetailsDTO, String module) {
        merchantDetailsDTO.setBusinessDetails(new MerchantDetailsDTO.BusinessDetails());
        merchantDetailsDTO.getBusinessDetails().setBusinessStartDate(lendingCpvDetails.getBusinessStartedAt());
        merchantDetailsDTO.getBusinessDetails().setDailySales(lendingCpvDetails.getDailySales());
        getBusinessDetailsImages(merchantDetailsDTO, lendingCpvDetails, module);
    }

    private void setShopDetails(LendingCpvDetails lendingCpvDetails, MerchantDetailsDTO merchantDetailsDTO, String module) {
        merchantDetailsDTO.setShopDetails(new MerchantDetailsDTO.ShopDetails());
        merchantDetailsDTO.getShopDetails().setBusinessType(lendingCpvDetails.getBusinessType());
        merchantDetailsDTO.getShopDetails().setShopOwnership(lendingCpvDetails.getOwnership());
        merchantDetailsDTO.getShopDetails().setShopName(lendingCpvDetails.getShopName());
        getShopDetailsImages(merchantDetailsDTO, lendingCpvDetails, module);
    }

    private void setPersonalDetails(LendingCpvDetails lendingCpvDetails, MerchantDetailsDTO merchantDetailsDTO) {
        merchantDetailsDTO.setPersonalDetails(new MerchantDetailsDTO.PersonalDetails());
        merchantDetailsDTO.getPersonalDetails().setAlternateMobile(lendingCpvDetails.getMobile() != null ? lendingCpvDetails.getMobile().toString() : null);
        merchantDetailsDTO.getPersonalDetails().setEmail(lendingCpvDetails.getEmail());
        merchantDetailsDTO.getPersonalDetails().setMaritalStatus(lendingCpvDetails.getEmail());
        merchantDetailsDTO.getPersonalDetails().setResidentialAddress(lendingCpvDetails.getAddress());
        merchantDetailsDTO.getPersonalDetails().setPincode(lendingCpvDetails.getPin() != null ? lendingCpvDetails.getPin().toString() : null);
        merchantDetailsDTO.getPersonalDetails().setState(lendingCpvDetails.getState());
        merchantDetailsDTO.getPersonalDetails().setCity(lendingCpvDetails.getCity());
        merchantDetailsDTO.getPersonalDetails().setLandmark(lendingCpvDetails.getLandmark());
    }
}
