package com.bharatpe.lending.service.impl;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.dto.ExperianResponseDTO;
import com.bharatpe.lending.dto.InsertExperianRequestDTO;
import com.bharatpe.lending.dto.UpdateExperianRequestDTO;
import com.bharatpe.lending.service.IExperianService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Optional;

@Slf4j
@Service
public class ExperianServiceImpl implements IExperianService {

    @Autowired
    ExperianDao experianDao;

    @Override
    public ExperianResponseDTO findByMerchantId(Long merchantId) {

        log.info("findByMerchantId for merchantId : {} ", merchantId);

        Experian experian = experianDao.getByMerchantId(merchantId);

        return ExperianResponseDTO.from(experian);
    }

    @Override
    public ExperianResponseDTO updateExperian(UpdateExperianRequestDTO updateExperianRequestDTO) {

        final Optional<Experian> experianOptional = experianDao.findById(updateExperianRequestDTO.getId());

        if (!experianOptional.isPresent())
            return null;

        Experian experian = experianOptional.get();

        if (!ObjectUtils.isEmpty(updateExperianRequestDTO.getPancardNumber())) {
            experian.setPancardNumber(updateExperianRequestDTO.getPancardNumber());
        }

        if (!ObjectUtils.isEmpty(updateExperianRequestDTO.getPincode())) {
            experian.setPincode(updateExperianRequestDTO.getPincode());
        }

        experianDao.saveAndFlush(experian);

        return ExperianResponseDTO.from(experian);
    }

    @Override
    public ExperianResponseDTO insertExperian(InsertExperianRequestDTO insertExperianRequestDTO) {

        final Experian experian = saveInExperian(insertExperianRequestDTO);

        return ExperianResponseDTO.from(experian);
    }

    private Experian saveInExperian(InsertExperianRequestDTO insertExperianRequestDTO) {

        Experian experian = new Experian();
        experian.setMerchantId(insertExperianRequestDTO.getMerchantId());
        experian.setIp(insertExperianRequestDTO.getIp());
        experian.setLatitude(insertExperianRequestDTO.getLatitude());
        experian.setLongitude(insertExperianRequestDTO.getLongitude());
        experian.setResponse(insertExperianRequestDTO.getResponse());
        experian.setMerchantName(insertExperianRequestDTO.getMerchantName());
        experian.setEmail(insertExperianRequestDTO.getEmail());
        experian.setRejected(ObjectUtils.isEmpty(insertExperianRequestDTO.getRejected()) ? false : insertExperianRequestDTO.getRejected());
        experian.setReason(insertExperianRequestDTO.getReason());
        experian.setRequestedLoanAmount(insertExperianRequestDTO.getRequestedLoanAmount());
        experian.setPancardNumber(insertExperianRequestDTO.getPancardNumber());
        experian.setTnc(ObjectUtils.isEmpty(insertExperianRequestDTO.getTnc()) ? true : insertExperianRequestDTO.getTnc());
        experian.setBpScore(insertExperianRequestDTO.getBpScore());
        experian.setExperianScore(insertExperianRequestDTO.getExperianScore());
        experian.setCategory(insertExperianRequestDTO.getCategory());
        experian.setColor(insertExperianRequestDTO.getColor());
        experian.setRetryCount(ObjectUtils.isEmpty(insertExperianRequestDTO.getRetryCount()) ? 0 : insertExperianRequestDTO.getRetryCount());
        experian.setSkip(ObjectUtils.isEmpty(insertExperianRequestDTO.isSkip()) ? false : insertExperianRequestDTO.isSkip());
        experian.setPincode(insertExperianRequestDTO.getPincode());
        experian.setRejectedDate(insertExperianRequestDTO.getRejectedDate());
        experian.setReportDate(insertExperianRequestDTO.getReportDate());
        experian.setEligibleAmount(insertExperianRequestDTO.getEligibleAmount());
        experian.setEligibleTenure(insertExperianRequestDTO.getEligibleTenure());
        experian.setLoanType(insertExperianRequestDTO.getLoanType());
        experian.setSource(ObjectUtils.isEmpty(insertExperianRequestDTO.getSource()) ? "LOAN" : insertExperianRequestDTO.getSource());
        experian.setBureau(insertExperianRequestDTO.getBureau());
        experian.setHitId(insertExperianRequestDTO.getHitId());

        experianDao.saveAndFlush(experian);

        return experian;
    }
}
