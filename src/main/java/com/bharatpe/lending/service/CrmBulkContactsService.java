package com.bharatpe.lending.service;

import com.bharatpe.lending.common.dao.CrmBulkContactsDao;
import com.bharatpe.lending.common.entity.CrmBulkContacts;
import com.bharatpe.lending.common.enums.CrmBulkContactsResponseStatus;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.slave.dao.PhonebookDaoSlave;
import com.bharatpe.lending.common.slave.entity.PhonebookSlave;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CrmBulkContactsService {
    Logger logger = LoggerFactory.getLogger(CrmBulkContactsService.class);

    @Autowired
    CrmBulkContactsDao crmBulkContactsDao;

//    @Autowired
//    MerchantDao merchantDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    PhonebookDaoSlave phonebookDaoSlave;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Value("${crm.contact.bucket.name}")
    private String bucketName;

    @Value("${aws.s3.bucket}")
    private String loanDocBucket;

    @Value("${crm.contact.bucket.region}")
    private String contactBucketRegion;
    @Autowired
    MerchantService merchantService;

    @Async
    public void fetchCrmBulkContacts(InputStream bulkContactFile, Long requestId) throws IOException {
        Optional<CrmBulkContacts> crmBulkContacts = crmBulkContactsDao.findById(requestId);
        if (!crmBulkContacts.isPresent()) {
            return;
        }
        try {
            BufferedReader bulkContactFileReader = new BufferedReader(new InputStreamReader(bulkContactFile));
            String readLine = bulkContactFileReader.readLine();
            int count = 0;
            List<String[]> emptyPhoneBookData = new ArrayList<String[]>();
            String[] emptyPhoneBookHeader = {"contact", "reason"};
            emptyPhoneBookData.add(emptyPhoneBookHeader);
            String zipFileName = "/tmp/bulkContact-" + requestId + ".zip";
            File zipFile = new File(zipFileName);
            FileOutputStream fos = new FileOutputStream(zipFile);
            ZipOutputStream zos = new ZipOutputStream(fos);
            while (Objects.nonNull(readLine)) {
                String[] temp = readLine.split(",");
                String contact = temp[0];
                if (contact.length() ==10) {
                    contact  = "91" + contact;
                }
                try {
                    if (count == 0) {
                        count++;
                        readLine = bulkContactFileReader.readLine();
                        continue;
                    }
//                    Merchant merchant = merchantDao.findByMobile(contact);
                    Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetailsByMobile(contact);
                    if (ObjectUtils.isEmpty(basicDetailsDto)) {
                        emptyPhoneBookData.add(new String[]{contact, "invalid merchant"});
                        readLine = bulkContactFileReader.readLine();
                        continue;
                    }
//                    LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
//                    if (ObjectUtils.isEmpty(lendingPaymentSchedule) || !"ACTIVE".equalsIgnoreCase(lendingPaymentSchedule.getStatus())) {
//                        emptyPhoneBookData.add(new String[]{contact, "no active loan"});
//                        readLine = bulkContactFileReader.readLine();
//                        continue;
//                    }
                    PhonebookSlave phonebook = phonebookDaoSlave.findTop1ByMerchantIdOrderByContactsCountDesc(basicDetailsDto.get().getId());
                    if (ObjectUtils.isEmpty(phonebook)) {
                        emptyPhoneBookData.add(new String[]{contact, "no contacts found"});
                        readLine = bulkContactFileReader.readLine();
                        continue;
                    }
                    String contactFileName = "/tmp/" + contact + "-" + requestId + ".csv";
                    if (phonebook.getContactsCount() > 50) {
                        String phoneBookS3Url = phonebook.getS3URL();
                        InputStream contactDataFileInputStream = s3BucketHandler.getObject(phoneBookS3Url.substring(phoneBookS3Url.lastIndexOf("/") + 1), bucketName, contactBucketRegion);
                        File contactDataFile = new File(contactFileName);
                        ZipEntry zipEntry = new ZipEntry(contactDataFile.getName());
                        zos.putNextEntry(zipEntry);
                        byte[] bytes = new byte[1024];
                        int length;
                        while ((length = contactDataFileInputStream.read(bytes)) >= 0) {
                            zos.write(bytes, 0, length);
                        }
                        contactDataFileInputStream.close();
                        count++;
                        readLine = bulkContactFileReader.readLine();
                        continue;

                    } else {
                        String outputPath = mergeContacts(basicDetailsDto.get().getId(), contactFileName);
                        if (outputPath!=null) {
                            addToZip(contact + "-" + requestId + ".csv", zos,outputPath);
                            count++;
                            readLine = bulkContactFileReader.readLine();
                            continue;
                        }
                    }
                    count++;
                } catch (Exception e) {
                    logger.info("Exception occured while processing {}", readLine);
                }
                emptyPhoneBookData.add(new String[]{contact, "no contacts found"});
                readLine = bulkContactFileReader.readLine();
            }
            String emptyPhonebookPath = "/tmp/emptyPhoneBook-" + requestId + ".csv";
            File emptyPhoneBook = new File(emptyPhonebookPath);
            FileWriter emptyPhoneBookOutputFile = new FileWriter(emptyPhoneBook);
            CSVWriter emptyPhoneBookWriter = new CSVWriter(emptyPhoneBookOutputFile);
            emptyPhoneBookWriter.writeAll(emptyPhoneBookData);
            emptyPhoneBookWriter.close();
            addToZip(emptyPhoneBook.getName(),zos,emptyPhonebookPath);
            ZipEntry zipEntry = new ZipEntry(emptyPhoneBook.getName());
            zos.close();
            fos.close();
            InputStream zipInputStream = new ByteArrayInputStream(Files.readAllBytes(Paths.get(zipFileName)));
//            s3BucketHandler.uploadFileToS3WithTtl(zipInputStream,"loan-bucket",requestId + ".zip",7);
            s3BucketHandler.uploadFileToS3WithTtl(zipInputStream,loanDocBucket,"bulkContact-" + requestId + ".zip",7);
            crmBulkContacts.get().setStatus(CrmBulkContactsResponseStatus.SUCCESS.name());
            String zipS3Url = s3BucketHandler.getPreSignedPublicURL("bulkContact-" + requestId + ".zip", loanDocBucket);
            crmBulkContacts.get().setPresignedUrl(zipS3Url);
            crmBulkContactsDao.save(crmBulkContacts.get());
            return;
        } catch (Exception e) {
            logger.error("Exception while fetching contacts");
        }
        crmBulkContacts.get().setStatus(CrmBulkContactsResponseStatus.FAILED.name());
        crmBulkContactsDao.save(crmBulkContacts.get());
    }
    public String mergeContacts (Long merchantId, String mergedContactsFileName) throws IOException {
        try {
            Map<String, String> lookUp = new HashMap<>();
            File mergedContactsFile = new File(mergedContactsFileName);
            FileWriter mergedContactsFileWriter = new FileWriter(mergedContactsFile);
            CSVWriter mergedContactsCsvWriter = new CSVWriter(mergedContactsFileWriter);
            mergedContactsCsvWriter.writeNext(new String[]{"name", "contact"});
            Pageable pageable = PageRequest.of(0, 10);
            List<PhonebookSlave> phonebookList = phonebookDaoSlave.getPhonebookByMerchantIdOrderByContactsCountDesc(merchantId, pageable);
            int contactCount = 0;
            for (PhonebookSlave phonebook : phonebookList) {
                String phoneBookS3Url = phonebook.getS3URL();
                InputStream contactDataFileInputStream = s3BucketHandler.getObject(phoneBookS3Url.substring(phoneBookS3Url.lastIndexOf("/") + 1), bucketName, contactBucketRegion);
                BufferedReader contactFile = new BufferedReader(new InputStreamReader(contactDataFileInputStream));
                String readLine = contactFile.readLine();
                int count = 0;
                if (contactCount >= 50) {
                    break;
                }
                while (Objects.nonNull(readLine)) {
                    if (count == 0) {
                        count++;
                        readLine = contactFile.readLine();
                        continue;
                    }
                    String[] record = readLine.split(",");
                    if (record.length < 2) {
                        count++;
                        readLine = contactFile.readLine();
                        continue;
                    }
                    String name = record[0];
                    String mobile = record[1];
                    if (!lookUp.containsKey(mobile)) {
                        lookUp.put(mobile, name);
                        mergedContactsCsvWriter.writeNext(new String[]{name, mobile});
                        contactCount++;
                    }
                    readLine = contactFile.readLine();
                }
            }
            mergedContactsCsvWriter.close();
            return mergedContactsFileName;
        } catch (Exception e) {
            logger.error("Exception occurred while merging process {}", Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public void addToZip(String fileName, ZipOutputStream zos, String filePath) throws IOException {
        ZipEntry zipEntry = new ZipEntry(fileName);
        zos.putNextEntry(zipEntry);
        byte[] bytes = Files.readAllBytes(Paths.get(filePath));
        zos.write(bytes, 0, bytes.length);
        zos.closeEntry();
    }
}
