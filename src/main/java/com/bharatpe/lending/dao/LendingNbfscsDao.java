package com.bharatpe.lending.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.LendingNbfscs;

@Repository
public interface LendingNbfscsDao extends CrudRepository<LendingNbfscs , Long> {
}
