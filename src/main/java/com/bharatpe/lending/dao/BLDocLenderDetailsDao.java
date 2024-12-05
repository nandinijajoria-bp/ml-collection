package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.BLDocLenderDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BLDocLenderDetailsDao extends JpaRepository<BLDocLenderDetails, Long> {
    @Query(nativeQuery = true, value = "SELECT * FROM bl_doc_lender_details WHERE lender=:lender AND doc_status=:docStatus AND mandatory_for_bl=:mandatoryForBl")
    List<BLDocLenderDetails> findMandatoryActiveDocsForLender(String lender, String docStatus, Boolean mandatoryForBl);
}
