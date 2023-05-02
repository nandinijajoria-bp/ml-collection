package com.bharatpe.lending.dao;

import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.entity.LenderAssignmentRules;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LenderAssignmentRulesDao extends JpaRepository<LenderAssignmentRules, Long> {

    List<LenderAssignmentRules> findByIsDefaultAndIsActive(Boolean isDefault, Boolean isActive);

    @Query(value = "select * from lender_assignment_rules where min_amount<=:amount and max_amount>=:amount and min_bureau_score<=:bureauScore and max_bureau_score>=:bureauScore and loan_type like :loanType and tenure like :tenure and risk_group like :riskGroupLike and pincode_color like :pincodeColor and is_default = false and is_active = true", nativeQuery = true)
    List<LenderAssignmentRules> fetchEligibleRules(Double amount, Double bureauScore, String loanType, String tenure, String riskGroupLike, String pincodeColor);

    List<LenderAssignmentRules> findByIsActive(Boolean isActive);

    @Query(value = "select * from lender_assignment_rules where min_amount<=:amount and max_amount>=:amount and min_bureau_score<=:bureauScore and max_bureau_score>=:bureauScore and loan_type like :loanType and tenure like :tenure and risk_group like :riskGroupLike and pincode_color like :pincodeColor and is_default = false and is_active = false", nativeQuery = true)
    List<LenderAssignmentRules> fetchEligibleRulesForInternal(Double amount, Double bureauScore, String loanType, String tenure, String riskGroupLike, String pincodeColor);


}
