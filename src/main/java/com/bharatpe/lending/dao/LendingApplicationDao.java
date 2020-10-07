package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.Merchant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Repository
@Transactional
public interface LendingApplicationDao extends CrudRepository<LendingApplication , Long> {
	
	LendingApplication findByIdAndMerchantAndStatus(Long id, Merchant merchant, String status);

	LendingApplication findByIdAndMerchant(Long id, Merchant merchant);

	@Query(value = "select * from lending_application where merchant_id= :merchantId and loan_type= :loanType and status!= :status order by id desc limit 1", nativeQuery = true)
	LendingApplication findByMerchantIdAndLoanTypeAndNotStatus(Long merchantId, String loanType, String status);

	@Query(value = "select * from lending_application where merchant_id= :merchantId and loan_type!= :loanType and status!= :status order by id desc limit 1", nativeQuery = true)
	LendingApplication findByMerchantIdAndNotLoanTypeAndNotStatus(Long merchantId, String loanType, String status);

	LendingApplication findTop1ByMerchantOrderByIdDesc(Merchant merchant);

	List<LendingApplication> fetchLatestOpenApplication(Merchant merchant);

	@Query(value = "select * from lending_application where merchant_id=?1 and status='pending_verification' order by id desc limit 1", nativeQuery = true)
	LendingApplication getLatestPendingApplication(Long merchantId);
	
	@Modifying
	@Query(value="UPDATE lending_application SET manual_kyc=:status where id=:applicationId",nativeQuery = true)
	void updateApplicationManualKyc(String status, Long applicationId);
	
	
	@Query(value="select * from lending_application where external_loan_id=:externalLoanId and nbfc_id=:nbfcId and status=:status and disbursal_partner='BHARATPE'", nativeQuery = true)
	LendingApplication findByExternalLoanIdNbfcIdAndStatus(String externalLoanId, String nbfcId,String status);
	
	List<LendingPaymentSchedule> findByMerchant(Merchant merchant);
	
	@Query(value="select count(*) from lending_application where created_at between :startDate and :endDate and lender='LDC'", nativeQuery = true)
	Long getLDCApplicationCountBetweenDate(Date startDate,Date endDate);

	@Query(value = "select * from lending_application where loan_type='NTB' and loan_disbursal_status='DISBURSED' LIMIT :offset, 1000", nativeQuery = true)
	List<LendingApplication> getApplications(long offset);

	@Query(value="select * from lending_application where id=:id and nbfc_id=:nbfcId and status='approved' and lender='LDC' and loan_disbursal_status='PENDING' and disbursal_partner='BHARATPE'", nativeQuery = true)
	LendingApplication findByIdAndNbfcId(Long id, String nbfcId);
}
