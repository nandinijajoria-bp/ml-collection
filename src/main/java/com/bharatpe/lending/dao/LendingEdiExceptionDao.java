package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.LendingEdiException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LendingEdiExceptionDao extends JpaRepository<LendingEdiException, Long> {
}
