package com.bharatpe.lending.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.LendingCategories;

@Repository
public interface LendingCategoryDao extends CrudRepository<LendingCategories, Long>{
	List<LendingCategories> findByCategory(String category);
}
