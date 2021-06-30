package com.bharatpe.lending.dao;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.BankList;

@Repository
public interface BankListDao extends CrudRepository<BankList, Long> {

	@Query(value="SELECT * FROM bank_list WHERE ifsc = :ifsc and is_payment_bank = 1",nativeQuery = true)
	List<BankList> fetchNonPaymentBankList(String ifsc);

	BankList findByBankCode(String bankCode);

	@Query(value="SELECT distinct b.* FROM bank_list b, lending_nach_bank l where b.ifsc=l.ifsc and l.active=1",nativeQuery = true)
	List<BankList> findNachBankList();
	
}
