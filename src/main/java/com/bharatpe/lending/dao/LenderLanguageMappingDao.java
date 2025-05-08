package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LanguageMapping;
import com.bharatpe.lending.entity.LenderLanguageMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LenderLanguageMappingDao extends JpaRepository<LenderLanguageMapping, Long> {
    LanguageMapping findTop1ById(long id);

    List<LenderLanguageMapping> findByLender(String lender);

    @Query(nativeQuery = true, value = "SELECT distinct lm.lender as lender, lm_mapping.language_label as languageLabel, lm_mapping.language_value as languageValue, lm_mapping.vernac_code as vernacCode, lm.doc_type as docType FROM lender_language_mapping lm JOIN language_mapping lm_mapping ON lm.language_id = lm_mapping.id WHERE lm.lender=:lender")
    List<LanguageMappings> findLanguageListByLender(String lender);

    @Query(nativeQuery = true, value = "SELECT distinct lm.lender as lender, lm_mapping.language_label as languageLabel, lm_mapping.language_value as languageValue, lm_mapping.vernac_code as vernacCode, lm.doc_type as docType FROM lender_language_mapping lm JOIN language_mapping lm_mapping ON lm.language_id = lm_mapping.id")
    List<LanguageMappings> findAllLenderLanguageList();

    interface LanguageMappings{
        String getLender();
        String getLanguageLabel();
        String getLanguageValue();
        String getVernacCode();
        String getDocType();
    }

    @Query(nativeQuery = true, value = "SELECT distinct lm.lender as lender FROM lender_language_mapping lm JOIN language_mapping lm_mapping ON lm.language_id = lm_mapping.id")
    List<LanguageLenders> findDistinctLenders();

    interface LanguageLenders{
        String getLender();
    }
}


