package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.enums.ApplicationDocType;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;


@Slf4j
@Service
public class ArcSoldLoanService {

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingNotificationService lendingNotificationService;

    @Autowired
    MerchantService merchantService;


    static Map<String, String> lenderNames = new HashMap<String, String>(){{
        put("LIQUILOANS_NBFC","TRILLION LOANS");
        put("HINDON","HINDON MERCANTILE LIMITED");
    }};
    
    static String ARC_COMM_LETTER_PREFIX = "ARC_COMM_LETTER_";

    static String bucket = "loan-document";


    public String getArcCommLetterShortUrl(LendingPaymentSchedule lendingPaymentSchedule, BasicDetailsDto basicDetailsDto) throws Exception {

        String presignedUrl = "";

        String fileName = ARC_COMM_LETTER_PREFIX + lendingPaymentSchedule.getId() + ".pdf";

        if (s3BucketHandler.doesS3ObjectExist(bucket, fileName)) {
            presignedUrl = fetchArcCommunicationFromS3andPresignedUrl(fileName);
        } else {
            presignedUrl = saveArcCommunicationLetter(lendingPaymentSchedule, basicDetailsDto);
        }

        return presignedUrl;
    }

    public ResponseDTO sendArcCommunication(Long lpsId, String mobile, BasicDetailsDto basicDetailsDto) {

        Optional<LendingPaymentSchedule> lendingPaymentSchedule = lendingPaymentScheduleDao.findById(lpsId);

        if (!lendingPaymentSchedule.isPresent()) {
            return new ResponseDTO(false, "Lps is not present");
        }

        if (mobile.isEmpty()) {
            mobile = lendingPaymentSchedule.get().getMobile();
        }

        if (ObjectUtils.isEmpty(basicDetailsDto)) {
            basicDetailsDto = merchantService.fetchMerchantBasicDetails(lendingPaymentSchedule.get().getMerchantId()).get();
        }

        final String arcCommLetterShortUrl;
        try {
            final String presignedUrl = getArcCommLetterShortUrl(lendingPaymentSchedule.get(), basicDetailsDto);
            arcCommLetterShortUrl = apiGatewayService.getShortUrl(presignedUrl);
        } catch (Exception e) {
            return new ResponseDTO(false, "Error occured while generating communication letter");
        }


        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("url", arcCommLetterShortUrl);

        String accountNumber = lendingPaymentSchedule.get().getLoanApplication() != null ? lendingPaymentSchedule.get().getLoanApplication().getExternalLoanId() : lendingPaymentSchedule.get().getId().toString();

        templateParams.put("account_number", accountNumber.substring(accountNumber.length()-4));

        String smsIdentifier = "";
        String whatsAppIdentifier = "";

        if (Lender.HINDON.name().equalsIgnoreCase(lendingPaymentSchedule.get().getNbfc())) {
            smsIdentifier = "ARC_COMMUNICATION_HINDON_SMS";
            whatsAppIdentifier = "ARC_COMMUNICATION_HINDON_WA";
        } else if (Lender.LIQUILOANS_NBFC.name().equalsIgnoreCase(lendingPaymentSchedule.get().getNbfc())) {
            smsIdentifier = "ARC_COMMUNICATION_TRILLION_SMS";
            whatsAppIdentifier = "ARC_COMMUNICATION_TRILLION_WA";
        }

        if (!ObjectUtils.isEmpty(smsIdentifier)) {
            //FOR SMS
            NotificationPayloadDto notificationPayloadForSMS = new NotificationPayloadDto();
            notificationPayloadForSMS.setTemplateIdentifier(smsIdentifier);
            notificationPayloadForSMS.setClientName("LENDING");
            notificationPayloadForSMS.setMobile(mobile);
            notificationPayloadForSMS.setTemplateParams(templateParams);

            log.info("payload for arc communication SMS for lpsId: {} {}", notificationPayloadForSMS, lendingPaymentSchedule.get().getId());
            lendingNotificationService.notify(notificationPayloadForSMS);

        }

        if (!ObjectUtils.isEmpty(whatsAppIdentifier)) {
            // FOR WHATSAPP
            NotificationPayloadDto notificationPayloadForWhatsapp = new NotificationPayloadDto();
            notificationPayloadForWhatsapp.setTemplateIdentifier(whatsAppIdentifier);
            notificationPayloadForWhatsapp.setClientName("LENDING");
            notificationPayloadForWhatsapp.setMobile(mobile);
            notificationPayloadForWhatsapp.setTemplateParams(templateParams);

            log.info("payload for arc communication WHATSAPP for lpsId: {} {}", notificationPayloadForWhatsapp, lendingPaymentSchedule.get().getId());
            lendingNotificationService.notify(notificationPayloadForWhatsapp);

        }


        return new ResponseDTO(true, arcCommLetterShortUrl);
    }

    public ResponseDTO getArcCommunicationLetterUrl(Long loanId, BasicDetailsDto basicDetailsDto) {

        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByIdAndMerchantId(loanId, basicDetailsDto.getId());

        if (ObjectUtils.isEmpty(lendingPaymentSchedule)) {
            log.info("lps not present for loanId : {} and merchantId : {}", loanId, basicDetailsDto.getId());
            return new ResponseDTO(false, "Lps is not present");
        }

        final String presignedUrl;
        try {
            presignedUrl = getArcCommLetterShortUrl(lendingPaymentSchedule, basicDetailsDto);
        } catch (Exception e) {
            log.error("Exception occurred while generating communication letter for loanId : {} and merchantId : {} {}", loanId, basicDetailsDto.getId(), Arrays.asList(e.getStackTrace()));
            return new ResponseDTO(false, "Error occured while generating communication letter");
        }

        Map<String, String> data = new HashMap<String, String>(){{
            put("url", presignedUrl);
        }};

        return new ResponseDTO(true, "success", data);
    }


    private String generateArcCommunicationLetterHtml(LendingPaymentSchedule  lendingPaymentSchedule, BasicDetailsDto basicDetailsDto){

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("date", new Date());
            data.put("due_amount", lendingPaymentSchedule.getDueAmount());

            String accountNumber = lendingPaymentSchedule.getLoanApplication() != null ? lendingPaymentSchedule.getLoanApplication().getExternalLoanId() : lendingPaymentSchedule.getId().toString();

            data.put("account_number", accountNumber.substring(accountNumber.length()-4));
            data.put("lender_name", lenderNames.get(lendingPaymentSchedule.getNbfc()));

            data.put("customer_name", basicDetailsDto.getBeneficiaryName());
            data.put("customer_address", "");

            if (!ObjectUtils.isEmpty(lendingPaymentSchedule.getApplicationId())) {
                data.put("customer_address", constructShopAddress(lendingPaymentSchedule.getLoanApplication()));
            }

            String html = "";
            String filePath = "/templates/ARC_COMM_LETTER.html";
            InputStream inputStream = this.getClass().getResourceAsStream(filePath);
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            html = scanner.hasNext() ? scanner.next() : "";;
            for(Map.Entry<String,Object> entry : data.entrySet()) {
                String key = "{{" + entry.getKey() + "}}";
                String val = Objects.nonNull(entry.getValue()) ? entry.getValue().toString() : "";
                log.info(key + " " + val);
                html = html.replace(key, val);
            }
            return html;
        } catch (Exception e) {
            log.error("Exception while generating arc communication letter html for lpsId : {}, {}, {}",lendingPaymentSchedule.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return "";
        }
    }

    private String saveArcCommunicationLetter(LendingPaymentSchedule  lendingPaymentSchedule, BasicDetailsDto basicDetailsDto) throws Exception {

        final String html = generateArcCommunicationLetterHtml(lendingPaymentSchedule, basicDetailsDto);

        if (html.isEmpty()) {
            log.error("Unable to store ArcCommunicationLetter pdf doc for lpsId : {}", lendingPaymentSchedule.getId());
            throw new Exception("Unable to store ArcCommunicationLetter pdf doc for lpsId" + lendingPaymentSchedule.getId());
        }

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outStream);
        PdfDocument pdfDocument = new PdfDocument(writer);
        if (Lender.HINDON.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc())) {
            ImageData headerImageData = ImageDataFactory.create(lendingApplicationServiceV2.getLenderLogo(lendingPaymentSchedule.getNbfc(),
              ApplicationDocType.HINDON_LETTERHEAD_HEADER));
            ImageData footerImageData = ImageDataFactory.create(lendingApplicationServiceV2.getLenderLogo(lendingPaymentSchedule.getNbfc(),
              ApplicationDocType.HINDON_LETTERHEAD_FOOTER));
            Header headerHandler = new Header(headerImageData);
            pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, headerHandler);
            Footer footerHandler = new Footer(footerImageData);
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, footerHandler);
        } else {
            ImageData logoImageData = ImageDataFactory.create(lendingApplicationServiceV2.getLenderLogo(lendingPaymentSchedule.getNbfc(),
              ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC));
            Header headerHandler = new Header(logoImageData);
            pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, headerHandler);
        }

        InputStream htmlStringInputStream = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
        HtmlConverter.convertToPdf(htmlStringInputStream, pdfDocument);
        ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
        String fileName = ARC_COMM_LETTER_PREFIX + lendingPaymentSchedule.getId() + ".pdf";
        s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, bucket);
        String shortUrl = fetchArcCommunicationFromS3andPresignedUrl(fileName);
        if(shortUrl == null || shortUrl.isEmpty() || shortUrl.trim().isEmpty())
            throw new Exception("Unable to create short URL for ArcCommunicationLetter doc link for lpsId: " + lendingPaymentSchedule.getId());

        return shortUrl;
    }


    private String fetchArcCommunicationFromS3andPresignedUrl(String fileName) throws Exception {
        return s3BucketHandler.getPreSignedPublicURL(fileName, bucket);
    }

    private String constructShopAddress(LendingApplication lendingApplication) {
        return (ObjectUtils.isEmpty(lendingApplication.getShopNumber()) ? "" : lendingApplication.getShopNumber()) + " " +
          (ObjectUtils.isEmpty(lendingApplication.getStreetAddress()) ? "" : lendingApplication.getStreetAddress()) + " " +
          (ObjectUtils.isEmpty(lendingApplication.getLandmark()) ? "" : lendingApplication.getLandmark()) + " " +
          (ObjectUtils.isEmpty(lendingApplication.getCity()) ? "" : lendingApplication.getCity()) + " " +
          (ObjectUtils.isEmpty(lendingApplication.getState()) ? "" : lendingApplication.getState()) + " " +
          (ObjectUtils.isEmpty(lendingApplication.getPincode()) ? "" : lendingApplication.getPincode());
    }

    protected static class Header implements IEventHandler {
        private ImageData headerImage;

        public Header(ImageData header) {
            this.headerImage = header;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            Rectangle pageSize = page.getPageSize();
            Rectangle rectangle = new Rectangle(0, pageSize.getHeight() - 75, pageSize.getWidth(), 75);
            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);
            pdfCanvas.addImageFittedIntoRectangle(headerImage, rectangle, false);
            pdfCanvas.release();
        }
    }

    protected static class HeaderFooter implements IEventHandler {
        private ImageData footerImage;
        private ImageData headerImage;

        public HeaderFooter(ImageData header,ImageData footer) {
            this.footerImage = footer;
            this.headerImage = header;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            Rectangle pageSize = page.getPageSize();
            Rectangle headerRectangle = new Rectangle(0, pageSize.getHeight() - 75, pageSize.getWidth(), 75);
            Rectangle footerRectangle = new Rectangle(0, 20 , pageSize.getWidth(), 80);
            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);
            pdfCanvas.addImageFittedIntoRectangle(headerImage, headerRectangle, false);
            pdfCanvas.addImageFittedIntoRectangle(footerImage, footerRectangle, false);
            pdfCanvas.release();
        }
    }

    protected static class Footer implements IEventHandler {
        private ImageData footerImage;

        public Footer(ImageData footer) {
            this.footerImage = footer;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            Rectangle pageSize = page.getPageSize();
            Rectangle footerRectangle =  new Rectangle(0, 0 , pageSize.getWidth(), 90);
            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);
            pdfCanvas.addImageFittedIntoRectangle(footerImage, footerRectangle, false);
            pdfCanvas.release();
        }
    }
}
