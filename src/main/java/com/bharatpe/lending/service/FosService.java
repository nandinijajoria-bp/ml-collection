package com.bharatpe.lending.service;


import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.BharatPeEnachDao;
import com.bharatpe.lending.common.dao.LendingPennydropDao;
import com.bharatpe.lending.common.dao.LoanAttributionDao;
import com.bharatpe.lending.common.entity.BharatPeEnach;
import com.bharatpe.lending.common.entity.LendingPennydrop;
import com.bharatpe.lending.common.entity.LoanAttribution;
import com.bharatpe.lending.dao.BPEnachDao;
import com.bharatpe.lending.dao.BankListDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.FosAttributionRequestDTO;
import com.bharatpe.lending.dto.FosAttributionResponseDTO;
import com.bharatpe.lending.dto.FosResponseDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

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

    @Autowired
    LendingCpvDetailsDao lendingCpvDetailsDao;

    @Autowired
    IfscDao ifscDao;

    @Autowired
    BankListDao bankListDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingPennydropDao lendingPennydropDao;

    @Autowired
    DocumentsIdProofDao documentsIdProofDao;

    @Autowired
    DocKycDetailsDao docKycDetailsDao;

    @Autowired
    MerchantInferredLocationDao merchantInferredLocationDao;

    @Autowired
    MerchantDao merchantDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LoanAttributionDao loanAttributionDao;

    @Autowired
    BPEnachDao bpEnachDao;


    public ResponseDTO fosLoan(Long merchantId) {
        ResponseDTO responseDTO = new ResponseDTO(true, null, null,null);
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
                    if(("NTB".equals(loanType) || "OGL".equals(loanType) || "BHARAT_SWIPE".equals(loanType) || "NTB_SMS_1".equals(loanType)) && !"APPROVED".equals(lendingApplication.getNachStatus())){
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
        ResponseDTO responseDTO = new ResponseDTO(true, null, null,null);
        Map<String,Object> data= new HashMap<>();
        Map<String,Object> loanData= new HashMap<>();
        Map<String,Object> details= new HashMap<>();
        try{
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId,"ACTIVE");
            if(lendingPaymentSchedule != null){
                loanData.put("activeLoan",Boolean.TRUE);
                loanData.put("eligible",Boolean.FALSE);
                loanData.put("color","#ED6A5B");
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
                loanData.put("color","#EAA003");
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
            }else{
                reason = "Keep Transacting With BharatPe To Become Eligible";
            }
            if(experian.getRejected() || experian.getReason() != null){
                loanData.put("experian",Boolean.TRUE);
                loanData.put("eligible",Boolean.FALSE);
                loanData.put("color","#ED6A5B");
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
                loanData.put("color","#ED6A5B");
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
                loanData.put("color","#02A758");
                loanData.put("header","Merchant is Eligible For Loan.");
                loanData.put("message","Please Apply Loan On BharatPe Merchant App.");
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
                    loanData.put("eligible",Boolean.TRUE);
                    loanData.put("loan_applied",Boolean.TRUE);
                    loanData.put("color","#EAA003");
                    loanData.put("header","Application is in Draft State.");
                    loanData.put("message","Please Complete Application.");
                    data.put("loan_data",loanData);
                    data.put("task_enable",Boolean.TRUE);
                    responseDTO.setData(data);
                    return  responseDTO;
                }
                Ifsc ifsc = ifscDao.findTop1ByIfscOrderByIdDesc(merchantBankDetail.getIfscCode());
                BankList bankList = bankListDao.findByBankCode(merchantBankDetail.getBankCode());
                details.put("external_loan_id",lendingApplication.getExternalLoanId());
                details.put("loan_amount",lendingApplication.getLoanAmount());
                details.put("beneficiary_name",lendingApplication.getMerchant().getBeneficiaryName());
                details.put("account_type",merchantBankDetail.getAccType());
                details.put("bank_name",merchantBankDetail.getBankName());
                details.put("account_number",merchantBankDetail.getAccountNumber());
                details.put("ifsc_code",merchantBankDetail.getIfscCode());
                details.put("application_id",lendingApplication.getId());
                details.put("branch",ifsc != null ? ifsc.getBranch() : " ");
                details.put("bank_logo",bankList != null ? bankList.getImageUrl() : " ");
                data.put("details",details);
                if("approved".equals(lendingApplication.getStatus())){
                    loanData.put("applicationPending",Boolean.FALSE);
                    loanData.put("eligible",Boolean.TRUE);
                    loanData.put("loan_applied",Boolean.TRUE);
                    loanData.put("color","#02A758");
                    loanData.put("header","Merchant Application is in approved state.");
                    loanData.put("message","It will take 7-10 days for disbursal process.");
                    data.put("loan_data",loanData);
                    data.put("task_enable",Boolean.FALSE);
                    responseDTO.setData(data);
                    return  responseDTO;
                }
                if("pending_verification".equals(lendingApplication.getStatus())){
                    loanData.put("applicationPending",Boolean.FALSE);
                    loanData.put("limited_cpv_required",Boolean.FALSE);
                    loanData.put("eligible",Boolean.TRUE);
                    loanData.put("header","Loan applied Succesfully");
                    loanData.put("color","#02A758");
                    loanData.put("message","Merchant Loan Application Is in Pending Verification State.");
                    data.put("task_enable",Boolean.FALSE);
                    loanData.put("agreement_at",lendingApplication.getAgreementAt().toString());
                    if("REGULAR".equalsIgnoreCase(lendingApplication.getLoanType()) && lendingApplication.getLoanAmount()>50000){
                        loanData.put("nachStatus","NotRequired");
                        loanData.put("header","Loan applied Succesfully");
                        loanData.put("loan_applied",Boolean.TRUE);
                        loanData.put("message","Merchant Loan Application Is in Pending Verification State.");
                        data.put("task_enable",Boolean.FALSE);
                    }else{
                        if(!"APPROVED".equals(lendingApplication.getNachStatus()) && !"NOT_STARTED".equalsIgnoreCase(lendingApplication.getNachStatus())){
                            BharatPeEnach bharatPeEnachSkipped = bharatPeEnachDao.isSkipped(merchantId,lendingApplication.getId());
                            Long bharatPeEnach = bharatPeEnachDao.isFailed(merchantId,lendingApplication.getId());
                            BharatPeEnach bharatPeEnachFailed = bharatPeEnachDao.findSpecificError(merchantId,lendingApplication.getId());
                            if(bharatPeEnachSkipped == null && bharatPeEnach != null){
                                if(bharatPeEnach > 2 || bharatPeEnachFailed != null){
                                    loanData.put("limited_cpv_required",Boolean.TRUE);
                                }
                            }else if(bharatPeEnachSkipped != null){
                                loanData.put("limited_cpv_required",Boolean.TRUE);
                            }
                            loanData.put("nachStatus","Pending");
                            loanData.put("color","#EAA003");
                            loanData.put("header","Ask User To Complete eNACH");
                            loanData.put("loan_applied",Boolean.FALSE);
                            loanData.put("message","Go To Loan Section On BharatPe Merchant App To  Start eNACH.");
                            data.put("task_enable",Boolean.TRUE);
                        }else{
                            loanData.put("loan_applied",Boolean.TRUE);
                            loanData.put("nachStatus",lendingApplication.getNachStatus().toLowerCase());
                        }
                    }
                    data.put("loan_data",loanData);
                    responseDTO.setData(data);
                    return  responseDTO;
                }else{
                    loanData.put("applicationPending",Boolean.FALSE);
                    loanData.put("header","Merchant Loan Application Is in Rejected State.");
                    loanData.put("color","#565652");
                    loanData.put("message","Loan has been Rejected, Please apply again through app");
                    loanData.put("loan_applied", Boolean.FALSE);
                    loanData.put("eligible",Boolean.TRUE);
                    loanData.put("applicationRejected",Boolean.TRUE);
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
        Long merchantId = Long.valueOf(requestDTO.containsKey("merchant_id") ? requestDTO.get("merchant_id").toString() :"0");
        Long applicationId = Long.valueOf(requestDTO.containsKey("application_id") ? requestDTO.get("application_id").toString() : "0");
        Long cpvAgentId =Long.valueOf(requestDTO.containsKey("agent_id") ? requestDTO.get("agent_id").toString() : "0");
        String accType =requestDTO.containsKey("account_type") ? requestDTO.get("account_type").toString() : null;
        if(merchantId == 0 || applicationId == 0 || cpvAgentId == 0 || accType == null){
            responseDTO.setSuccess(Boolean.FALSE);
            responseDTO.setMessage("Require Parameter Missing In Request.");
            return  responseDTO;
        }
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
                lendingApplication.setPhysicalVerificationStatus("SUBMITTED");
                lendingApplication.setCpvAgentId(cpvAgentId);
                lendingApplication.setAssignedAt(new Date());
                lendingApplication.setCpvSubmitTimestamp(new Date());
                lendingApplicationDao.save(lendingApplication);

                apiGatewayService.fosAttribution(lendingApplication.getMerchant().getId(),"NTB_LOAN_V2","CLOSED");
            }
            BharatPeEnach bharatPeEnach = bharatPeEnachDao.findByMerchantIdAndApplicationId(merchantId,applicationId);
            if(bharatPeEnach != null && !bharatPeEnach.getSuccess()){
                bharatPeEnach.setSkip(Boolean.TRUE);
                LendingPennydrop lendingPennydrop = lendingPennydropDao.isFailed(lendingApplication.getMerchant().getId(), lendingApplication.getId());
                if (lendingPennydrop == null) {
                    apiGatewayService.updateApplicationPriority(lendingApplication.getMerchant().getId(), lendingApplication.getId());
                }
                bharatPeEnachDao.save(bharatPeEnach);
            }
            MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId,"ACTIVE");
            if(merchantBankDetail != null){
                merchantBankDetail.setAccType(accType);
                merchantBankDetailDao.save(merchantBankDetail);
            }
            LendingCpvDetails lendingCpvDetails =lendingCpvDetailsDao.findByMerchantIdAndApplicationIdAndAgentId(merchantId,applicationId,cpvAgentId);
            if(lendingCpvDetails != null){
                lendingCpvDetails.setAccountType(accType);
                lendingCpvDetailsDao.save(lendingCpvDetails);
            }
            responseDTO.setMessage("Loan Application Updated Successfully!");
            responseDTO.setSuccess(Boolean.TRUE);
        }catch(Exception ex){
            logger.info("Fos Update API Exception",ex);
            responseDTO.setMessage("Something Went Wrong!");
            responseDTO.setSuccess(Boolean.FALSE);
        }
        return  responseDTO;
    }

    public ResponseDTO getMerchantAddress(Long merchantId){
        ResponseDTO responseDTO = new ResponseDTO();

        Merchant merchant = merchantDao.findById(merchantId).get();

        try{
            Map<String, Object> addressResponse = new HashMap<>();
            LendingApplication lendingApplication = lendingApplicationDao.findApplicableApplication(merchant.getId());
            if(Objects.nonNull(lendingApplication)) {
                Object experianAddress = getExperianAddress(merchant);
                Object lendingAddress = getLendingAddress(lendingApplication);
                Object documentIdProofAddress = getDocumentIdProofAddress(lendingApplication);
                Object merchantInferredAddress = getMerchantInferredAddress(lendingApplication);

                addressResponse.put("experian_address", experianAddress);
                addressResponse.put("lending_address", lendingAddress);
                addressResponse.put("document_id_proof_address", documentIdProofAddress);
                addressResponse.put("merchant_inferred_address", merchantInferredAddress);

                responseDTO.setMessage("Address");
                responseDTO.setSuccess(Boolean.TRUE);
                responseDTO.setData(addressResponse);

                return responseDTO;
            }
        }catch (Exception ex){
            logger.error("Error while sending Address to fos App for Merchant: {} Error: {}", merchant.getId(),ex);
        }

        responseDTO.setMessage("Something Went Wrong!");
        responseDTO.setSuccess(Boolean.FALSE);
        return responseDTO;
    }


    public Object getExperianAddress(Merchant merchant){
//        Map<String, Object> experianAddress = new HashMap<>();
//        Experian experian = experianDao.getByMerchantId(merchant.getId());
//
//        JsonNode bureauResponse = parseStringResponse(experian.getResponse());;
//        if (bureauResponse == null) {
//            return new CreditScoreReportDetailDTO();
//        }
//        ResponseUtil responseUtil;
//        if ("CRIF".equalsIgnoreCase(experian.getBureau())) {
//            responseUtil = new CrifResponseUtil(experianDao);
//        } else {
//            responseUtil = new ExperianResponseUtil(experianDao);
//        }

        return null;
    }

    public Object getLendingAddress(LendingApplication lendingApplication){
        try {
            Map<String, Object> lendingAddress = new HashMap<>();

            String address = Objects.isNull(lendingApplication.getShopNumber()) ? "" : lendingApplication.getShopNumber()+ ",";
            address += Objects.isNull(lendingApplication.getStreetAddress()) ? "" : lendingApplication.getStreetAddress()+ ",";
            address += Objects.isNull(lendingApplication.getArea()) ? "" : lendingApplication.getArea()+ ",";
            address += Objects.isNull(lendingApplication.getLandmark()) ? "" : lendingApplication.getLandmark()+ ",";

            address += Objects.isNull(lendingApplication.getCity()) ? "" : lendingApplication.getCity()+ ",";
            address += Objects.isNull(lendingApplication.getState()) ? "" : lendingApplication.getState()+ ",";

            lendingAddress.put("address", address);
            lendingAddress.put("pincode", lendingApplication.getPincode());
            lendingAddress.put("lat", lendingApplication.getLatitude());
            lendingAddress.put("long", lendingApplication.getLongitude());

            return lendingAddress;
        }catch (Exception ex){
            logger.error("Error while sending LendingAddress to fos App for Application Id: {} Error: {}", lendingApplication.getId(),  ex);
        }

        return null;
    }


    public Object getDocumentIdProofAddress(LendingApplication lendingApplication){
        try {

            DocKycDetails docKycDetails =  docKycDetailsDao.getAadharAddress(lendingApplication.getId());
            logger.error("check for getDocumentIdProofAddress :{}", docKycDetails);
            if(Objects.nonNull(docKycDetails)) {
                Map<String, Object> docAddress = new HashMap<>();

                docAddress.put("pincode", docKycDetails.getPincode());
                docAddress.put("city", docKycDetails.getCity());
                docAddress.put("state", docKycDetails.getState());
                docAddress.put("address", docKycDetails.getAddress());
                docAddress.put("lat", docKycDetails.getDocumentsIdProof().getLatitude());
                docAddress.put("long", docKycDetails.getDocumentsIdProof().getLongitude());

                return docAddress;
            }
        }catch (Exception ex){
            logger.error("Error while sending DocumentIdProof to fos App for Application Id: {} Error: {}", lendingApplication.getId(),ex);
        }
        return null;
    }

    public Object getMerchantInferredAddress(LendingApplication lendingApplication){
        try {
            MerchantInferredLocation merchantInferredLocation = merchantInferredLocationDao.findTop1ByMerchantIdOrderByIdDesc(lendingApplication.getMerchant().getId());

            if(Objects.nonNull(merchantInferredLocation)){
                Map<String, Object> lendingAddress = new HashMap<>();

                String address = Objects.isNull(merchantInferredLocation.getAddress()) ? "" : merchantInferredLocation.getAddress()+ ", ";
                address += Objects.isNull(merchantInferredLocation.getCity()) ? "" : merchantInferredLocation.getCity()+ ", ";
                address += Objects.isNull(merchantInferredLocation.getState()) ? "" : merchantInferredLocation.getState()+ ", ";

                lendingAddress.put("address", address);
                lendingAddress.put("initial_lat", merchantInferredLocation.getIntialLatitude());
                lendingAddress.put("initial_long", merchantInferredLocation.getIntialLongitude());
                lendingAddress.put("inferred_lat", merchantInferredLocation.getInferredLatitude());
                lendingAddress.put("inferred_long", merchantInferredLocation.getInferredLongitude());
//                lendingAddress.put("pincode", merchantInferredLocation.getPincode());

                return lendingAddress;
            }
        }catch (Exception ex){
            logger.error("Error while sending DocumentIdProof to fos App for Application Id: {} Error: {}", lendingApplication.getId(),ex);
        }
        return null;
    }

    public FosResponseDTO getFosSalaryAttribution(FosAttributionRequestDTO request){
        logger.info("start getting fos salary attribution for fos refNumber: {} and merchant: {}", request.getFseRefcode(), request.getMerchantId());
        FosResponseDTO responseDTO =  new FosResponseDTO();
        try{
            List<LoanAttribution> loanAttributions = loanAttributionDao.getAttributionByMerchantIdAndRefCode(request.getMerchantId(), request.getFseRefcode());
            FosAttributionResponseDTO fosAttributionResponseDTO = new FosAttributionResponseDTO();
            if(Objects.isNull(loanAttributions) || loanAttributions.isEmpty()){
                fosAttributionResponseDTO.setStatus("MAYBE");

                responseDTO.setSuccess(true);
                responseDTO.setMessage("Attribution state");
                responseDTO.setData(fosAttributionResponseDTO);
                responseDTO.setStatusCode("200");
                return responseDTO;
            }
            for(LoanAttribution loanAttribution: loanAttributions){
                LendingApplication lendingApplication = lendingApplicationDao.findById(loanAttribution.getApplicationId()).get();
                if("REGULAR".equalsIgnoreCase(loanAttribution.getLoanType()) && loanAttribution.getLoanAmount() > 50000){

                    fosAttributionResponseDTO = isAgreementAttributed(request, loanAttribution, lendingApplication);
                    if(fosAttributionResponseDTO.getStatus().equalsIgnoreCase("YES")){
                        responseDTO.setSuccess(true);
                        responseDTO.setMessage("Attribution state");
                        responseDTO.setData(fosAttributionResponseDTO);
                        responseDTO.setStatusCode("200");
                        return responseDTO;
                    }
                }else if(("NTB".equalsIgnoreCase(loanAttribution.getLoanType()) || "OGL".equalsIgnoreCase(loanAttribution.getLoanType()) || ("REGULAR".equalsIgnoreCase(loanAttribution.getLoanType()) && loanAttribution.getLoanAmount() <= 50000))){
                    if(Objects.nonNull(loanAttribution.getEnachAttributedAt()) && Objects.nonNull(loanAttribution.getAgreementAttributedAt())) {
                        if(loanAttribution.getAgreementAttributedAt().compareTo(loanAttribution.getEnachAttributedAt()) > 0){
                            fosAttributionResponseDTO = isAgreementAttributed(request, loanAttribution, lendingApplication);
                            if(fosAttributionResponseDTO.getStatus().equalsIgnoreCase("YES")){
                                responseDTO.setSuccess(true);
                                responseDTO.setMessage("Attribution state");
                                responseDTO.setData(fosAttributionResponseDTO);
                                responseDTO.setStatusCode("200");
                                return responseDTO;
                            }
                        }else{
                            fosAttributionResponseDTO = isEnachAttributed(request, loanAttribution, lendingApplication);
                            if(fosAttributionResponseDTO.getStatus().equalsIgnoreCase("YES")){
                                responseDTO.setSuccess(true);
                                responseDTO.setMessage("Attribution state");
                                responseDTO.setData(fosAttributionResponseDTO);
                                responseDTO.setStatusCode("200");
                                return responseDTO;
                            }
                        }
                    }else{
                        fosAttributionResponseDTO.setStatus("NO");
                    }
                }else{
                    fosAttributionResponseDTO.setStatus("NO");
                }
            }

            responseDTO.setSuccess(true);
            responseDTO.setMessage("Attribution state");
            responseDTO.setData(fosAttributionResponseDTO);
            responseDTO.setStatusCode("200");
            return responseDTO;
        }catch(Exception ex){
            logger.error("Exception while getting fos salary attribution for fos refNumber: {} and merchant: {}, ex", request.getFseRefcode(), request.getMerchantId(), ex);
        }
        responseDTO.setSuccess(false);
        responseDTO.setMessage("Something Went Wrong!");
        responseDTO.setStatusCode("500");
        return responseDTO;
    }

    private FosAttributionResponseDTO isAgreementAttributed(FosAttributionRequestDTO request, LoanAttribution loanAttribution, LendingApplication lendingApplication){
        FosAttributionResponseDTO fosAttributionResponseDTO = new FosAttributionResponseDTO();
        if(Objects.nonNull(loanAttribution.getAgreementAttributedAt())) {
            Long hourDiff = LoanUtil.getDateDiffInHour(request.getTaskStartedAt(), loanAttribution.getAgreementAttributedAt());
            if (hourDiff > -1 && hourDiff < 168 && "FOS".equalsIgnoreCase(loanAttribution.getAgreementAttributedTo())) {
                fosAttributionResponseDTO.setStatus("YES");
                if (Objects.nonNull(lendingApplication.getDisburseTimestamp())) {
                    fosAttributionResponseDTO.setStage("AMOUNT_DISBURSED");
                    fosAttributionResponseDTO.setRemarks("Regular loan disbursed");
                } else {
                    fosAttributionResponseDTO.setStage("APPLICATION_COMPLETED");
                    fosAttributionResponseDTO.setRemarks("Regular loan application completed");
                }
            }else{
                fosAttributionResponseDTO.setStatus("NO");
                fosAttributionResponseDTO.setRemarks("Regular loan not eligible");
            }
            return fosAttributionResponseDTO;
        }

        fosAttributionResponseDTO.setStatus("NO");
        fosAttributionResponseDTO.setRemarks("Regular loan not eligible");
        return fosAttributionResponseDTO;
    }

    private FosAttributionResponseDTO isEnachAttributed(FosAttributionRequestDTO request, LoanAttribution loanAttribution, LendingApplication lendingApplication){
        FosAttributionResponseDTO fosAttributionResponseDTO = new FosAttributionResponseDTO();
        if(Objects.nonNull(loanAttribution.getEnachAttributedAt())) {
            Long hourDiff = LoanUtil.getDateDiffInHour(request.getTaskStartedAt(), loanAttribution.getEnachAttributedAt());
            if (hourDiff > -1 && hourDiff < 168 && "FOS".equalsIgnoreCase(loanAttribution.getEnachAttributedTo())) {
                fosAttributionResponseDTO.setStatus("YES");
                if (Objects.nonNull(lendingApplication.getDisburseTimestamp())) {
                    fosAttributionResponseDTO.setStage("AMOUNT_DISBURSED");
                    fosAttributionResponseDTO.setRemarks(loanAttribution.getLoanType()+ " loan disbursed");
                } else {
                    fosAttributionResponseDTO.setStage("APPLICATION_COMPLETED");
                    fosAttributionResponseDTO.setRemarks(loanAttribution.getLoanType()+ " loan application completed");
                }
            }else{
                fosAttributionResponseDTO.setRemarks(loanAttribution.getLoanType()+ " loan not eligible");
                fosAttributionResponseDTO.setStatus("NO");
            }
            return fosAttributionResponseDTO;
        }

        fosAttributionResponseDTO.setStatus("NO");
        fosAttributionResponseDTO.setRemarks(loanAttribution.getLoanType()+ " loan not eligible");
        return fosAttributionResponseDTO;
    }
//    private JsonNode parseStringResponse(String response){
//        if (response == null || response.isEmpty()) return null;
//        try {
//            return objectMapper.readTree(response);
//        } catch (Exception e) {
//            logger.info("Exception while parsing string response ", e);
//            return null;
//        }
//    }

}
