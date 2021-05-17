package com.bharatpe.lending.dao;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.bharatpe.lending.entity.LoanPaymentOrder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface LoanPaymentOrderDao extends CrudRepository<LoanPaymentOrder, Long>{

	public LoanPaymentOrder findByOrderId(String orderId);


	@Query(nativeQuery = true, value = "select * from loan_payment_order where owner_id=:ownerId and merchant_id=:merchantId order by id desc")
	List<LoanPaymentOrder> findByOwnerIdAndMerchantId(String ownerId, Long merchantId);

	@Query(nativeQuery = true, value = "select * from loan_payment_order where owner_id=:ownerId and merchant_id=:merchantId and source=:source order by id desc limit 1")
	LoanPaymentOrder findByOwnerIdAndMerchantIdAndSource(Long ownerId, Long merchantId, String source);

	@Modifying(
			clearAutomatically = true,
			flushAutomatically = true
	)
	@Transactional
	@Query(nativeQuery = true, value="UPDATE loan_payment_order l set l.status=:status where l.id=:id and l.status='PENDING'")
	int updateStatusForPendingTxn(String status, Long id);
}
