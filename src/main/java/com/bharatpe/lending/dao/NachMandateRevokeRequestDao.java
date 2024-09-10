package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.NachMandateRevokeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NachMandateRevokeRequestDao extends JpaRepository<NachMandateRevokeRequest, Long> {
}
