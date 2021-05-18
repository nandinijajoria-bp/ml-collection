package com.bharatpe.lending.dao;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.LendingCategories;

@Repository
public interface LendingCategoryDao extends CrudRepository<LendingCategories, Long>{

	List<LendingCategories> findByStatus(String status);

	@Query(value = "select l from LendingCategories l where l.masterCategory=?1 and l.status='ACTIVE' and l.loanConstruct='CONSTRUCT_1'")
	List<LendingCategories> getByMasterCategoryForConstruct1(String category);

	@Query(value = "select l from LendingCategories l where l.masterCategory=?1 and l.status='ACTIVE' and l.loanConstruct='CONSTRUCT_3' and l.payableConverter in ?2")
	List<LendingCategories> getByMasterCategoryForConstruct3(String category, List<String> payableConverters);

	@Query(value = "select l from LendingCategories l where l.category=?1 and l.status='ACTIVE'")
	LendingCategories getByCategory(String category);

	@Query(value = "select l from LendingCategories l where l.bureau=?1 and l.status='ACTIVE'")
	List<LendingCategories> findByBureau(String bureau);

	@Query(value = "select l from LendingCategories l where l.masterCategory=?1 and l.payableDays=?2 and l.status='ACTIVE'")
	LendingCategories getByMasterCategoryAndPayableDays(String masterCategory, int payableDays);
}
