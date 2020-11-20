package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.common.entity.BpEnach;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BPEnachDao extends CrudRepository<BpEnach, Long> {

    BpEnach findByIdAndMerchantIdAndStatus(Long Id,Long merchantId,String status);

    BpEnach findTop1ByMerchantIdAndReferenceNumber(Long merchantId,String referenceNumber);

    @Query(value = "select * from merchant_nach_details where merchant_id=:merchantId and status='APPROVED' and internal_nach_type='ENACH' order by id desc limit 1", nativeQuery = true)
    BpEnach findSuccessEnach(Long merchantId);
}
