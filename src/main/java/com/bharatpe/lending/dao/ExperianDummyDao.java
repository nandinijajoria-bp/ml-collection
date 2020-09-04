package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.ExperianDummy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExperianDummyDao extends JpaRepository<ExperianDummy, Long> {
}
