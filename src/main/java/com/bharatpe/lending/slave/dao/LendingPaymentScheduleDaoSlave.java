package com.bharatpe.lending.slave.dao;

import com.bharatpe.lending.common.slave.entity.LendingPaymentScheduleSlave;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LendingPaymentScheduleDaoSlave extends CrudRepository<LendingPaymentScheduleSlave, Long> {

    @Query(value="SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId and merchant_store_id = :merchantStoreId and status=:status and credit_loan=false order by id", nativeQuery=true)
    List<LendingPaymentScheduleSlave> findByMerchantIdAndMerchantStoreIdAndStatus(Long merchantId, Long merchantStoreId, String status);

    @Query(value="SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId and status=:status and credit_loan=false order by id", nativeQuery=true)
    List<LendingPaymentScheduleSlave> findByMerchantIdAndStatusList(Long merchantId, String status);

    @Query(value="SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId and status=:status and credit_loan=false order by id limit 1",nativeQuery=true)
    LendingPaymentScheduleSlave findByMerchantIdAndStatus(Long merchantId, String status);
}
