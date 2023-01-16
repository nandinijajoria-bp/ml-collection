package com.bharatpe.lending.dao;

import com.bharatpe.lending.common.enums.PincodeColor;
import com.bharatpe.lending.entity.LoanDowngradeConfigEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoanDowngradeConfigDao extends JpaRepository<LoanDowngradeConfigEntity, Long> {

    List<LoanDowngradeConfigEntity> findByRiskSegmentAndRiskGroupAndColorAndVersion(String riskSegment, String riskGroup, PincodeColor color, Sort sort, Double version);

}
