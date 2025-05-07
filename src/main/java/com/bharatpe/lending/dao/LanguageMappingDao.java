package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LanguageMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LanguageMappingDao extends JpaRepository<LanguageMapping, Long> {
    LanguageMapping findTop1ById(long id);
    LanguageMapping findTop1ByLanguageValue(String languageValue);
    LanguageMapping findTop1ByLanguageLabel(String languagelabel);
}
