package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.MerchantAggregateData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantAggregateDataDao extends JpaRepository<MerchantAggregateData, Long> {

    MerchantAggregateData findByMerchantIdAndAggregateId(Long merchantId, String aggregateId);
}
