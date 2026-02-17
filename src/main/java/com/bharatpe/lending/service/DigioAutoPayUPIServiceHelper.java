package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.Constants.DeductionStatusEnum;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.constant.PaymentConstants;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.loanV3.revamp.dto.EnachStatusUpdateRequestDto;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.util.Objects;


@Slf4j
@Service
@RequiredArgsConstructor
public class DigioAutoPayUPIServiceHelper {

    private final AutoPayUPIDao autoPayUPIDao;

    private static final int DEFAULT_FREQUENCY_FOR_NEW_APPLICATIONS = 1;

    public AutoPayUPI submitDigioUpi(@NotNull LendingApplication lendingApplication, @NotNull ENachIntitiationResponseDTO.Data data){
        log.info("handling submit call for digio upi for merchant: {} and application: {}",
                lendingApplication.getMerchantId(), lendingApplication.getId());

        EnachStatusUpdateRequestDto enachStatusUpdateRequestDto = new EnachStatusUpdateRequestDto(
                lendingApplication.getId(), data.getMandate_id(), NachStatus.APPROVED.name(), null, null, data.getCustomerVpa(), data.getUmrn()
        );
        return updateAutoPayStatus(enachStatusUpdateRequestDto);
    }

    public AutoPayUPI submitDigioMigrationUpi(@NotNull LendingApplication lendingApplication,@NotNull ENachIntitiationResponseDTO.Data data){
        log.info("handling submit call for migration digio upi for merchant: {} and application: {}",
                lendingApplication.getMerchantId(), lendingApplication.getId());

        EnachStatusUpdateRequestDto enachStatusUpdateRequestDto = new EnachStatusUpdateRequestDto(
                lendingApplication.getId(), data.getMandate_id(), NachStatus.APPROVED.name(), null, null, data.getCustomerVpa(), data.getUmrn()
        );
        return updateAutoPayStatus(enachStatusUpdateRequestDto);
    }

    public AutoPayUPI registerDigioUpi(LendingApplication lendingApplication, @NotNull ENachIntitiationResponseDTO.Data enachData){
        log.info("handling register call for digio upi for merchant: {} and application: {}",
                lendingApplication.getMerchantId(), lendingApplication.getId());
        AutoPayUPI autoPayUPI = new AutoPayUPI();
        autoPayUPI.setAmount(1D);
        autoPayUPI.setMerchantId(lendingApplication.getMerchantId());
        autoPayUPI.setLender(lendingApplication.getLender());
        autoPayUPI.setApplicationId(lendingApplication.getId());
        autoPayUPI.setFrequency(DEFAULT_FREQUENCY_FOR_NEW_APPLICATIONS);
        autoPayUPI.setGateway("DIGIO");
        autoPayUPI.setIsAutoPayUpiDeduction(DeductionStatusEnum.AUTO_PAY_UPI.name());
        autoPayUPI.setMandateId(enachData.getMandate_id());
        autoPayUPI.setMandateEndDate(enachData.getMandateEndDate());
        autoPayUPI.setStatus(AutoPayStatusEnum.PENDING);
        autoPayUPIDao.save(autoPayUPI);
        return autoPayUPI;
    }

    public AutoPayUPI registerDigioMigrationUpi(LendingApplication lendingApplication, @NotNull ENachIntitiationResponseDTO.Data enachData, Double nachAmount){
        log.info("handling register call for digio migration upi for merchant: {} and application: {}",
                lendingApplication.getMerchantId(), lendingApplication.getId());
        AutoPayUPI autoPayUPI = new AutoPayUPI();
        autoPayUPI.setAmount(1D);
        autoPayUPI.setMerchantId(lendingApplication.getMerchantId());
        autoPayUPI.setLender(lendingApplication.getLender());
        autoPayUPI.setApplicationId(lendingApplication.getId());
        autoPayUPI.setFrequency(DEFAULT_FREQUENCY_FOR_NEW_APPLICATIONS);
        autoPayUPI.setGateway("DIGIO");
        autoPayUPI.setMandateId(enachData.getMandate_id());
        autoPayUPI.setStandaloneAutopaySetup(Constants.DISBURSED_LOAN.equals(lendingApplication.getLoanDisbursalStatus()));
//        autoPayUPI.setIsAutoPayUpiDeduction(Constants.DISBURSED_LOAN.equals(lendingApplication.getLoanDisbursalStatus())
//                ? DeductionStatusEnum.HARD_QR_DEDUCTION.name() : DeductionStatusEnum.AUTO_PAY_UPI.name());
        autoPayUPI.setIsAutoPayUpiDeduction(DeductionStatusEnum.AUTO_PAY_UPI.name());
        autoPayUPI.setMandateEndDate(enachData.getMandateEndDate());
        autoPayUPI.setStatus(AutoPayStatusEnum.PENDING);
        autoPayUPI.setMaxMandateAmount(nachAmount);
        autoPayUPIDao.save(autoPayUPI);
        return autoPayUPI;
    }

    public AutoPayUPI updateAutoPayStatus(@NotNull EnachStatusUpdateRequestDto requestDto){
        log.info("updating autopay status for application: {}, mandate: {} status are: {}",
                requestDto.getApplicationId(), requestDto.getMandateId(), requestDto.getStatus());
        if(Objects.isNull(requestDto.getApplicationId()) || Objects.isNull(requestDto.getMandateId()) || Objects.isNull(requestDto.getStatus())){
            return null;
        }
        AutoPayUPI autoPayUPI = autoPayUPIDao.findByApplicationIdAndMandateId(requestDto.getApplicationId(), requestDto.getMandateId());
        if(Objects.isNull(autoPayUPI)){
            log.info("entry not found in autopayupi table for application: {} and mandateId: {}",
                    requestDto.getApplicationId(), requestDto.getMandateId());
            return null;
        }
        AutoPayStatusEnum newAutoPayStatus = PaymentConstants.NACH_STATUS_AUTO_PAY_STATUS_ENUM_MAP.get(requestDto.getStatus());
        if(Objects.isNull(newAutoPayStatus)){
            log.error("got invalid status for application: {} and mandateId: {} status are: {}",
                    requestDto.getApplicationId(), requestDto.getMandateId(), requestDto.getStatus());
            return autoPayUPI;
        }
        if(PaymentConstants.AUTOPAY_TERMINAL_STATUS.contains(autoPayUPI.getStatus())){
            log.warn("mandate is already in terminal state for application: {} and mandateId: {} current status: {} received status: {}",
                    requestDto.getApplicationId(), requestDto.getMandateId(), autoPayUPI.getStatus(), newAutoPayStatus);
            return autoPayUPI;
        }
        // TODO we can remove inactive from here if we start marking active for timeout cases. inactive is marked when new mandate has been completed
        if((AutoPayStatusEnum.PENDING.equals(newAutoPayStatus) || AutoPayStatusEnum.INACTIVE.equals(newAutoPayStatus))
                && AutoPayStatusEnum.ACTIVE.equals(autoPayUPI.getStatus())){
            log.info("mandate is already active for application: {} and mandateId: {}, avoiding downgrade from ACTIVE to PENDING",
                    requestDto.getApplicationId(), requestDto.getMandateId());
            return autoPayUPI;
        }
        autoPayUPI.setStatus(newAutoPayStatus);
        if(!StringUtils.isEmpty(requestDto.getCustomerVpa())){
            autoPayUPI.setPayerVpa(requestDto.getCustomerVpa());
        }
        if(!StringUtils.isEmpty(requestDto.getUmrn())){
            autoPayUPI.setUmrn(requestDto.getUmrn());
        }
        autoPayUPIDao.save(autoPayUPI);
        log.info("updated autopayupi table for application: {} and mandateId: {} with status: {}",
                requestDto.getApplicationId(), requestDto.getMandateId(), newAutoPayStatus);
        return autoPayUPI;
    }

}
