package com.bharatpe.lending.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.lending.common.query.dao.ExperianDaoSlave;
import com.bharatpe.lending.common.query.dao.LendingPincodesQueryDao;
import com.bharatpe.lending.common.query.entity.ExperianSlave;
import com.bharatpe.lending.common.query.entity.LendingPincodesQuery;
import com.bharatpe.lending.dao.LenderLanguageMappingDao;
import com.bharatpe.lending.dto.LanguageMappingDto;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LenderDocLanguageMap;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV2.dto.KfsDto;
import com.bharatpe.lending.loanV3.enums.piramal.DocumentLanguageMap;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LanguageService {

    @Autowired
    ExperianDaoSlave experianDaoSlave;

    @Autowired
    LendingPincodesQueryDao lendingPincodesQueryDao;

    @Autowired
    public LenderLanguageMappingDao languageMappingDao;

    @Autowired
    public LendingCache lendingCache;

    private final Object lock = new Object();

    public static final String LANGUAGE_PREFIX = "LI_lender_language_";

    private static final String LANG_LABEL_CACHE_KEY = "LI_Lender_lang_cache";

    @Value("${vernacular.doc.lender.list}")
    String  vernacularDocLanguageList;


    @Value("${language.mapping.cache.days:2}")
    public int languageMappingCacheDays;

    private List<LanguageMappingDto> loadLanguageMappings() {
        List<LenderLanguageMappingDao.LanguageMappings> mappings = languageMappingDao.findAllLenderLanguageList();
        return mappings.stream()
                .map(m -> new LanguageMappingDto(m.getLender(), m.getLanguageLabel(), m.getLanguageValue(), m.getVernacCode(), m.getDocType()))
                .collect(Collectors.toList());
    }

    private void storeInCache(String cacheKey, List<LanguageMappingDto> data) {
        log.info("Storing language list in cache with key {}: {}", cacheKey, data);

        AddCacheDto addCacheDto = new AddCacheDto();
        addCacheDto.setKey(cacheKey);
        addCacheDto.setTtl(languageMappingCacheDays);
        addCacheDto.setValue(data);
        lendingCache.add(addCacheDto, TimeUnit.DAYS);

    }

    private Map<String, String> storeLanguageMappingInCache(List<LanguageMappingDto> data) {
        log.info("Storing language list in cache with key {}: {}", LANG_LABEL_CACHE_KEY, data);

        Map<String, String> languageCache = new HashMap<>();
        for (LanguageMappingDto dto : data) {
            if (!ObjectUtils.isEmpty(dto.getLanguageValue())) {
                languageCache.put(dto.getLanguageValue(), dto.getLanguageLabel());
                languageCache.put(dto.getLanguageLabel(), dto.getLanguageLabel());
            }
        }

        // Store the language cache map for fast lookups
        AddCacheDto languageCacheDto = new AddCacheDto();
        languageCacheDto.setKey(LANG_LABEL_CACHE_KEY);
        languageCacheDto.setTtl(languageMappingCacheDays);
        languageCacheDto.setValue(languageCache);
        lendingCache.add(languageCacheDto, TimeUnit.DAYS);

        return languageCache;
    }

    private List<LanguageMappingDto> getFromCache(String cacheKey) {
        Object cachedValue = lendingCache.get(cacheKey);
        if (!ObjectUtils.isEmpty(cachedValue)) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                List<LanguageMappingDto> mappings = objectMapper.convertValue(
                        cachedValue,
                        new TypeReference<List<LanguageMappingDto>>() {}
                );
                log.info("Cache hit for key {}: {}", cacheKey, mappings);
                return mappings;
            } catch (IllegalArgumentException e) {
                log.error("Error deserializing cache data for key {}: {}", cacheKey, e.getMessage(), e);
            }
        } else {
            log.info("Cache miss for key: {}", cacheKey);
        }
        return Collections.emptyList();
    }

    private Map<String, String> getLanguageCacheFromCache() {
        Object cachedValue = lendingCache.get(LANG_LABEL_CACHE_KEY);
        if (!ObjectUtils.isEmpty(cachedValue)) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.convertValue(cachedValue, new TypeReference<Map<String, String>>() {});
            } catch (IllegalArgumentException e) {
                log.error("Error deserializing language cache for key {}: {}", LANG_LABEL_CACHE_KEY, e.getMessage(), e);
            }
        } else {
            log.info("Cache miss for language cache key: {}", LANG_LABEL_CACHE_KEY);
        }
        return Collections.emptyMap();
    }

    public KfsDto.LanguageData getOrSetLanguageMapping(String lender, Long applicationId) {
        try {
            synchronized (lock) {
                List<LanguageMappingDto> cachedMappings = getFromCache(LANGUAGE_PREFIX + lender);
                Map<String, List<KfsDto.DocumentLanguageInfo>> languageList;
                if (ObjectUtils.isEmpty(cachedMappings)) {
                    log.info("Cache miss: loading language cache for lender: {}. applicationId: {}", lender, applicationId);
                    cachedMappings = loadLanguageMappings();
                    storeInCache(LANGUAGE_PREFIX + lender, cachedMappings);
                    storeLanguageMappingInCache(cachedMappings);
                }

                if (ObjectUtils.isEmpty(cachedMappings)) {
                    return KfsDto.LanguageData.builder().languageList(Collections.emptyMap()).build();
                }

                languageList = cachedMappings.stream()
                        .filter(m -> lender.equalsIgnoreCase(m.getLender()))
                        .filter(m -> !ObjectUtils.isEmpty(m.getDocType()))
                        .collect(Collectors.groupingBy(
                                LanguageMappingDto::getDocType,
                                Collectors.mapping(dto -> KfsDto.DocumentLanguageInfo.builder()
                                        .lender(dto.getLender())
                                        .languageLabel(dto.getLanguageLabel())
                                        .languageValue(dto.getLanguageValue())
                                        .vernacCode(dto.getVernacCode())
                                        .build(), Collectors.toList()
                                )
                        ));

                return KfsDto.LanguageData.builder().languageList(languageList).build();
            }


        } catch (Exception e) {
            log.error("Error in getOrSetLanguageMapping for lender {}, applicationId: {}, error: {}", lender, applicationId, e.getMessage(), e);
        }

        return KfsDto.LanguageData.builder().languageList(Collections.emptyMap()).build();
    }

    public String getOrSetLanguageMappingByLenderAndLang(String lender, Long applicationId, String lang) {
        try {
            String languageMappings = findLangMapping(lang);
            log.info("fetched mapping from cache: {}", languageMappings);
            if (!ObjectUtils.isEmpty(languageMappings))
                return languageMappings;
            log.info("Cache miss: loading language cache for lender: {}. applicationId: {}", lender, applicationId);
            List<LanguageMappingDto> cachedMappings = loadLanguageMappings();
            return storeLanguageMappingInCache(cachedMappings).get(lang);
        } catch (Exception e) {
            log.error("Error fetching language cache for lender {}, applicationId: {}, error: {}", lender, applicationId, e.getMessage(), e);
        }
        return "";
    }

    private String findLangMapping(String lang) {
       Map<String, String> languageCache = getLanguageCacheFromCache();
            if(!languageCache.isEmpty()){
                return languageCache.get(lang);
            }
        return "";
    }

    public String getDocLanguage(long merchantId, String lender, boolean specialChar)
    {
        String language="";
        try {
            ExperianSlave experian = experianDaoSlave.getByMerchantId(merchantId);
            if (!ObjectUtils.isEmpty(experian)) {
                LendingPincodesQuery lendingPincodes = lendingPincodesQueryDao.findByPincode(experian.getPincode());
                if (!ObjectUtils.isEmpty(lendingPincodes)) {
                    language= DocumentLanguageMap.getDocumentLanguage(lendingPincodes.getState()).name();
                    language = LenderDocLanguageMap.fetchSupportedLanguageByLender(lender,language);
                    if (!ObjectUtils.isEmpty(language)) {
                        log.info("doc language for merchantId  {} with given pinCode  {} is : {}", merchantId, lendingPincodes.getPincode(), language);
                        language = specialChar ? "_" + language : language;
                    }
                }
            }
        }
        catch(Exception e)
        {
            log.error("Exception while fetching language for merchantId: {} ,{}",merchantId ,Arrays.asList(e.getStackTrace()));
        }
        return language;
    }


    public String getVernacLanguage(String lender, String loanType, Long merchantId) {
        String language="";
        boolean vernacularDocLanguageDisabled = false;
        if (Arrays.asList(Lender.ABFL.name(), Lender.PIRAMAL.name()).contains(lender) && LoanType.TOPUP.name().equalsIgnoreCase(loanType)) {
            vernacularDocLanguageDisabled = true;
        }

        if (vernacularDocLanguageList.contains(lender) && !vernacularDocLanguageDisabled) {
            language = getDocLanguage(merchantId, lender, true);
        }
        return language;
    }

}
