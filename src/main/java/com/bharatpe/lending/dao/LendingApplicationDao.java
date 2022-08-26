package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Repository
public interface LendingApplicationDao extends CrudRepository<LendingApplication , Long> {
	
	LendingApplication findByIdAndMerchantIdAndStatus(Long id, Long merchantId, String status);

	@Query(value = "select * from lending_application where merchant_id= :merchantId and loan_type= :loanType and status!= :status order by id desc limit 1", nativeQuery = true)
	LendingApplication findByMerchantIdAndLoanTypeAndNotStatus(Long merchantId, String loanType, String status);

	@Query(value = "select * from lending_application where merchant_id= :merchantId and status = :status and merchant_id is not null and status is not null order by id desc limit 1", nativeQuery = true)
	LendingApplication findByMerchantIdAndStatus(Long merchantId, String status);

	@Query(value = "select * from lending_application where merchant_id= :merchantId and id=:applicationId and status = :status and status is not null order by id desc limit 1", nativeQuery = true)
	LendingApplication findByMerchantIdAndApplicationIdAndStatus(Long merchantId,Long applicationId, String status);

	@Query(value = "select * from lending_application where merchant_id= :merchantId and loan_type!= :loanType and status!= :status order by id desc limit 1", nativeQuery = true)
	LendingApplication findByMerchantIdAndNotLoanTypeAndNotStatus(Long merchantId, String loanType, String status);

//	LendingApplication findTop1ByMerchantOrderByIdDesc(Merchant merchant);

	@Query(value = "SELECT * FROM lending_application WHERE  merchant_id = :merchantId AND status NOT IN ('closed','deleted') ORDER BY id DESC", nativeQuery = true)
	List<LendingApplication> fetchLatestOpenApplication(Long merchantId);

	@Query(value = "select * from lending_application where merchant_id=?1 and status in ('draft','pending_verification','approved') and disburse_timestamp is null order by id desc limit 1", nativeQuery = true)
	LendingApplication getLatestPendingApplication(Long merchantId);

	@Transactional
	@Modifying
	@Query(value="UPDATE lending_application SET manual_kyc=:status where id=:applicationId",nativeQuery = true)
	void updateApplicationManualKyc(String status, Long applicationId);
	
	
	@Query(value="select * from lending_application where external_loan_id=:externalLoanId and nbfc_id=:nbfcId and status=:status and disbursal_partner='BHARATPE'", nativeQuery = true)
	LendingApplication findByExternalLoanIdNbfcIdAndStatus(String externalLoanId, String nbfcId,String status);
	
//	List<LendingPaymentSchedule> findByMerchant(Merchant merchant);
	
	@Query(value="select count(*) from lending_application where created_at between :startDate and :endDate and lender='LDC'", nativeQuery = true)
	Long getLDCApplicationCountBetweenDate(Date startDate,Date endDate);

	@Query(value = "select * from lending_application where loan_disbursal_status='PROCESSING' and nbfc_send_date>='2021-02-18'", nativeQuery = true)
	List<LendingApplication> getApplications();

	@Query(value="select * from lending_application where id=:id and nbfc_id=:nbfcId and status='approved' and loan_disbursal_status='PENDING' and disbursal_partner='BHARATPE'", nativeQuery = true)
	LendingApplication findByIdAndNbfcId(Long id, String nbfcId);

	@Query(value="select * from lending_application where merchant_id=:merchantId and disburse_timestamp is null and status not IN ('deleted','closed') order by id desc limit 1", nativeQuery = true)
	LendingApplication findBymerchantId(Long merchantId);

	@Query(value="select * from lending_application where merchant_id=:merchantId and loan_type='NTB' and status='approved' and loan_disbursal_status='DISBURSED' order by id desc limit 1", nativeQuery = true)
	LendingApplication getPreviousNTBLoan(Long merchantId);

	@Query(value="select * from lending_application where id=:id and merchant_id=:merchantId order by id desc limit 1", nativeQuery = true)
	LendingApplication findByIdAndMerchantId(Long id, Long merchantId);

//	LendingApplication findTop1ByMerchantAndStatusOrderByIdDesc(Merchant merchant, String status);

	@Query(value="select * from lending_application where id=:id and status='approved' and loan_disbursal_status='PENDING' and disbursal_partner='BHARATPE'", nativeQuery = true)
	LendingApplication findByMamtaLoan(Long id);

	@Transactional
	@Modifying
	@Query(value = "delete from lending_application where merchant_id = :merchantId", nativeQuery = true)
	int deleteByMerchantId(Long merchantId);

	@Query(value="select * from lending_application where merchant_id=:merchantId and status != 'deleted' and (loan_disbursal_status not in ('REJECTED','DISBURSED') or loan_disbursal_status is null) order by id desc limit 1", nativeQuery = true)
	LendingApplication findTopByMerchantIdAndLoanDisbursalStatusNullOrderByIdDesc(Long merchantId);

	@Query(value="select * from lending_application where merchant_id=:merchantId and status != 'deleted' and (loan_disbursal_status not in ('REJECTED','DISBURSED') or loan_disbursal_status is null) and created_at > :createdAt order by id desc limit 1", nativeQuery = true)
	LendingApplication findTopByMerchantIdAndLoanDisbursalStatusNullAndPaymentScheduleStatusClosedOrderByIdDesc(Long merchantId, Date createdAt);

	@Query(nativeQuery = true, value = "SELECT * FROM lending_application WHERE  merchant_id = :merchantId AND status IN ('pending_verification','approved') and disburse_timestamp is null limit 1")
	LendingApplication findOpenApplication(Long merchantId);

	@Query(nativeQuery = true, value = "SELECT * FROM lending_application WHERE  merchant_id = :merchantId AND status IN ('pending_verification','approved', 'rejected') order by id desc limit 1")
	LendingApplication findApplicableApplication(Long merchantId);

	@Query(value="select * from lending_application where merchant_id=:merchantId and status='approved' AND send_to_nbfc='YES' AND NBFC_send_date is not null AND disburse_timestamp is null", nativeQuery = true)
	LendingApplication findPendingDisbursal(Long merchantId);

	@Query(nativeQuery = true, value = "select * from lending_application where external_loan_id =:externalLoanId limit 1")
	LendingApplication findByExternalLoanId(String externalLoanId);

	@Transactional
	@Modifying
	@Query(value="UPDATE lending_application SET ckyc_id=:kycId where id=:applicationId and merchant_id=:merchantId",nativeQuery = true)
	void updateKycId(Long applicationId, String kycId, Long merchantId);

    @Query(value="select * from lending_application where merchant_id=:merchantId and status='approved' and loan_disbursal_status='DISBURSED' order by id desc limit 1", nativeQuery = true)
    LendingApplication getLastDisbursedLoan(Long merchantId);

	@Query(value="select * from lending_application where merchant_id=:merchantId and created_at>=:createdAt and status != 'deleted' and (loan_disbursal_status not in ('REJECTED','DISBURSED') or loan_disbursal_status is null) order by id desc limit 1", nativeQuery = true)
	LendingApplication getRepeatLoanApplication(Long merchantId, Date createdAt);

	LendingApplication findTop1ByMerchantIdOrderByIdDesc(Long merchantId);

	LendingApplication findTop1ByMerchantIdAndStatusOrderByIdDesc(Long merchant, String status);

/*
	@Query(value = "select * from lending_application where merchant_id= :merchantId and status != 'deleted' order by id desc limit 1", nativeQuery = true)
*/
	LendingApplication findTop1ByMerchantIdAndStatusNotOrderByIdDesc(Long merchantId, String status);
}
