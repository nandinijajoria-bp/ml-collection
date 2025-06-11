package com.bharatpe.lending.dao;

import com.bharatpe.lending.common.entity.PaymentBank;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentBankDao extends JpaRepository<PaymentBank, Long> {

    // Define any custom query methods if needed
    // For example:
    // List<PaymentBank> findByBankName(String bankName);

    // You can also use Spring Data JPA's derived query methods
    // or define custom queries using @Query annotation.
}
