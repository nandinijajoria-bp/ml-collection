package com.bharatpe.lending.dao;

import com.bharatpe.lending.common.entity.BpEnachSkip;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BPEnachSkipDao extends CrudRepository<BpEnachSkip,Long> {

    BpEnachSkip findByMerchantIdAndReferenceNumber(Long merchantId,String referenceNumber);
}
