package com.bharatpe.lending.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.lending.entity.ExperianRawResponse;

@Repository
public interface ExperianRawResponseDao extends CrudRepository<ExperianRawResponse, Long>{

}
