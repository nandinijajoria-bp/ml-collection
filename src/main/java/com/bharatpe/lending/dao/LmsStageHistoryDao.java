package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LmsStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LmsStageHistoryDao extends JpaRepository<LmsStageHistory, Long> {

    List<LmsStageHistory> findTop2ByLendingApplicationIdOrderByIdDesc(Long lendingApplicationId);
}
