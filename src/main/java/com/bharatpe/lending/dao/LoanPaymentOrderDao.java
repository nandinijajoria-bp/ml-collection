package com.bharatpe.lending.dao;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.bharatpe.lending.entity.LoanPaymentOrder;

import java.util.List;

public interface LoanPaymentOrderDao extends CrudRepository<LoanPaymentOrder, Long>{

	public LoanPaymentOrder findByOrderId(String orderId);


	@Query(nativeQuery = true, value = "select * from loan_payment_order where owner_id=:ownerId and merchant_id=:merchantId order by id desc")
	List<LoanPaymentOrder> findByOwnerIdAndMerchantId(String ownerId, Long merchantId);
}
