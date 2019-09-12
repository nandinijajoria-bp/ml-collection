package com.bharatpe.lending.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.TmpLoanGenerate;

@Repository
public interface TmpLoanGenerateDao extends CrudRepository<TmpLoanGenerate, Long> {


}
