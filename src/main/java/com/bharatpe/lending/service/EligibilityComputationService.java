package com.bharatpe.lending.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.loanV2.dto.LoanDetailsRequest;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EligibilityComputationService {

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    MerchantService merchantService;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * Extracting values(merchant_id, pin, pan) from file and storing them in redis
     */
    @Async
    public void extractDataAndAddInRedis(InputStream byteArrayInputStream) {
        try {
            BufferedReader bulkContactFileReader = new BufferedReader(new InputStreamReader(byteArrayInputStream));
            String readLine = bulkContactFileReader.readLine(); //skipping the file headers
            List<String> merchantDetails = new ArrayList<>();
            while((readLine = bulkContactFileReader.readLine()) != null) {
                List<String> values = Arrays.stream(readLine.split(",")).map(String::trim).collect(Collectors.toList());
                if (values.size() < 3) continue;
                if (values.stream().anyMatch(v  -> "".equalsIgnoreCase(v))) continue;
                merchantDetails.add(values.stream().collect(Collectors.joining(",")));
            }

            if(ObjectUtils.isEmpty(merchantDetails)) {
                log.info("No data to insert into redis");
                return;
            }

            Instant startTime = Instant.now();
            addInRedis(merchantDetails);
            Instant endTime = Instant.now();
            log.info("Time Taken to upload all merchantId to redis : {} milliseconds", Duration.between(startTime, endTime).toMillis());
        } catch (IOException e) {
            e.printStackTrace();
            log.info("something went wrong !! {}", e.getMessage());
        }
    }

    /**
     * Computing global limit for each merchant in sublist.
     */
    public void processBatch(List<Object> subList) {
        List<Future> futureList = new ArrayList<>();
        for (Object obj : subList) {
            if (Objects.isNull(obj)) {
                continue;
            }
            String[] merchantInfo = ((String) obj).split(",");
            log.info("Starting processing eligibility computation for: {}", Arrays.toString(merchantInfo));
            try {
                LoanDetailsRequest loanDetailsRequest = new LoanDetailsRequest();
                loanDetailsRequest.setIOS(false);
                loanDetailsRequest.setPancard(merchantInfo[1]);
                loanDetailsRequest.setPincode(merchantInfo[2]);
                loanDetailsRequest.setAppVersion(318);
                final Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(Long.parseLong(merchantInfo[0]));
                BasicDetailsDto merchant;
                if (!basicDetailsDto.isPresent())
                    continue;
                merchant = basicDetailsDto.get();
                futureList.add(executorService.submit(() -> loanDetailsServiceV2.getLoanDetails(loanDetailsRequest, merchant, null)));
            } catch (Exception e) {
                log.error("something went wrong while processing batch {}", e.getMessage());
            }
        }
        futureList.forEach(future -> {
            try {
                log.info("Processed Record : {}", future.get(20, TimeUnit.SECONDS));
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                log.error("Error processing Record : {}, {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Adding merchant_id, pin, pan separated with comma in redis set.
     *
     * @param sublist
     */
    private void addInRedis(List<String> sublist) {
        String key = LendingConstants.COMPUTE_GLOBAL_LIMIT;
        for (String detail : sublist) {
            lendingCache.addValue(key, detail);
        }
        log.info("successfully added values: {} in redis", sublist);
    }

}