package com.bharatpe.lending.loanV3.services.associationsV2;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.bharatpe.lending.util.FileUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class AbflDigiSignService {
    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LenderGatewayFactory lenderGatewayFactory;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    MerchantService merchantService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DocUploadUtils docUploadUtils;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Value("${aws.s3.bucket}")
    private String bucket;

    private static final String CURRENT_DIR = Paths.get("").toAbsolutePath().toString();

    public AbflDigiSignResponseDTO invokeDigiSign(Long applicationId, LendingApplication lendingApplication) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
        try {
            log.info("DIGI sign: initiating for abfl lender for applicationId: {}", applicationId);
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return null;
            }
            AbflDigiSignRequestDTO digiSignRequest = createPayload(lendingApplication);
            INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(digiSignRequest.getLender());
            AbflDigiSignResponseDTO digiSignResponseDTO = apiGatewayV3.invokeDigiSign(digiSignRequest);
            if (!ObjectUtils.isEmpty(digiSignResponseDTO)
                    && (!ObjectUtils.isEmpty(digiSignResponseDTO.getData()) && StatusCheckResponse.SUCCESS.name().equalsIgnoreCase(digiSignResponseDTO.getData().getResponseStatus()))){
                log.info("DIGI sign: successfully placed the digi sign request at lender for {}", applicationId);
                lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.DIGI_SIGN_IN_PROGRESS.name());
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                return digiSignResponseDTO;
            }
        } catch (Exception ex) {
            log.error("DIGI sign: exception occurred while processing digiSign request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        log.error("DIGI sign: Unable to initiate digiSign request at lender for : {}", applicationId);
        lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.DIGI_SIGN_INIT_FAILED.name());
        lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        return null;
    }

    private AbflDigiSignRequestDTO createPayload(LendingApplication lendingApplication) {
        try {
        LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
        if(ObjectUtils.isEmpty(lendingKfs)) {
            log.error("DIGI sign: No documents found for applicationId {} for digiSign API", lendingApplication.getId());
            throw new RuntimeException("DIGI sign: Kfs and sanction letter not found for applicationId");
        }
        MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(lendingApplication.getMerchantId());
        if(ObjectUtils.isEmpty(merchantDetailsDto)) {
            log.error("DIGI sign: Error in fetching merchant details for merchantId: {}", lendingApplication.getMerchantId());
            throw new RuntimeException("DIGI sign: error in fetching merchant details for ABFL DigiSign API");
        }
        String mergedURL = mergedKFSAndSanctionLetterUrl(lendingApplication.getId(), lendingKfs.getKfsDocUrl(), lendingKfs.getSanctionLoanAgreementDocUrl());
        return AbflDigiSignRequestDTO.builder()
                .applicationId(lendingApplication.getId())
                .lender("ABFL")
                .productName("LENDING")
                .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())?true:false)
                .payload(AbflDigiSignRequestDTO.Payload.builder()
                        .accountId(lendingApplication.getExternalLoanId())
                        .unsigned_merged_pdf(mergedURL)
                        .merged_pdf_flag(Boolean.TRUE)
                        .mobile_number(merchantDetailsDto.getMerchantDetail().getMobile().substring(2))
                        .build())
                .build();
        } catch (IOException ex) {
            log.error("DIGI sign: IOException while merging kfs and sanction files applicationId {}", lendingApplication.getId());
            throw new RuntimeException("DIGI sign: eIOException while merging kfs and sanction files");
        }catch (DocumentException exception) {
            log.error("DIGI sign: IOException while merging kfs and sanction files applicationId {}", lendingApplication.getId());
            throw new RuntimeException("DIGI sign: DocumentException while merging kfs and sanction files");
        } catch (Exception exception) {
            log.error("DIGI sign: Error in while merging kfs and sanction files applicationId: {}", lendingApplication.getId());
            throw new RuntimeException("DIGI sign: error in merging docs for ABFL DigiSign API");
        }
    }

    public Boolean processDigitalSignCallback(NBFCResponseDTO nbfcResponseDTO) {
        LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId())).orElse(null);
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
            return false;
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
        try {
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
                log.info("No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            if (Objects.nonNull(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                AbflDigiSignStatusResponseDTO digitalSignCallbackResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), AbflDigiSignStatusResponseDTO.class);
                log.info("DIGI sign: callback Response for id {} {}", nbfcResponseDTO.getApplicationId(), digitalSignCallbackResponseDto);
                if (!ObjectUtils.isEmpty(digitalSignCallbackResponseDto) && !ObjectUtils.isEmpty(digitalSignCallbackResponseDto.getData()) && !ObjectUtils.isEmpty(digitalSignCallbackResponseDto.getData().getShortUrl())) {
                    lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.DIGI_SIGN_COMPLETE.name());
                    lendingApplicationLenderDetails.setESignedSanc(Boolean.TRUE);
                    lendingApplicationLenderDetails.setESignedKfs(Boolean.TRUE);
                    docUploadUtils.saveESignedDocs(lendingApplication.getId(), digitalSignCallbackResponseDto.getData().getShortUrl(), digitalSignCallbackResponseDto.getData().getShortUrl());
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("exception while processing DIGI sign callback of ABFL for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.DIGI_SIGN_HARD_FAILED.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        }
        return false;
    }

    public String mergedKFSAndSanctionLetterUrl(Long applicationId,
                                            String docKfsName, String docSanctionName) throws IOException, DocumentException {

        String mergedFileName = "KFS_SANCTION_AGREEMENT_MERGED_FOR_DIGISIGN_"+ applicationId + ".pdf";

        URL url1 = new URL(s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(docKfsName,bucket));
        URLConnection connection1 = url1.openConnection();
        InputStream inputStream1 = connection1.getInputStream();
        PdfReader reader1 = new PdfReader(inputStream1);

        URL url2 = new URL(s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(docSanctionName,bucket));
        URLConnection connection2 = url2.openConnection();
        InputStream inputStream2 = connection2.getInputStream();
        PdfReader reader2 = new PdfReader(inputStream2);

        Document document = new Document();
        PdfCopy copy = new PdfCopy(document, Files.newOutputStream(Paths.get("/data/" + mergedFileName)));
        copy.setCompressionLevel(9);
        document.open();

        copy.addDocument(reader1);
        copy.addDocument(reader2);

        document.close();

        File mergedFile = new File("/data/" + mergedFileName);
        s3BucketHandler.uploadFileToS3(mergedFile,"loan-document", mergedFileName);

        String mergeDocumentPresignedUrl = s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(mergedFileName, bucket);

        log.info("pre-signed url for merged doc for digi sign: {}, {}", applicationId,  mergeDocumentPresignedUrl);

        Path uploadedFilePath = Paths.get(CURRENT_DIR + "/" + mergedFileName);

        FileUtil.deleteFile(uploadedFilePath);

        return mergeDocumentPresignedUrl;

    }

}