package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.BPEnachRawRequest;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface BPEnachRawRequestDao extends CrudRepository<BPEnachRawRequest,Long> {

    List<BPEnachRawRequest> findByMerchantIdAndAndReferenceNumberAndAndApiName(Long merchantId,String referenceNumber,String apiName);
}
