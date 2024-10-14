package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingOfferModificationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LendingOfferModificationSnapshotDao extends JpaRepository<LendingOfferModificationSnapshot, Long> {
}
