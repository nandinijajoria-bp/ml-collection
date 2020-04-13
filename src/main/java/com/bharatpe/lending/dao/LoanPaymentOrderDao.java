package com.bharatpe.lending.dao;

import org.springframework.data.repository.CrudRepository;

import com.bharatpe.lending.entity.LoanPaymentOrder;

public interface LoanPaymentOrderDao extends CrudRepository<LoanPaymentOrder, Long>{

	public LoanPaymentOrder findByOrderId(String orderId);
	
}
