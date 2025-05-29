package com.bharatpe.lending.loanV3.services.associationsV2.oxyzo.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.config.OxyzoConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.oxyzo.OxyzoKycRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.oxyzo.OxyzoCommonResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.oxyzo.OxyzoKycResponseDTO;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class OxyzoKycService {

    @Autowired
    CommonService commonService;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    OxyzoConfig oxyzoConfig;

    @Value("${aws.s3.bucket:loan-document}")
    private String bucket;

    @Transactional
    public boolean invokeKyc(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Oxyzo: Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.KYC.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();

            lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));

            NBFCRequestDTO kycRequestPayload = getKycRequestPayload(lenderAssociationDetailsDto);

            if (Objects.isNull(kycRequestPayload)) {
                log.info("Oxyzo: error in KYC payload of oxyzo for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.KYC_FAILED);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(kycRequestPayload, LenderAssociationStages.KYC);

            log.info("Oxyzo: KYC response of Oxyzo from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("Oxyzo: KYC request of Oxyzo success for {}", lenderAssociationDetailsDto.getApplicationId());

                OxyzoCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), OxyzoCommonResponseDTO.class);

                if (oxyzoConfig.getSuccessErrorCode().equalsIgnoreCase(commonResponseDTO.getErrorCode())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_COMPLETED.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }

            }
        } catch (Exception e) {
            log.error("Oxyzo: exception occurred while KYC of Oxyzo for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.KYC_FAILED);
        return false;

    }

    private NBFCRequestDTO getKycRequestPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {

            OxyzoKycRequestDTO docUploadRequest = OxyzoKycRequestDTO.builder()
                    .organisationId(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getCccId())
                    .profilePicture(lenderAssociationDetailsRequestDto.getCKycResponseDto().getSelfieString())
                    .aadhaarXml(getAadharXmlLink(lenderAssociationDetailsRequestDto))
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lenderAssociationDetailsRequestDto.getApplicationId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.OXYZO.name())
                    .payload(docUploadRequest)
                    .build();
        } catch (Exception e) {
            log.error("Exception while creating docUpload payload of oxyzo for applicationId: {}, {}, {}", lenderAssociationDetailsRequestDto.getLendingApplication(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getAadharXmlLink(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto){
        try{
            DocType docName = DocType.DIGILOCKER_AADHAAR_XML;
            byte[] aadharXml = lenderAssociationDetailsRequestDto.getCKycResponseDto().getPoaString().getBytes(StandardCharsets.UTF_8);
            InputStream aadharXmlStream = new ByteArrayInputStream(aadharXml);
            String fileName = docName.name() + "_" + lenderAssociationDetailsRequestDto.getLendingApplication().getId() + docName.getFileExtension();
            if (!ObjectUtils.isEmpty(aadharXmlStream)) {
                s3BucketHandler.uploadFileToS3WithTtl(aadharXmlStream,bucket, fileName, 7);
                return s3BucketHandler.getPreSignedPublicURL(fileName, bucket);
            }
        }
        catch (Exception e) {
            log.error("Exception in saving aadhar xml to s3 for oxyzo for {}, {}, {}", lenderAssociationDetailsRequestDto.getLendingApplication().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }

        return null;
    }
}
