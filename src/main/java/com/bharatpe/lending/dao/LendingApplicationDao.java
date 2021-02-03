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
public interface LendingApplicationDao extends CrudRepository<LendingApplication , Long> {
	
	LendingApplication findByIdAndMerchantAndStatus(Long id, Merchant merchant, String status);

	LendingApplication findByIdAndMerchant(Long id, Merchant merchant);

	@Query(value = "select * from lending_application where merchant_id= :merchantId and loan_type= :loanType and status!= :status order by id desc limit 1", nativeQuery = true)
	LendingApplication findByMerchantIdAndLoanTypeAndNotStatus(Long merchantId, String loanType, String status);

	@Query(value = "select * from lending_application where merchant_id= :merchantId and status = :status and merchant_id is not null and status is not null order by id desc limit 1", nativeQuery = true)
	LendingApplication findByMerchantIdAndStatus(Long merchantId, String status);

	@Query(value = "select * from lending_application where merchant_id= :merchantId and loan_type!= :loanType and status!= :status order by id desc limit 1", nativeQuery = true)
	LendingApplication findByMerchantIdAndNotLoanTypeAndNotStatus(Long merchantId, String loanType, String status);

	LendingApplication findTop1ByMerchantOrderByIdDesc(Merchant merchant);

	List<LendingApplication> fetchLatestOpenApplication(Merchant merchant);

	@Query(value = "select * from lending_application where merchant_id=?1 and status in ('draft','pending_verification') order by id desc limit 1", nativeQuery = true)
	LendingApplication getLatestPendingApplication(Long merchantId);

	@Transactional
	@Modifying
	@Query(value="UPDATE lending_application SET manual_kyc=:status where id=:applicationId",nativeQuery = true)
	void updateApplicationManualKyc(String status, Long applicationId);
	
	
	@Query(value="select * from lending_application where external_loan_id=:externalLoanId and nbfc_id=:nbfcId and status=:status and disbursal_partner='BHARATPE'", nativeQuery = true)
	LendingApplication findByExternalLoanIdNbfcIdAndStatus(String externalLoanId, String nbfcId,String status);
	
	List<LendingPaymentSchedule> findByMerchant(Merchant merchant);
	
	@Query(value="select count(*) from lending_application where created_at between :startDate and :endDate and lender='LDC'", nativeQuery = true)
	Long getLDCApplicationCountBetweenDate(Date startDate,Date endDate);

	@Query(value = "select l.* from lending_application l left join lending_bank_disburse lb on l.id=lb.order_id, lending_ldc_borrower ld left join ecollect_transaction e on ld.account_number=e.virtual_account_number where l.merchant_id=ld.merchant_id and lender='LDC' and disbursal_partner='BHARATPE' and loan_disbursal_status='PROCESSING' and lb.status is null", nativeQuery = true)
	List<LendingApplication> getApplications();

	@Query(value="select * from lending_application where id=:id and nbfc_id=:nbfcId and status='approved' and lender in ('LDC', 'MAMTA') and loan_disbursal_status='PENDING' and disbursal_partner='BHARATPE'", nativeQuery = true)
	LendingApplication findByIdAndNbfcId(Long id, String nbfcId);

	@Query(value="select * from lending_application where merchant_id=:merchantId  order by id desc limit 1", nativeQuery = true)
	LendingApplication findBymerchantId(Long merchantId);

	@Query(value="select * from lending_application where merchant_id=:merchantId and loan_type='NTB' and status='approved' and loan_disbursal_status='DISBURSED' order by id desc limit 1", nativeQuery = true)
	LendingApplication getPreviousNTBLoan(Long merchantId);

	@Query(value="select * from lending_application where id=:id and merchant_id=:merchantId order by id desc limit 1", nativeQuery = true)
	LendingApplication findByIdAndMerchantId(Long id, Long merchantId);

	LendingApplication findTop1ByMerchantAndStatusOrderByIdDesc(Merchant merchant, String status);

	@Query(value="select * from lending_application where id=:id and status='approved' and lender='MAMTA' and loan_disbursal_status='PENDING' and disbursal_partner='BHARATPE'", nativeQuery = true)
	LendingApplication findByMamtaLoan(Long id);

	@Transactional
	@Modifying
	@Query(value = "DELETE FROM lending_application e where e.merchant_id = ?1", nativeQuery = true)
	int deleteByMerchantId(Long merchantId);
}
