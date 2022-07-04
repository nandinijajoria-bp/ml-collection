package com.bharatpe.lending.service.impl;

import com.bharatpe.lending.common.dao.LendingPullPaymentDao;
import com.bharatpe.lending.common.entity.LendingPullPayment;
import com.bharatpe.lending.dto.LendingPullPaymentResponseDTO;
import com.bharatpe.lending.dto.UpdateLendingPullPaymentDto;
import com.bharatpe.lending.service.ILendingPullPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class LendingPullPaymentServiceImpl implements ILendingPullPaymentService {

    @Autowired
    LendingPullPaymentDao lendingPullPaymentDao;


    @Override
    public LendingPullPaymentResponseDTO getLendingPullPaymentByMerchantIdAndMode(Long merchantId, String mode) {

        log.info("get LendingPullPayment for merchantId : {} and mode : {}", merchantId, mode);

        LendingPullPayment lendingPullPayment =
          lendingPullPaymentDao.findTop1ByMerchantIdAndModeOrderByIdDesc(merchantId, mode);

        if (ObjectUtils.isEmpty(lendingPullPayment)) {
            return null;
        }

        return LendingPullPaymentResponseDTO.from(lendingPullPayment);
    }

    @Override
    public LendingPullPaymentResponseDTO getLendingPullPaymentByMerchantIdAndModeAndOwnerId(Long merchantId, String mode,
                                                                                 Long ownerId) {

        log.info("get LendingPullPayment for merchantId : {} ownerId : {} and mode : {}", merchantId, ownerId, mode);

        LendingPullPayment lendingPullPayment =
          lendingPullPaymentDao.findTop1ByMerchantIdAndOwnerIdAndModeOrderByIdDesc(merchantId, ownerId, mode);
        if (ObjectUtils.isEmpty(lendingPullPayment)) {
            return null;
        }

        return LendingPullPaymentResponseDTO.from(lendingPullPayment);
    }

    @Override
    public List<LendingPullPaymentResponseDTO> getLendingPullPaymentWithDueAmountGreaterThan(Long merchantId, String mode,
                                                                                  Long ownerId, Double dueAmount) {

        log.info("get LendingPullPayment for merchantId : {} ownerId : {}  mode : {} and dueAmount : {}", merchantId, ownerId, mode, dueAmount);

        List<LendingPullPayment> lendingPullPayments = lendingPullPaymentDao.findByMerchantIdAndMerchantStoreIdAndModeAndDueAmountGreaterThan(merchantId, ownerId, mode, dueAmount);

        List<LendingPullPaymentResponseDTO> lendingPullPaymentResponseDTOList = new ArrayList<>();

        lendingPullPayments.forEach(lendingPullPayment -> lendingPullPaymentResponseDTOList.add(LendingPullPaymentResponseDTO.from(lendingPullPayment)));

        return lendingPullPaymentResponseDTOList;
    }

    @Override
    public LendingPullPaymentResponseDTO updateLendingPullPayment(UpdateLendingPullPaymentDto updateLendingPullPaymentDto) {

        Optional<LendingPullPayment> lendingPullPaymentOptional = lendingPullPaymentDao.findById(updateLendingPullPaymentDto.getId());

        if (!lendingPullPaymentOptional.isPresent()) {
            return null;
        }

        LendingPullPayment lendingPullPayment = lendingPullPaymentOptional.get();

        if(!ObjectUtils.isEmpty(updateLendingPullPaymentDto.getDeductedAmount())) {
            lendingPullPayment.setDeductedAmount(updateLendingPullPaymentDto.getDeductedAmount());
        }

        if(!ObjectUtils.isEmpty(updateLendingPullPaymentDto.getDueAmount())) {
            lendingPullPayment.setDueAmount(updateLendingPullPaymentDto.getDueAmount());
        }

        lendingPullPaymentDao.saveAndFlush(lendingPullPayment);

        return LendingPullPaymentResponseDTO.from(lendingPullPayment);
    }


}
