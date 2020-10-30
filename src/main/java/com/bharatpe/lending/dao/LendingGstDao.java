package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.LendingGstDetail;
import org.springframework.data.repository.CrudRepository;

public interface LendingGstDao extends CrudRepository<LendingGstDetail, Long> {
    LendingGstDetail findByApplicationId(Long applicationId);

    LendingGstDetail findByTop1ByMerchantIdOrderByIdDesc(Long applicationId);
}
