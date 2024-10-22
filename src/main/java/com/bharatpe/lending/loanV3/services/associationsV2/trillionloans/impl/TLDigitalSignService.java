package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLDigitalSignRequestDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLDigitalSignCallbackResponseDto;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.service.APIGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.html2pdf.HtmlConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class TLDigitalSignService {

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CommonService commonService;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    DocUploadUtils docUploadUtils;

    @Value("${aws.s3.bucket:loan-document}")
    private String bucket;

    public Boolean invokeDigitalSign(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto, String docType) {
        try {
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails();

            lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStages.DIGI_SIGN.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);

            NBFCRequestDTO digitalSignRequest = getPayload(lenderAssociationDetailsRequestDto, DocType.valueOf(docType));
            if (Objects.isNull(digitalSignRequest) || Objects.isNull(digitalSignRequest.getPayload())) {
                log.info("error in doc upload payload of TrillionLoans for applicationId: {}", lenderAssociationDetailsRequestDto.getLendingApplication().getId());
                lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.DIGI_SIGN_FAILED.name());
                lendingApplicationLenderDetails.setESignedSanc(Boolean.FALSE);
                lendingApplicationLenderDetails.setFailedUpload(Objects.isNull(lendingApplicationLenderDetails.getFailedUpload()) ?
                        LenderAssociationStages.DIGI_SIGN.name() : lendingApplicationLenderDetails.getFailedUpload() + ";" + LenderAssociationStages.DIGI_SIGN.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                return false;
            }
            log.info("request body {}", new ObjectMapper().writeValueAsString(digitalSignRequest));
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(digitalSignRequest, LenderAssociationStages.DIGI_SIGN);
            log.info("Digital Sign response of TrillionLoans from nbfc for DocType: {} {} with applicationId: {}", nbfcResponseDto, docType, lenderAssociationDetailsRequestDto.getLendingApplication().getId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess()) {
                lendingApplicationLenderDetails.setDigitalDataUploadStatus(Objects.isNull(lendingApplicationLenderDetails.getFailedUpload()) ?
                            LenderAssociationStatus.DIGI_SIGN_IN_PROGRESS.name() : LenderAssociationStatus.DIGI_SIGN_FAILED.name());
                lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.DIGI_SIGN_IN_PROGRESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                return true;
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload of TrillionLoans for {} {} {} {}", docType, lenderAssociationDetailsRequestDto.getLendingApplication().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private NBFCRequestDTO<?> getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest, DocType docType) {
        try {
            LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
            if (ObjectUtils.isEmpty(lendingApplication) || ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                throw new RuntimeException("Lending Application / Lending Application Lender Details not found for application id : " + lenderAssociationDetailsRequest.getApplicationId());
            }

            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lenderAssociationDetailsRequest.getApplicationId(), lendingApplication.getLender());
            CKycResponseDto cKycResponseDto = kycUtils.getKycData(lenderAssociationDetailsRequest.getMerchantId());
            if (ObjectUtils.isEmpty(lendingKfs) || ObjectUtils.isEmpty(cKycResponseDto)) {
                throw new RuntimeException("Unable to fetch lending kfs/cKycResponseDto for application " + lenderAssociationDetailsRequest.getApplicationId());
            }
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(TLDigitalSignRequestDto.builder()
                            .fileUrl(getS3PresignedUrlFromKey(lendingKfs.getSanctionLoanAgreementDocFile()))
                            .fileName(docType.name() + docType.getFileExtension())
                            .expireInDays(10)
                            .displayOnPage("all")
                            .sendSignLink(Boolean.TRUE)
                            .notifySigners(Boolean.TRUE)
                            .isEstampRequired(Boolean.FALSE)
                            .signers(Collections.singletonList(TLDigitalSignRequestDto.Signer.builder()
                                            .identifier(ObjectUtils.isEmpty(cKycResponseDto.getMobile()) ? "" : cKycResponseDto.getMobile().substring(2))
                                            .name(Optional.ofNullable(cKycResponseDto.getName()).orElse("").trim())
                                            .signType("electronic")
                                            .reason(docType.name())
                                    .build()))
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating payload of digital sign of TrillionLoans for {} {} {}", lenderAssociationDetailsRequest.getLendingApplication().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getS3PresignedUrlFromKey(String key) {
        log.info("key to fetch from aws: {}", key);
        return ObjectUtils.isEmpty(key) ? "" : s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(key, bucket);
    }

    public Boolean processDigitalSignCallback(NBFCResponseDTO nbfcResponseDTO) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId())).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
                return false;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
                log.info("No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .manageState(true)
                    .modifyLender(enableLenderChange)
                    .build();
            if (Objects.nonNull(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                TLDigitalSignCallbackResponseDto digitalSignCallbackResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), TLDigitalSignCallbackResponseDto.class);
                log.info("Digital Sign callback Response of TrillionLoans for {} {}", nbfcResponseDTO.getApplicationId(), digitalSignCallbackResponseDto);
                if (!ObjectUtils.isEmpty(digitalSignCallbackResponseDto)) {
                    if (digitalSignCallbackResponseDto.getPayload().getDocument().getAgreementStatus().equalsIgnoreCase("completed") && !ObjectUtils.isEmpty(digitalSignCallbackResponseDto.getBytesPdfContent())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setDigitalDataUploadStatus(LenderAssociationStatus.DIGI_SIGN_COMPLETE.name());
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setESignedSanc(Boolean.TRUE);
                        docUploadUtils.saveESignedDocs(lendingApplication.getId(), null, digitalSignCallbackResponseDto.getBytesPdfContent());
                        commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                        return true;
                    }
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setDigitalDataUploadStatus(LenderAssociationStatus.DIGI_SIGN_FAILED.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequest);
        } catch (Exception e) {
            log.error("exception while processing KYC callback of Muthoot for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }
}
