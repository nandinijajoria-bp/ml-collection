package com.bharatpe.lending.dao;

import java.util.Date;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bharatpe.common.entities.SettlementSchedule;

@Repository
@Transactional
public interface SettlementScheduleDao extends CrudRepository<SettlementSchedule, Long>{
	
	@Modifying
	@Query(value="UPDATE settlement_schedule SET settlement_date=:settlementDate, move_daily=:type WHERE merchant_id=:merchantId AND status=:status", nativeQuery=true)
	int updateSettlementDateAndMoveDaily(Date settlementDate, String type, String status, Long merchantId);
	
	SettlementSchedule findTop1ByMerchantIdAndStatus(Long merchantId, String status);
}
