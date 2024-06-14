package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.LendingPancard;
import com.bharatpe.lending.entity.LendingPancardDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LendingPancardDetailsDao extends JpaRepository<LendingPancardDetails, Long> {

    LendingPancardDetails findTop1ByMerchantIdOrderByIdDesc(Long merchantId);

}
