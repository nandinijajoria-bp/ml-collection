package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingBlockedPancard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LendingBlockedPancardDao extends JpaRepository<LendingBlockedPancard, String> {

    LendingBlockedPancard findByPancard(String pancard);
}
