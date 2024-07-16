package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingLoanInsuranceDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingLoanInsurance;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.dto.piramal.LoanInsuranceDocumentDTO;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static com.bharatpe.lending.constant.KfsConstants.INSURANCE_POLICY_DOC_PREFIX;

@Service
@Slf4j
public class InsurancePolicyDocService {
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    LendingApplicationDao lendingApplicationDao;
    @Autowired
    LendingCache lendingCache;
    @Autowired
    S3BucketHandler s3BucketHandler;
    @Autowired
    APIGatewayService apiGatewayService;
    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Autowired
    private LendingLoanInsuranceDao lendingLoanInsuranceDao;
    @Autowired
    private LoanUtil loanUtil;
    @Value("${aws.s3.bucket:loan-document}")
    private String bucket;

    public ApiResponse<?> uploadInsurancePolicyDoc(NbfcResponseDto nbfcResponseDto) {
        try {
            log.info("insurance policy doc request for {}", objectMapper.writeValueAsString(nbfcResponseDto));

            LoanInsuranceDocumentDTO loanInsuranceDocument = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), LoanInsuranceDocumentDTO.class);
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDto.getApplicationId()));
            if (!lendingApplication.isPresent()) {
                log.info("application {} not found for insurance policy doc event", nbfcResponseDto.getApplicationId());
                return new ApiResponse<>(false, "lending application doesn't exists !");
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.get().getId(), Status.ACTIVE.name(), lendingApplication.get().getLender());

            acquireLockAndUploadFileToS3(loanInsuranceDocument, lendingApplicationLenderDetails);

        } catch (Exception e) {
            log.error("Exception occurred while processing uploadLoanInsuranceDocument: {}", e.getMessage());
            return new ApiResponse<>(false, e.getMessage());
        }
        return new ApiResponse<>(true);
    }

    private void acquireLockAndUploadFileToS3(LoanInsuranceDocumentDTO loanInsuranceDocument, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        String policyDocLockKey = "LOAN_POLICY_DOC_" + lendingApplicationLenderDetails.getApplicationId();
        if (lendingCache.acquireLock(policyDocLockKey, 5)) {
            uploadPolicyToS3(loanInsuranceDocument, lendingApplicationLenderDetails);
            lendingCache.releaseLock(policyDocLockKey);
        } else {
            log.error("Unable to redis lock with key: {}", policyDocLockKey);
        }
    }

    private void uploadPolicyToS3(LoanInsuranceDocumentDTO loanInsuranceDocument, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        try {

            log.info("uploading insurance policy doc on S3 for {} !", lendingApplicationLenderDetails.getApplicationId());
            String policyDocFileNumber = INSURANCE_POLICY_DOC_PREFIX + lendingApplicationLenderDetails.getApplicationId();
            byte[] doc = Base64.getDecoder().decode(loanInsuranceDocument.getFileBlob());
            InputStream inputStream = new ByteArrayInputStream(doc);
            s3BucketHandler.uploadToS3PdfBucket(inputStream, policyDocFileNumber, bucket);
            savePolicyDocUrl(
                    apiGatewayService.getShortUrl(
                            s3BucketHandler.getPreSignedPublicURL(policyDocFileNumber, bucket)),
                    lendingApplicationLenderDetails,
                    loanInsuranceDocument);
        } catch (FileNotFoundException e) {
            log.error("File not found exception occurred while getting pre-signed url for PolicyLoanDoc");
        }
    }

    private void savePolicyDocUrl(String shortUrl, LendingApplicationLenderDetails lendingApplicationLenderDetails, LoanInsuranceDocumentDTO loanInsuranceDocument) {
        LendingLoanInsurance loanInsurance = loanUtil.getInsuranceDetails(
                lendingApplicationLenderDetails.getApplicationId(),
                lendingApplicationLenderDetails.getLender(),
                "SELECTED");

        loanInsurance.setPolicyDocUrl(shortUrl);
        loanInsurance.setCommencementDate(new Date(loanInsuranceDocument.getCommencementDate()));
        loanInsurance.setMaturityDate(new Date(loanInsuranceDocument.getMaturityDate()));
        lendingLoanInsuranceDao.save(loanInsurance);
    }
}
