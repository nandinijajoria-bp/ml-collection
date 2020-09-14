package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.common.entity.BpEnach;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BPEnachDao extends CrudRepository<BpEnach, Long> {

    BpEnach findByIdAndMerchantIdAndStatus(Long Id,Long merchantId,String status);

    BpEnach findTop1ByMerchantIdAndReferenceNumber(Long merchantId,String referenceNumber);
}
