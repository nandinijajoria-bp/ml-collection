package com.bharatpe.lending.service;


import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.BharatPeEnachDao;
import com.bharatpe.lending.common.entity.BharatPeEnach;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.ResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class FosService {
    private Logger logger = LoggerFactory.getLogger(FosService.class);

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    BharatPeEnachDao bharatPeEnachDao;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    public ResponseDTO fosLoan(Long merchantId) {
        ResponseDTO responseDTO = new ResponseDTO(true, null, null);
        Map<String,Object> data= new HashMap<>();
        data.put("rejected",Boolean.FALSE);
        data.put("merchantId",merchantId.toString());
        data.put("activeLoan",Boolean.FALSE);
        data.put("eligible",Boolean.FALSE);
        data.put("experian",Boolean.TRUE);
        data.put("applicationPending",Boolean.FALSE);
        try{
            Experian experian = experianDao.getByMerchantId(merchantId);
            if(experian == null){
                data.put("message","Merchant Experian Not Pulled");
                data.put("experian",Boolean.FALSE);
                responseDTO.setData(data);
                return  responseDTO;
            }
            String reason = experian.getReason();
            if("ENACH".equalsIgnoreCase(reason)){
                reason = "Merchant Bank A/C does Not Allow Enach.";
            }else if("OGL".equalsIgnoreCase(reason)){
                reason = "Entered PIN Code Area is not Serviceable right now.";
            }else{
                reason = "Keep Transacting With BharatPe To Become Eligible.";
            }

            if(experian.getRejected()){
                data.put("message",reason);
                data.put("rejected",Boolean.TRUE);
                data.put("eligible",Boolean.FALSE);
                responseDTO.setData(data);
                return responseDTO;
            }

            if(experian.getReason() != null){
                data.put("message",reason);
                data.put("rejected",Boolean.TRUE);
                responseDTO.setData(data);
                return  responseDTO;
            }
            EligibleLoan eligibleLoan=eligibleLoanDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            LendingApplication lendingApplication=lendingApplicationDao.findBymerchantId(merchantId);
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId,"ACTIVE");
            logger.info("Payment Schedule:{}",lendingPaymentSchedule);
            if(lendingPaymentSchedule != null){
                data.put("message","Merchant Has a Active Loan.");
                data.put("activeLoan",Boolean.TRUE);
                responseDTO.setData(data);
                return responseDTO;
            }
            if(eligibleLoan == null){
                data.put("message","Merchant Not Eligible For Loan.");
                data.put("eligible",Boolean.FALSE);
                responseDTO.setData(data);
                return responseDTO;
            }
            if(lendingApplication == null){
                data.put("message","Merchant is Eligible For Loan.");
                data.put("eligible",Boolean.TRUE);
                responseDTO.setData(data);
                return responseDTO;
            }else{
                data.put("applicationPending",Boolean.TRUE);
                data.put("applicationRejected",Boolean.FALSE);
                data.put("eligible",Boolean.TRUE);
                data.put("nachRequired",Boolean.FALSE);
                data.put("created_at",lendingApplication.getCreatedAt().toString());
                data.put("loanType",lendingApplication.getLoanType());
                data.put("loanAmount",lendingApplication.getLoanAmount());
                data.put("loanId",lendingApplication.getExternalLoanId());
                data.put("nachStatus", "APPROVED".equals(lendingApplication.getNachStatus()) ? "APPROVED" : "PENDING");
                String loanType = lendingApplication.getLoanType();

                if("draft".equals(lendingApplication.getStatus())){
                    data.put("message","Application Is Draft Mode.");
                    responseDTO.setData(data);
                    return  responseDTO;
                }

                if("approved".equals(lendingApplication.getStatus())){
                    data.put("message","Merchant Application Is Approved State.");
                    data.put("agreement_at",lendingApplication.getAgreementAt().toString());
                    responseDTO.setData(data);
                    return  responseDTO;
                }

                if("pending_verification".equals(lendingApplication.getStatus())){
                    data.put("message","Merchant Loan Application Is Pending Verification State.");
                    data.put("agreement_at",lendingApplication.getAgreementAt().toString());
                    if(("NTB".equals(loanType) || "OGL".equals(loanType) || "BHARAT_SWIPE".equals(loanType)) && !"APPROVED".equals(lendingApplication.getNachStatus())){
                        data.put("message","Please Complete Enach For Further Process Application.");
                        data.put("nachRequired",Boolean.TRUE);
                    }
                    responseDTO.setData(data);
                    return  responseDTO;
                }else{
                    data.put("message","Merchant Loan Application Is Rejected State.");
                    data.put("applicationPending", Boolean.FALSE);
                    data.put("applicationRejected",Boolean.TRUE);
                    responseDTO.setData(data);
                    return  responseDTO;
                }
            }
        }catch(Exception ex){
            logger.error("Error Fos Loan Details API", ex);
            return responseDTO;
        }
    }

    public ResponseDTO fosnewLoan(Long merchantId) {
        ResponseDTO responseDTO = new ResponseDTO(true, null, null);
        Map<String,Object> data= new HashMap<>();
        Map<String,Object> loanData= new HashMap<>();
        Map<String,Object> details= new HashMap<>();
        try{
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId,"ACTIVE");
            if(lendingPaymentSchedule != null){
                loanData.put("activeLoan",Boolean.TRUE);
                loanData.put("eligible",Boolean.FALSE);
                loanData.put("color","#02a758");
                loanData.put("header","Loan Already Active");
                loanData.put("message","Merchant Already have an Active Loan.");
                data.put("loan_data",loanData);
                data.put("task_enable",Boolean.FALSE);
                responseDTO.setData(data);
                return responseDTO;
            }
            Experian experian = experianDao.getByMerchantId(merchantId);
            if(experian == null){
                loanData.put("experian",Boolean.FALSE);
                loanData.put("eligible",Boolean.FALSE);
                loanData.put("color","#eaa003");
                loanData.put("header","Merchant Not Eligible");
                loanData.put("message","Please Ask Merchant to Enter PAN/PIN");
                data.put("loan_data",loanData);
                data.put("task_enable",Boolean.TRUE);
                responseDTO.setData(data);
                return  responseDTO;
            }
            String reason = experian.getReason();
            if("ENACH".equalsIgnoreCase(reason)){
                reason = "Merchant Bank A/C does Not Allow Enach.";
            }else if("OGL".equalsIgnoreCase(reason)){
                reason = "Entered PIN Code is not Serviceable right now";
            }else if("LOW_TPV".equalsIgnoreCase(reason)){
                reason = "Keep Transacting With BharatPe To Become Eligible";
            }
            if(experian.getRejected() || experian.getReason() != null){
                loanData.put("experian",Boolean.TRUE);
                loanData.put("eligible",Boolean.FALSE);
                loanData.put("color","#ed6a5b");
                loanData.put("header","Merchant Not Eligible");
                loanData.put("message",reason);
                data.put("loan_data",loanData);
                data.put("task_enable",Boolean.FALSE);
                responseDTO.setData(data);
                return responseDTO;
            }
            EligibleLoan eligibleLoan=eligibleLoanDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            LendingApplication lendingApplication=lendingApplicationDao.findBymerchantId(merchantId);
            if(eligibleLoan == null){
                loanData.put("eligible",Boolean.FALSE);
                loanData.put("color","#ed6a5b");
                loanData.put("header","Merchant Not Eligible");
                loanData.put("message","Merchant Not Eligible For Loan.");
                data.put("loan_data",loanData);
                data.put("task_enable",Boolean.FALSE);
                responseDTO.setData(data);
                return responseDTO;
            }
            if(eligibleLoan != null && lendingApplication == null){
                loanData.put("eligible",Boolean.TRUE);
                loanData.put("applicationPending",Boolean.FALSE);
                loanData.put("color","#02a758");
                loanData.put("header","");
                loanData.put("message","Merchant is Eligible For Loan.");
                data.put("loan_data",loanData);
                data.put("task_enable",Boolean.TRUE);
                responseDTO.setData(data);
                return responseDTO;
            }
            if(lendingApplication != null){
                MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId,"ACTIVE");
                logger.info("Merchant bank Detais",merchantBankDetail);
                if("draft".equals(lendingApplication.getStatus())){
                    loanData.put("applicationPending",Boolean.TRUE);
                    loanData.put("loan_applied",Boolean.TRUE);
                    loanData.put("color","#02a758");
                    loanData.put("header","Application is in Draft State.");
                    loanData.put("message","Please Complete Application.");
                    data.put("loan_data",loanData);
                    data.put("task_enable",Boolean.TRUE);
                    responseDTO.setData(data);
                    return  responseDTO;
                }
                if("approved".equals(lendingApplication.getStatus())){
                    loanData.put("applicationPending",Boolean.FALSE);
                    loanData.put("loan_applied",Boolean.TRUE);
                    loanData.put("color","#02a758");
                    loanData.put("header","Merchant Application is in approved state.");
                    loanData.put("message","It will take 7-10 days for disbursal process.");
                    data.put("loan_data",loanData);
                    details.put("external_loan_id",lendingApplication.getExternalLoanId());
                    details.put("loan_amount",lendingApplication.getLoanAmount());
                    details.put("beneficiary_name",lendingApplication.getMerchant().getBeneficiaryName());
                    details.put("bank_name",merchantBankDetail.getBankName());
                    details.put("account_number",merchantBankDetail.getAccountNumber());
                    details.put("ifsc_code",merchantBankDetail.getIfscCode());
                    details.put("application_id",lendingApplication.getId());
                    data.put("details",details);
                    data.put("task_enable",Boolean.FALSE);
                    responseDTO.setData(data);
                    return  responseDTO;
                }
                if("pending_verification".equals(lendingApplication.getStatus())){
                    loanData.put("applicationPending",Boolean.FALSE);
                    loanData.put("limited_cpv_required",Boolean.FALSE);
                    loanData.put("header","Loan applied Succesfully");
                    loanData.put("message","Merchant Loan Application Is in Pending Verification State.");
                    data.put("task_enable",Boolean.FALSE);
                    loanData.put("agreement_at",lendingApplication.getAgreementAt().toString());
                    if("REGULAR".equalsIgnoreCase(lendingApplication.getLoanType()) && lendingApplication.getLoanAmount()>50000){
                        loanData.put("nachStatus","NotRequired");
                        loanData.put("header","Loan applied Succesfully");
                        loanData.put("message","Merchant Loan Application Is in Pending Verification State.");
                        data.put("task_enable",Boolean.FALSE);
                    }else{
                        if(!"APPROVED".equals(lendingApplication.getNachStatus())){
                            BharatPeEnach bharatPeEnachSkipped = bharatPeEnachDao.isSkipped(merchantId,lendingApplication.getId());
                            Long bharatPeEnach = bharatPeEnachDao.isFailed(merchantId,lendingApplication.getId());
                            if(bharatPeEnachSkipped == null && bharatPeEnach != null){
                                if(bharatPeEnach > 2){
                                    loanData.put("limited_cpv_required",Boolean.TRUE);
                                }
                            }
                            loanData.put("nachStatus","Pending");
                            loanData.put("header","Please Complete Enach For Further Process Application.");
                            loanData.put("message","Please get the NACH Form completed.");
                            data.put("task_enable",Boolean.TRUE);
                        }else{
                            loanData.put("nachStatus",lendingApplication.getNachStatus().toLowerCase());
                        }
                    }

                    data.put("loan_data",loanData);
                    details.put("external_loan_id",lendingApplication.getExternalLoanId());
                    details.put("loan_amount",lendingApplication.getLoanAmount());
                    details.put("beneficiary_name",lendingApplication.getMerchant().getBeneficiaryName());
                    details.put("bank_name",merchantBankDetail.getBankName());
                    details.put("account_number",merchantBankDetail.getAccountNumber());
                    details.put("ifsc_code",merchantBankDetail.getIfscCode());
                    details.put("application_id",lendingApplication.getId());
                    data.put("details",details);
                    responseDTO.setData(data);
                    return  responseDTO;
                }else{
                    loanData.put("applicationPending",Boolean.FALSE);
                    loanData.put("header","Merchant Loan Application Is in Rejected State.");
                    loanData.put("color","#565652");
                    loanData.put("message","Loan has been Rejected, Please apply again through app");
                    loanData.put("loan_applied", Boolean.FALSE);
                    loanData.put("applicationRejected",Boolean.TRUE);
                    details.put("external_loan_id",lendingApplication.getExternalLoanId());
                    details.put("loan_amount",lendingApplication.getLoanAmount());
                    details.put("beneficiary_name",lendingApplication.getMerchant().getBeneficiaryName());
                    details.put("bank_name",merchantBankDetail.getBankName());
                    details.put("account_number",merchantBankDetail.getAccountNumber());
                    details.put("ifsc_code",merchantBankDetail.getIfscCode());
                    details.put("application_id",lendingApplication.getId());
                    data.put("details",details);
                    data.put("loan_data",loanData);
                    data.put("task_enable",Boolean.TRUE);
                    responseDTO.setData(data);
                    return  responseDTO;
                }
            }else{
                return  responseDTO;
            }
        }catch(Exception ex){
            logger.error("Error Fos Loan Details API", ex);
            return responseDTO;
        }
    }

    public ResponseDTO fosUpdate(Map<String,Object> requestDTO){
        ResponseDTO responseDTO = new ResponseDTO();
        Long merchantId = Long.valueOf(requestDTO.get("merchant_id").toString());
        Long applicationId = Long.valueOf(requestDTO.get("application_id").toString());
        try{
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId,merchantId);
            if(lendingApplication == null){
                responseDTO.setSuccess(Boolean.FALSE);
                responseDTO.setMessage("Application Id And Merchant Id Not Validated");
                return  responseDTO;
            }
            if(!"APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus()) && !"approved".equalsIgnoreCase(lendingApplication.getStatus())){
                lendingApplication.setNachReferenceNumber(lendingApplication.getExternalLoanId());
                lendingApplication.setNachLender("BHARATPE");
                lendingApplication.setNachType("EXTERNAL");
                lendingApplication.setNachStatus("NOT_STARTED");
                lendingApplicationDao.save(lendingApplication);
            }
            responseDTO.setMessage("Loan Application Updated Succesfully!");
            responseDTO.setSuccess(Boolean.TRUE);
        }catch(Exception ex){
            logger.info("Fos Update API Exception",ex);
            responseDTO.setMessage("Something Went Wrong!");
            responseDTO.setSuccess(Boolean.FALSE);
        }
        return  responseDTO;
    }
}
