package com.bharatpe.lending.dao;

import com.bharatpe.lending.common.enums.PincodeColor;
import com.bharatpe.lending.entity.LoanDowngradeConfigEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanDowngradeConfigDao extends JpaRepository<LoanDowngradeConfigEntity, Long> {

    LoanDowngradeConfigEntity findTop1ByRiskSegmentAndRiskGroupAndColorAndTenureAndVersion(String riskSegment, String riskGroup, PincodeColor color, Integer tenure, Double version);

}
