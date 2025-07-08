package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.MerchantAggregateData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantAggregateDataDao extends MongoRepository<MerchantAggregateData, String> {

    MerchantAggregateData findByMerchantIdAndAggregateId(Long merchantId, String aggregateId);
}