package com.bharatpe.lending.service;

import com.bharatpe.common.dao.InternalClientDao;
import com.bharatpe.common.dao.LendingNachBankDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.common.Constants.BPEnachConstant;
import com.bharatpe.lending.common.dao.IfscNewDao;
import com.bharatpe.lending.common.entity.BpEnachSkip;
import com.bharatpe.lending.common.entity.IfscNew;
import com.bharatpe.lending.common.enums.BPEnachEnum;
import com.bharatpe.lending.dao.BPEnachDao;
import com.bharatpe.lending.common.entity.BpEnach;
import com.bharatpe.lending.dao.BPEnachSkipDao;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class BPEnachService {

    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    @Autowired
    BPEnachDao bpEnachDao;

    @Autowired
    LendingNachBankDao lendingNachBankDao;

    @Autowired
    BPEnachSkipDao bpEnachSkipDao;


    @Value("${enach.digio.authorization}")
    String authorization;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;


    @Autowired
    IfscNewDao ifscNewDao;

    @Value("${bpnach.register.endpoint}")
    public String BPNACH_REGISTER_URL;

    @Autowired
    AesEncryption aesEncryption;

    @Autowired
    InternalClientDao internalClientDao;

    @Autowired
    HmacCalculator hmacCalculator;

    Logger logger = LoggerFactory.getLogger(BPEnachService.class);

    public ENachIntitiationResponseDTO eNachInitiate(Merchant merchant, String appVersion, String module, Double nachAmount, String type,String referenceNumber) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        Date mandateDate = new Date(new Date().getTime() + (1000 * 60 * 60 * 24));
        final double LOAN_AMOUNT = nachAmount;
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
        if (merchantBankDetail == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Active Bank not found");
            logger.error("No Bank detail found for Merchant - {}", merchant.getId());
            return responseDTO;
        }
        String bankCode;
        if (appVersion != null && Integer.parseInt(appVersion) >= 238) {
            bankCode = fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "BOTH");
        } else {
            bankCode = fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "NET");
        }
        if (bankCode == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Bank not supported for Enach");
            logger.error("Merchant Bank not supported for Enach - {}", merchant);
            return responseDTO;
        }
        String bankBranch = getBranchName(merchantBankDetail.getIfscCode());
        if (bankBranch == null || StringUtils.isEmpty(bankBranch)) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Bank Branch not found for Enach");
            logger.error("Merchant Bank Br`anch not found for Enach - {}", merchant);
            return responseDTO;
        }

        BpEnach bpEnach = new BpEnach(merchant.getId(), referenceNumber, type, merchant.getBeneficiaryName(), merchant.getBeneficiaryName(), Long.parseLong(bankCode),
                merchantBankDetail.getBankName(), merchantBankDetail.getAccountNumber(), merchantBankDetail.getIfscCode(), merchantBankDetail.getAccType(),
                BPEnachConstant.NACH_LENDER, BPEnachConstant.INTERNAL_NACH_TYPE, BPEnachConstant.NACH_MODE, LOAN_AMOUNT, mandateDate, BPEnachEnum.applicationStatus.INPROCESS.toString(), bankBranch
        );

        bpEnach = bpEnachDao.save(bpEnach);
        responseDTO.setData(new ENachIntitiationResponseDTO.Data(bpEnach.getId(), bpEnach.getId(), bankCode, LOAN_AMOUNT, sdf.format(mandateDate), bpEnach.getId(), merchantBankDetail.getAccountNumber(), merchantBankDetail.getBeneficiaryName(), merchantBankDetail.getIfscCode(), merchant.getMid()));
        return responseDTO;
    }


    public ENachIntitiationResponseDTO submitEnach(Merchant merchant, ENachSubmitRequestDTO requestDTO) {
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(new ENachIntitiationResponseDTO.Data());
        BpEnach bpEnach = bpEnachDao.findByIdAndMerchantIdAndStatus(requestDTO.getApplicationId(), merchant.getId(), BPEnachEnum.applicationStatus.INPROCESS.toString());
        BPEnachEnum.enachDeepLink bpEnachEnum = BPEnachEnum.enachDeepLink.valueOf(bpEnach.getPlatform().toUpperCase());
        responseDTO.getData().setDeep_link("bharatpe://dynamic?key=" + bpEnachEnum.toString().toLowerCase() + "&&wroute=status");

        if (bpEnach == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Enach not initiated");
            return responseDTO;
        }
        bpEnach.setIdentifier(requestDTO.getIdentifier());
        bpEnach.setMandateId(requestDTO.getMandateId());
        bpEnach.setResponse(requestDTO.getResponse());
        bpEnach.setBankMessage(requestDTO.getStatusMessage());

        if (requestDTO.getStatus()) {
            bpEnach.setStatus(BPEnachEnum.applicationStatus.APPROVED.toString());
            bpEnach.setNachStatus(BPEnachEnum.applicationStatus.APPROVED.toString());
        } else {
            responseDTO.setMessage("Enach rejected");
            bpEnach.setStatus(BPEnachEnum.applicationStatus.REJECTED.toString());
            bpEnach.setNachStatus(BPEnachEnum.applicationStatus.REJECTED.toString());
        }
        bpEnachDao.save(bpEnach);
        return responseDTO;
    }


    public String fetchBankCode(String ifscCode, String mode) {
        LendingNachBank lendingNachBank = lendingNachBankDao.findByIfscAndMode(ifscCode, mode);
        return lendingNachBank != null ? lendingNachBank.getBankCode() : null;
    }


    public ResponseDTO setEnachSkipStatus(Merchant merchant, String referenceNumber) {
        BpEnach bpEnach = bpEnachDao.findByMerchantIdAndReferenceNumber(merchant.getId(), referenceNumber);
        BpEnachSkip bpEnachSkip = bpEnachSkipDao.findByMerchantIdAndReferenceNumber(merchant.getId(), referenceNumber);
        if (bpEnachSkip == null) {
            return new ResponseDTO(false, "Loan Application not found", null);
        }

        bpEnachSkip.setSkip(true);
        bpEnachSkip.setMerchantId(merchant.getId());
        bpEnachSkip.setMerchantStoreId(bpEnach.getMerchantStoreId());
        bpEnachSkip.setReferenceNumber(referenceNumber);
        bpEnachSkipDao.save(bpEnachSkip);
        return new ResponseDTO(true, null, null);

    }


    public String getBranchName(String ifscCode) {
        String branch = null;
        List<IfscNew> bankList = ifscNewDao.findByIfsc(ifscCode);
        if (bankList != null && bankList.size() > 0) {
            branch = bankList.get(0).getBranch();
        }
        return branch;
    }

    public void registerNach(Map requestParams, Long merchantId, String clientName) {
        logger.info("Registering Nach for merchant:{}", merchantId);
        try {
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), getSecret(clientName));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("hash", hash);
            headers.set("clientName", clientName);

            HttpEntity<Map> request = new HttpEntity<>(requestParams, headers);
            logger.info("URL: {} request: {} ", BPNACH_REGISTER_URL, objectMapper.writeValueAsString(request));
            ResponseEntity<Object> response = restTemplate.exchange(BPNACH_REGISTER_URL, HttpMethod.POST, request, Object.class);
            if (response.getStatusCode().equals(HttpStatus.OK) && "200".equalsIgnoreCase((objectMapper.convertValue(response.getBody(), Map.class)).get("statusCode").toString())) {
                logger.info("Nach register successful for merchant:{}", merchantId);
            } else {
                logger.info("Nach register Failed for merchant:{}", merchantId);
            }
        } catch (Exception e) {
            logger.error("Exception in nach register api---", e);
        }
    }

    private String getSecret(String clientName) {
        InternalClient client = internalClientDao.findByClientName(clientName);
        if (client != null) {
            return aesEncryption.decrypt(client.getSecret());
        }
        return "";
    }
}



