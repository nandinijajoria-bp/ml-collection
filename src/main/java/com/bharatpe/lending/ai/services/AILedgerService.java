package com.bharatpe.lending.ai.services;


import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.ai.dto.LedgerApiResponse;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


@Service
@Slf4j
public class AILedgerService {

    private final RestTemplate restTemplate;
    private final String ledgerServiceHost = "";
    private final LendingPaymentScheduleDao lendingPaymentScheduleDao;
    private final LendingLedgerDao lendingLedgerDao;

    public AILedgerService(RestTemplate restTemplate, LendingPaymentScheduleDao lendingPaymentScheduleDao, LendingLedgerDao lendingLedgerDao) {
        this.restTemplate = restTemplate;
        this.lendingPaymentScheduleDao = lendingPaymentScheduleDao;
        this.lendingLedgerDao = lendingLedgerDao;
    }

    public LedgerApiResponse fetchLedger(Long merchantId, String token) {
        String url = "http://localhost:9091/ai/collection/ledger?merchantId=" + merchantId;

//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        headers.set("token", token);
//
//        HttpEntity<String> entity = new HttpEntity(headers);
//        try{
//            log.info("Fetching ledger for merchantId: {}", merchantId);
//
//            ResponseEntity<LedgerApiResponse> responseEntity =
//                    restTemplate.exchange(url, HttpMethod.GET, entity, LedgerApiResponse.class);
//            log.info("Received response from ledger service for merchantId: {}, is {}", merchantId, responseEntity);
//            LedgerApiResponse response = responseEntity.getBody();
//            if(response!=null && response.getData() !=null){
//                return null;
//            }
//        }
//        catch (RestClientException e){
//            log.error("Error while fetching ledger for merchantId: {}, error: {}", merchantId, e.getMessage());
//        }catch (Exception e){
//            log.error("Unknown error while fetching ledger for merchantId: {}, error: {}", merchantId, e.getMessage());
//        }

        List<LendingPaymentSchedule> lendingPaymentScheduleList = lendingPaymentScheduleDao.findAllLendingPaymentScheduleByMerchantId(merchantId);
        if (lendingPaymentScheduleList.isEmpty()) {
            log.info("No active loan found for merchantId: {}", merchantId);
            return new LedgerApiResponse();
        }
        lendingPaymentScheduleList = lendingPaymentScheduleList.stream()
                .sorted(Comparator.comparing(LendingPaymentSchedule::getCreatedAt).reversed())
                .collect(Collectors.toList());
        List<List<LendingLedger>> allLendingLedgerList = new ArrayList<>();
        for(LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
            List<LendingLedger> lendingLedgerList = lendingLedgerDao.findByLendingPaymentScheduleOrderByDateAsc(lendingPaymentSchedule.getId());
            if(!lendingLedgerList.isEmpty()){
                allLendingLedgerList.add(lendingLedgerList);
                break;
            }
        }
        if(allLendingLedgerList.isEmpty()){
            log.info("No ledger records found for merchantId: {}", merchantId);
            return new LedgerApiResponse();
        }
        log.info("Fetched loan application details for merchantId: {}, details are: {}", merchantId, allLendingLedgerList);
        List<List<LedgerApiResponse.LedgerData>> ledgerDataList = new ArrayList<>();
        for(List<LendingLedger> lendingLedgerList:allLendingLedgerList){
            List<LedgerApiResponse.LedgerData> ledgerDataSubList = new ArrayList<>();
            for(LendingLedger lendingLedger:lendingLedgerList){
                LedgerApiResponse.LedgerData ledgerData = mapToLedgerData(lendingLedger);
                ledgerDataSubList.add(ledgerData);
            }
            ledgerDataList.add(ledgerDataSubList);
        }
        return new LedgerApiResponse(true, "success", ledgerDataList);
    }

    public LedgerApiResponse.LedgerData mapToLedgerData(LendingLedger lendingLedger) {
        LedgerApiResponse.LedgerData ledgerData = new LedgerApiResponse.LedgerData();

        ledgerData.setId(lendingLedger.getId());
        ledgerData.setCreatedAt(lendingLedger.getCreatedAt());
        ledgerData.setUpdatedAt(lendingLedger.getUpdatedAt());
        ledgerData.setMerchantId(lendingLedger.getMerchantId());
        ledgerData.setTxnType(lendingLedger.getTxnType());
        ledgerData.setDate(lendingLedger.getDate());
        ledgerData.setAmount(lendingLedger.getAmount());
        ledgerData.setPrinciple(lendingLedger.getPrinciple());
        ledgerData.setInterest(lendingLedger.getInterest());
        ledgerData.setOtherCharges(lendingLedger.getOtherCharges());
        ledgerData.setPenalty(lendingLedger.getPenalty());
        ledgerData.setDescription(lendingLedger.getDescription());

        // Map LendingPaymentSchedule if needed
//        if (lendingLedger.getLendingPaymentSchedule() != null) {
//            LedgerApiResponse.LendingPaymentSchedule lendingPaymentSchedule = new LedgerApiResponse.LendingPaymentSchedule();
//            lendingPaymentSchedule.setId(lendingLedger.getLendingPaymentSchedule().getId());
//            lendingPaymentSchedule.setCreatedAt(lendingLedger.getLendingPaymentSchedule().getCreatedAt());
//            lendingPaymentSchedule.setUpdatedAt(lendingLedger.getLendingPaymentSchedule().getUpdatedAt());
//            lendingPaymentSchedule.setMerchantId(lendingLedger.getLendingPaymentSchedule().getMerchantId());
//            lendingPaymentSchedule.setLoanType(lendingLedger.getLendingPaymentSchedule().getLoanType());
//            lendingPaymentSchedule.setLoanAmount(lendingLedger.getLendingPaymentSchedule().getLoanAmount());
//            lendingPaymentSchedule.setEdiAmount(lendingLedger.getLendingPaymentSchedule().getEdiAmount());
//            lendingPaymentSchedule.setStartDate(lendingLedger.getLendingPaymentSchedule().getStartDate());
//            lendingPaymentSchedule.setEdiCount(lendingLedger.getLendingPaymentSchedule().getEdiCount());
//            lendingPaymentSchedule.setOverdueEdiCount(lendingLedger.getLendingPaymentSchedule().getOverdueEdiCount());
//            lendingPaymentSchedule.setOverdueAmount(lendingLedger.getLendingPaymentSchedule().getOverdueAmount());
//            lendingPaymentSchedule.setPaidAmount(lendingLedger.getLendingPaymentSchedule().getPaidAmount());
//            lendingPaymentSchedule.setDueAmount(lendingLedger.getLendingPaymentSchedule().getDueAmount());
//            lendingPaymentSchedule.setTotalPenaltyAmount(lendingLedger.getLendingPaymentSchedule().getTotalPenaltyAmount());
//            lendingPaymentSchedule.setStatus(lendingLedger.getLendingPaymentSchedule().getStatus());
//            lendingPaymentSchedule.setApplicationId(lendingLedger.getLendingPaymentSchedule().getApplicationId());
//            lendingPaymentSchedule.setTotalPayableAmount(lendingLedger.getLendingPaymentSchedule().getTotalPayableAmount());
//            lendingPaymentSchedule.setMobile(lendingLedger.getLendingPaymentSchedule().getMobile());
//            lendingPaymentSchedule.setNbfc(lendingLedger.getLendingPaymentSchedule().getNbfc());
//            lendingPaymentSchedule.setTentativeClosingDate(lendingLedger.getLendingPaymentSchedule().getTentativeClosingDate());
//            lendingPaymentSchedule.setInterest(lendingLedger.getLendingPaymentSchedule().getInterest());
//            lendingPaymentSchedule.setDuePrinciple(lendingLedger.getLendingPaymentSchedule().getDuePrinciple());
//            lendingPaymentSchedule.setDueInterest(lendingLedger.getLendingPaymentSchedule().getDueInterest());
//            lendingPaymentSchedule.setDuePenalty(lendingLedger.getLendingPaymentSchedule().getDuePenalty());
//
//            ledgerData.setLendingPaymentSchedule(lendingPaymentSchedule);
//        }

        return ledgerData;
    }
}
