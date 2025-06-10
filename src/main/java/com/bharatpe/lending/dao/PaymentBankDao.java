package com.bharatpe.lending.dao;

public interface PaymentBankDao extends JpaRepository<PaymentBank, Long> {

    // Define any custom query methods if needed
    // For example:
    // List<PaymentBank> findByBankName(String bankName);

    // You can also use Spring Data JPA's derived query methods
    // or define custom queries using @Query annotation.
}
