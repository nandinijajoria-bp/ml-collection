 
  
package com.bharatpe.lending.controller;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import com.bharatpe.common.objects.CommonAPIRequest;

import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("lending")
public class LendingApplicationController {

	Logger logger = LoggerFactory.getLogger(LendingApplicationController.class);

	@Autowired
	LendingApplicationService lendingApplicationService;

	@Autowired
	LoanDetailsService loanDetailsService;

	@Autowired
	UploadDocumentService uploadDocumentService;

	@Autowired
	SignAgreementService signAgreementService;

	@Autowired
	VerifyOTPService verifyOTPService;

	@Autowired
	CancelApplicationService cancelApplicationService;
	
	@Autowired
	CallLoanDetailService callLoanDetailService;

	@Autowired
	LendingEdiScheduleService lendingEdiScheduleService;

	@Autowired
	RefundService refundService;

	@Autowired
	LendingCache lendingCache;
	
//	@RequestMapping(value="/createApplication", method = RequestMethod.POST, consumes="application/json", produces="application/json")
//	public LendingApplicationResponseDTO createApplication(@RequestAttribute BasicDetailsDto merchant, @RequestAttribute String clientIp, HttpServletResponse response, @RequestBody RequestDTO<LendingApplicationRequestDTO> requestDTO) {
//
//		if(requestDTO.getPayload() != null && requestDTO.getPayload().getPincode() != null && !lendingApplicationService.checkLoanRequestPinCodeForLoanEligibilty((int)(long)requestDTO.getPayload().getPincode())) {
//			logger.info("This loan request was raised from the location whose pin code is not eligible for the loan");
//			LendingApplicationResponseDTO lendingApplicationResponse=new LendingApplicationResponseDTO();
//			lendingApplicationResponse.setCode(LendingConstants.LOAN_APPLICATION_OGL_CODE);
//			lendingApplicationResponse.setMessage(LendingConstants.LOAN_APPLICATION_OGL_MESSAGE);
//			lendingApplicationResponse.setSuccess(false);
//			return lendingApplicationResponse;
//		}
//
//		logger.info("Create Application request : {} for merchant:{}",requestDTO, merchant.getId());
//		if(requestDTO.getPayload() == null) {
//			logger.info("Invalid request parameters : {}", requestDTO);
//			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
//			return null;
//		}
//		requestDTO.getMeta().setIp(clientIp);
//		LendingApplicationResponseDTO lendingApplicationResponse = lendingApplicationService.createApplication(merchant, requestDTO);
//		logger.info("Create Application response : {}", lendingApplicationResponse);
//		lendingApplicationResponse.setCode(LendingConstants.LOAN_APPLICATION_SUCCESS_CODE);
//		lendingApplicationResponse.setCode(LendingConstants.LOAN_APPLICATION_SUCCESS_MESSAGE);
//		return lendingApplicationResponse;
//	}

	@RequestMapping(value="/uploadDocument", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public UploadDocumentResponseDTO uploadDocument(@RequestAttribute BasicDetailsDto merchant, @RequestAttribute String clientIp,
													HttpServletResponse response, @RequestBody RequestDTO<UploadDocumentRequestDTO> requestDTO) {
		logger.info("UploadDocument request : {}",requestDTO);
		if(requestDTO.getPayload() == null) {
			logger.info("Invalid request parameters : {}", requestDTO);
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			return null;
		}
		requestDTO.getMeta().setIp(clientIp);
		UploadDocumentResponseDTO uploadDocumentResponse = uploadDocumentService.uploadDocument(merchant, requestDTO);

		logger.info("UploadDocument response : {}", uploadDocumentResponse);
		return uploadDocumentResponse;
	}

	@RequestMapping(value="/signAgreement", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object signAgreement(@RequestAttribute BasicDetailsDto merchant, @RequestAttribute String clientIp,  @RequestBody RequestDTO<SignAgreementDTO> requestDTO) {
		logger.info("singAgreement request : {}",requestDTO);
		requestDTO.getMeta().setIp(clientIp);
		Object resp = signAgreementService.signAgreement(merchant, requestDTO);
		logger.info("signAgreement response : {}", resp);
		return resp;
	}

	@RequestMapping(value="/verifyOTP", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object verifyOTP(@RequestAttribute BasicDetailsDto merchant, @RequestAttribute String clientIp, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("verifyOTP request : {}",commonAPIRequest);
		commonAPIRequest.getMeta().setIp(clientIp);
		Object resp = verifyOTPService.verifyOTP(merchant, commonAPIRequest);
		logger.info("verifyOTP response : {}", resp);
		return resp;
	}

	@RequestMapping(value="/cancelApplication", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object cancelApplication(@RequestAttribute BasicDetailsDto merchant, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("cancelApplication request : {}",commonAPIRequest);
		if(Objects.nonNull(merchant.getId())) {
			String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchant.getId();
			logger.info("deleting cached key of loan details in create application for merchant: {}",merchant.getId());
			lendingCache.delete(loanDetailsCacheKey);
		} else {
			logger.info("merchant id not found in create application");
		}
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		String reason = commonAPIRequest.getPayload().get("reason") != null ? commonAPIRequest.getPayload().get("reason").toString() : null;
		if(applicationId == null || applicationId <=0) {
			logger.info("CancelApplicationService invalid applicationId");
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			Map<String, Boolean> resp = new HashMap<>();
			resp.put("success",false);
			return resp;
		}

		Object resp = cancelApplicationService.cancelApplication(merchant, applicationId, reason);

		logger.info("cancelApplication response : {}", resp);
		return resp;
	}

	@RequestMapping(value="/sendOTP", method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity<ResponseDTO> sendOTP(@RequestAttribute BasicDetailsDto merchant, @RequestParam(name = "app_hash", required = false) String appHash) {
		return new ResponseEntity<>(lendingApplicationService.sendOtp(merchant, appHash), HttpStatus.OK);
	}

	@RequestMapping(value="/tnc", method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity<TncDto> tnc(@RequestAttribute BasicDetailsDto merchant, @RequestParam(required = false) Long applicationId,@RequestParam(required = false) String category, @RequestParam(required = false) String lender) {
		return new ResponseEntity<>(lendingApplicationService.getTnc(merchant, applicationId,category, lender), HttpStatus.OK);
	}

	@RequestMapping(value = "/callLoanDetail", method = RequestMethod.GET)
	public void callLoanDetails(@RequestParam Long id) {
		callLoanDetailService.callLoanDetail(id);
	}

	@RequestMapping(value="/publishDisbursal",method = RequestMethod.GET)
	public void callDisbursal(@RequestParam Long id){
		callLoanDetailService.publishForDisbursal(id);
	}

	@RequestMapping(value="/kafka/publish", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity publish(@RequestBody CreateTxnRequestDTO requestDTO,
								  @RequestAttribute BasicDetailsDto merchant) {
		lendingApplicationService.publishKafka(requestDTO, merchant.getId());
		return new ResponseEntity(HttpStatus.OK);
	}

	@RequestMapping(value="/fos/loan", method = RequestMethod.GET, produces="application/json")
	public ResponseEntity<ResponseDTO> fosLoanDetails(@RequestParam Long merchantId) {
		return new ResponseEntity<>(lendingApplicationService.fosLoan(merchantId), HttpStatus.OK);
	}

	@RequestMapping(value="/fos/loan/v2", method = RequestMethod.GET, produces="application/json")
	public ResponseEntity<ResponseDTO> fosnewLoanDetails(@RequestParam Long merchantId) {
		return new ResponseEntity<>(lendingApplicationService.fosnewLoan(merchantId), HttpStatus.OK);
	}

	@RequestMapping(value="/creditScore", method= RequestMethod.POST,produces = "application/json")
	public ResponseEntity<ResponseDTO> creditscore(@RequestAttribute BasicDetailsDto merchant,
												   @RequestAttribute String clientIp, HttpServletResponse response,@RequestBody(required = false) RequestDTO<CreditScoreRequestDto> requestDTO){
		return new ResponseEntity<>(loanDetailsService.creditScore(merchant,requestDTO,clientIp), HttpStatus.OK);
	}

	@RequestMapping(value="/applicationStatus", method= RequestMethod.POST,produces = "application/json")
	public ResponseEntity<ResponseDTO> applicationStatus(@RequestAttribute BasicDetailsDto merchant,
														 @RequestAttribute String clientIp, HttpServletResponse response,@RequestBody(required = false) RequestDTO<ApplicationStatusRequestDTO> requestDTO, @RequestHeader("token") String token) {
		return new ResponseEntity<>(lendingApplicationService.applicationStatus(merchant, requestDTO, clientIp, token), HttpStatus.OK);
	}

//	@RequestMapping(value="/adhaar_mask", method= RequestMethod.POST,produces = "application/json")
//	public ResponseEntity<CommonResponse> maskAdhaar(@RequestBody AdhaarMaskRequest requestDTO){
//		return new ResponseEntity<>(adhaarMaskService.maskAdhaar(requestDTO), HttpStatus.OK);
//	}

	@GetMapping(value="/edi_schedule", produces = "application/json")
	public ResponseEntity<CommonResponse> getEdiSchedule(@RequestParam Long merchantId, @RequestParam Long applicationId){
		return new ResponseEntity<>(lendingEdiScheduleService.getEdiSchedule(merchantId, applicationId), HttpStatus.OK);
	}

	@GetMapping(value="/edi_schedule/v2", produces = "application/json")
	public ResponseEntity<CommonResponse> getEdiScheduleV2(@RequestParam Long merchantId, @RequestParam Long applicationId){
		return new ResponseEntity<>(lendingEdiScheduleService.getEdiScheduleV2(merchantId, applicationId), HttpStatus.OK);
	}

	@RequestMapping(value="/allow_bankaccount_change", method = RequestMethod.GET, produces="application/json")
	public ResponseEntity<ResponseDTO> bankAccount(@RequestParam Long merchantId) {
		return new ResponseEntity<>(lendingApplicationService.bankAccountChange(merchantId), HttpStatus.OK);
	}

	@PostMapping(value = "/nach_refund")
	public ResponseEntity<CommonResponse> nachRefund(@RequestBody NachRefundRequest refundRequest) {
		logger.info("Nach refund request:{}", refundRequest);
		CommonResponse response = refundService.nachRefund(refundRequest);
		logger.info("Nach refund response:{}", response);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping(value = "/processing_fee_refund")
	public ResponseEntity<CommonResponse> processingFeeRefund(@RequestBody ProcessingFeeRequest processingFeeRequest, @RequestParam(required = false) Boolean callFromLMS){
		logger.info("Processing Fee Request:{}", processingFeeRequest);
		CommonResponse response = refundService.processingFeeRefund(processingFeeRequest, callFromLMS);
		logger.info("Nach refund response:{}", response);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@RequestMapping(value="/repayment_history", method = RequestMethod.GET)
	public ResponseEntity<CommonResponse> repaymentHistory(@RequestAttribute BasicDetailsDto merchant, @RequestParam(name =
	"loan_id") String lendingPaymentScheduleId){
		logger.info("repayment History request for lendingPaymentScheduleId :{}", lendingPaymentScheduleId);
		CommonResponse response = loanDetailsService.getRepaymentHistory(merchant, lendingPaymentScheduleId);
		logger.info("repayment History Response: {}", response);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@RequestMapping(value="/application", method = RequestMethod.GET, produces="application/json")
	public ResponseEntity<?> getLendingApplication(@RequestParam(name = "applicationId") Long applicationId) {
		logger.info("getLendingApplication request with applicationId : {}", applicationId);

		if (ObjectUtils.isEmpty(applicationId)) {
			return new ResponseEntity<>(new ApiResponse<>(false, "Required fields applicationId not sent"),
			HttpStatus.BAD_REQUEST);
		}

		final LendingApplicationDTO lendingApplicationDTO = lendingApplicationService.getLendingApplication(applicationId);

		if (ObjectUtils.isEmpty(lendingApplicationDTO)) {
			return new ResponseEntity<>(new ApiResponse<>(false, "Lending application not found"),
			HttpStatus.NOT_FOUND);
		}

		return new ResponseEntity<>(new ApiResponse<>(lendingApplicationDTO), HttpStatus.OK);
	}

	@RequestMapping(value="/loan/topup", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<?> signAgreementForTopup(@RequestAttribute BasicDetailsDto merchant,  @RequestBody CreateApplicationRequestForTopupDTO requestDTO) {
		logger.info("singAgreement request : {}",requestDTO);
		Map<String, Object> response = signAgreementService.createNewApplicationAndSendOTPForTopup(merchant, requestDTO);
//		logger.info("signAgreement response : {}", resp);
		return new ResponseEntity<>(new ApiResponse<>(response), HttpStatus.OK);

	}

}