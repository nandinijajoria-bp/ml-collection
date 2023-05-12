package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import com.bharatpe.common.objects.CommonAPIRequest;

import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

@RestController
@RequestMapping("lending")
public class LoanDetailsController {
	Logger logger = LoggerFactory.getLogger(LoanDetailsController.class);

	@Autowired
	LoanDetailsService loanDetailsService;

	@Autowired
	LendingAgreementService lendingAgreementService;

	@Autowired
	LendingOffersService lendingOffersService;

	@Autowired
	ImageURLService imageURLService;

	@Autowired
	MerchantLoansService merchantLoansService;

	@Autowired
	VerifyDocService verifyDocService;

	@Autowired
	LoanEligibleService loanEligibleService;

	@Autowired
	MerchantUpdateService merchantUpdateService;

	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;

	@Autowired
	MerchantService merchantService;

//	 for testing - to be removed in future
	@Value("${lending.edi.model:7}")
	Integer lendingEdiModel;

	@RequestMapping(value="/loanDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<LoanDetailsResponseDTO> loanDetails(@RequestAttribute(required = false) BasicDetailsDto merchant, @RequestAttribute(required = false) String clientIp
	, HttpServletResponse response, @RequestBody(required = false) RequestDTO<IneligibleRequestDTO> requestDTO,
															  @RequestHeader(value = "token", required = false) String token,
															  @RequestParam(required = false) Long merchantId) {
		logger.info("loanDetails request : {}", requestDTO);

		if (ObjectUtils.isEmpty(merchant)) {
			final Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchantId);
			if (basicDetailsDto.isPresent())
				merchant = basicDetailsDto.get();
			else {
				return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
			}
		}

		LoanDetailsResponseDTO resp = loanDetailsService.fetchLoanDetails(merchant, requestDTO, clientIp, token);
		if (resp == null){
			logger.info("Sending gateway timeout for merchant: {}", merchant.getId());
			LoanDetailsResponseDTO response1 = new LoanDetailsResponseDTO();
			response1.setSuccess(false);
			response1.setMessage("Experian Failed");
			return new ResponseEntity<>(response1, HttpStatus.OK);
		}
		logger.info("loanDetails response : {}", resp);
		return new ResponseEntity<>(resp, HttpStatus.OK);
	}

	@RequestMapping(value="/lendingAgreement", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object lendingAgreement(@RequestAttribute BasicDetailsDto merchant, HttpServletResponse response,
								   @RequestBody CommonAPIRequest commonAPIRequest) {

		Object resp = lendingAgreementService.fetchLendingAgreement(merchant, response, commonAPIRequest);

		logger.info("LendingAgreement response : {}", resp);
		return resp;
	}

	@RequestMapping(value="/imageURL", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public Object imageURL(@RequestAttribute BasicDetailsDto merchant, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		logger.info("ImageURLController request : {}",commonAPIRequest);

		Object resp = imageURLService.fetchAndWrapResult(merchant, commonAPIRequest);

		logger.info("ImageURLController response : {}", resp);
		return resp;
	}

	@RequestMapping(value="/settlement", method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity<SettlementResponseDTO> settlement(@RequestAttribute BasicDetailsDto merchant, @RequestParam(name =
	"loan_id", required = false) Long loanId) {
		try {
			return new ResponseEntity<>(loanDetailsService.getSettlements(merchant, loanId), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Exception in settlement---", e);
			return new ResponseEntity<>(new SettlementResponseDTO(false, "Something went wrong"), HttpStatus.OK);
		}
	}

	@RequestMapping(value = "/active_loans", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<LendingActiveLoansResponseDTO> getAvailableLoans(
			@RequestParam(name = "merchant_id") Long requestMerchantId,
			@RequestParam(name = "merchant_store_id", required = false) Long requestMerchantStoreId) {
		logger.info("activeLoans request with merchant_id : {}, merchant_store_id: {}", requestMerchantId,
				requestMerchantStoreId);
		return new ResponseEntity<>(merchantLoansService.getActiveLoans(requestMerchantId, requestMerchantStoreId), HttpStatus.OK);

	}

	@RequestMapping(value = "/first_loan_status", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<?> firstLoanStatus(
	@RequestParam(name = "merchant_id") Long requestMerchantId,
	@RequestParam(name = "merchant_store_id", required = false) Long requestMerchantStoreId) {
		logger.info("first_loan_status request with merchant_id : {}, merchant_store_id: {}", requestMerchantId,
		requestMerchantStoreId);
		return new ResponseEntity<>(new ApiResponse<>(merchantLoansService.firstLoanStatus(requestMerchantId, requestMerchantStoreId)),
		HttpStatus.OK);
	}

	@RequestMapping(value = "/check_loan_status", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<?> checkLoanStatus(
	@RequestParam(name = "merchant_id") Long requestMerchantId,
	@RequestParam(name = "merchant_store_id", required = false) Long requestMerchantStoreId) {
		logger.info("check_loan_status request with merchant_id : {}, merchant_store_id: {}", requestMerchantId,
		requestMerchantStoreId);
		return new ResponseEntity<>(new ApiResponse<>(merchantLoansService.checkLoanStatus(requestMerchantId, requestMerchantStoreId)),
		HttpStatus.OK);
	}

	@RequestMapping(value = "/loan_history", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<?> loanHistory(
	@RequestParam(name = "merchant_id") Long requestMerchantId,
	@RequestParam(name = "merchant_store_id", required = false) Long requestMerchantStoreId) {
		logger.info("check_loan_status request with merchant_id : {}, merchant_store_id: {}", requestMerchantId,
		requestMerchantStoreId);
		return new ResponseEntity<>(new ApiResponse<>(merchantLoansService.getLoansHistory(requestMerchantId, requestMerchantStoreId)),
		HttpStatus.OK);
	}

	@RequestMapping(value = "/loan_statement", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<?> getLoanDetailsAndStatement(
	@RequestParam(name = "merchant_id") Long requestMerchantId,
	@RequestParam(name = "merchant_store_id", required = false) Long requestMerchantStoreId) {
		logger.info("getLoanDetailsAndSatement request with merchant_id : {}, merchant_store_id: {}", requestMerchantId,
		requestMerchantStoreId);
		return new ResponseEntity<>(new ApiResponse<>(merchantLoansService.getLoanDetailsAndStatement(requestMerchantId, requestMerchantStoreId)),
		HttpStatus.OK);
	}

	@RequestMapping(value = "/get_offers", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<LendingOffersResponseDTO> getAvailableLoans(@RequestAttribute BasicDetailsDto merchant) {
		logger.info("LendingOffers request with merchant_id : {}", merchant.getId());
		return new ResponseEntity<>(lendingOffersService.getOffers(merchant.getId()), HttpStatus.OK);
	}

	@RequestMapping(value = "/verify_pan_card", method = RequestMethod.GET)
	public VerifyPanCardDto verifyPanCard(@RequestAttribute BasicDetailsDto merchant,@RequestParam("panCard") String panCard) {
		logger.info("verify pancard check request for merchant:{} and pancard:{}", merchant.getId(), panCard);
		VerifyPanCardDto verifyPanCardDto =  verifyDocService.verifyPanCard(merchant, panCard);
		logger.info("verify pancard check response for merchant:{} and pancard:{} is :{}", merchant.getId(), panCard, verifyPanCardDto);
		return verifyPanCardDto;
	}

	@RequestMapping(value = "/offers", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<LendingOffersResponseDTO> getSwipeOffers(@RequestParam(name = "merchant_id") Long requestMerchantId,
																   @RequestParam(name = "merchant_store_id", required = false) Long requestMerchantStoreId) {
		logger.info("LendingOffers request with merchant_id : {}", requestMerchantId);
		return new ResponseEntity<>(lendingOffersService.getOffers(requestMerchantId), HttpStatus.OK);
	}

	@RequestMapping(value = "/eligible_offers", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<EligibleLendingOffersResponseDTO> getEligibleOfferDetails(@RequestAttribute BasicDetailsDto merchant,
																					@RequestParam(name = "query_amount", required = true) Double queryAmount,
																					@RequestParam(name = "edi_model", required = false) Integer ediModel) {
		ediModel = (ediModel == null) ? lendingEdiModel : ediModel;
		logger.info("EligibleLendingOffers request with merchant_id: {}, query_amount: {}", merchant.getId(), queryAmount, ediModel);
		EligibleLendingOffersResponseDTO resp = loanEligibleService.getEligibilityDetails(merchant.getId(), queryAmount,ediModel);
		logger.info("EligibleLendingOffers response: {}", resp);
		return new ResponseEntity<>(resp, HttpStatus.OK);
	}

	@RequestMapping(value = "/eligible_loan", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
	public ResponseEntity<ResponseDTO> updateEligibleLoanAmount(@RequestAttribute BasicDetailsDto merchant, @RequestBody(required = false) EligibleLoanUpdateRequestDTO requestDTO) {
		logger.info("updateEligibleLoanAmount request with merchant_id: {}, with body: {}", merchant.getId(), requestDTO);

		final ResponseDTO responseDTO = loanEligibleService.updateEligibleLoan(merchant.getId(), requestDTO);

		logger.info("updateEligibleLoanAmount response: {}", responseDTO);

		if (responseDTO.isSuccess()) {
			return new ResponseEntity<>(responseDTO, HttpStatus.OK);
		}
		return new ResponseEntity<>(responseDTO, HttpStatus.BAD_REQUEST);
	}

	@RequestMapping(value = "/derog_application", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<ApplicationDerogResponseDTO> derogMerchantExperian(@RequestParam(name = "merchant_id") Long merchantId,
	@RequestParam(name = "application_id") Long applicationId) {
		logger.info("derogMerchantExperian request with merchant_id: {}, applicationId: {}", merchantId, applicationId);
		return new ResponseEntity<>(loanEligibleService.processDerogSince(merchantId, applicationId, LendingConstants.APPLICATION_DEROG_RECHECK_MIN_DAYS), HttpStatus.OK);
	}

	@RequestMapping(value="/merchant_loans", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<LendingMerchantLoansResponseDTO> merchantLoans(
			@RequestAttribute(required = false) BasicDetailsDto merchant,
			@RequestParam(required = false) Long merchantId
	) {
		if (!ObjectUtils.isEmpty(merchant)){
			merchantId = merchant.getId();
		}
		logger.info("merchantLoans request merchant_id: {}", merchantId);
		LendingMerchantLoansResponseDTO resp = merchantLoansService.getMerchantLoans(merchantId);
		logger.info("merchantLoans response : {}", resp);
		return new ResponseEntity<>(resp, HttpStatus.OK);
	}

	@RequestMapping(value="/algo360_logs", method=RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<String> processAlgo360Logs(@RequestAttribute BasicDetailsDto merchant, @RequestBody String data, @RequestHeader("token") String token){

		merchantUpdateService.saveAlgo360Logs(merchant, data);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = "/check_new_merchant")
	public ResponseEntity<CommonResponse> checkCoolOff(@RequestAttribute BasicDetailsDto merchant, @RequestBody CoolOffRequestDTO requestDTO) {
		logger.info("check_new_merchant request:{} for merchantId: {}", requestDTO, merchant.getId());
		return new ResponseEntity<>(lendingOffersService.checkCoolOffPeriod(merchant, requestDTO), HttpStatus.OK);
	}

	@RequestMapping(value="/make_me_fresh", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<CommonResponse> fresh(@RequestAttribute BasicDetailsDto merchant) {
		logger.info("make me fresh request merchant_id: {}", merchant.getId());
		lendingOffersService.makeMeFresh(merchant);
		return new ResponseEntity<>(new CommonResponse(true, "success"), HttpStatus.OK);
	}

	@RequestMapping(value="/sms_permission", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
	public ResponseEntity<CommonResponse> smsPermission(@RequestAttribute BasicDetailsDto merchant) {
		logger.info("sms_permission request for merchant_id: {}", merchant.getId());
		lendingOffersService.checkNTBSMS(merchant);
		return new ResponseEntity<>(new CommonResponse(true, "success"), HttpStatus.OK);
	}

	@GetMapping(value = "/due_amount")
	public ResponseEntity<CommonResponse> getDueAmount(@RequestAttribute(required = false) BasicDetailsDto merchant,
													   @RequestParam(required = false) Long merchantId, @RequestParam(required = false) Long merchantStoreId) {
		logger.info("request to get due amount for merchantId:{} and merchantStoreId:{}", merchant != null ? merchant.getId() : merchantId, merchantStoreId);
		if (merchant == null && merchantId == null) {
			return ResponseEntity.badRequest().body(new CommonResponse(false, "merchantId/token is required"));
		}
		return ResponseEntity.ok(merchantLoansService.getDueAmount(merchantId, merchantStoreId, merchant));
	}

	@RequestMapping(value="/v2/settlement", method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity<SettlementV2ResponseDTO> settlementV2(@RequestAttribute BasicDetailsDto merchant, @RequestParam(name = "loan_id", required = false) Long loanId) {
		try {
			return new ResponseEntity<>(loanDetailsService.getSettlementsV2(merchant,loanId), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Exception in settlement---", e);
			return new ResponseEntity<>(new SettlementV2ResponseDTO(false, "Something went wrong"), HttpStatus.OK);
		}
	}

	@RequestMapping(value="/document_details", method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity<DocumentDetailsDto> documentDetails(@RequestParam(name = "application_id") Long loanId) {
		try {
			Optional<LendingPaymentSchedule> lendingPaymentSchedule = lendingPaymentScheduleDao.findById(loanId);
			if (!lendingPaymentSchedule.isPresent()) {
				return new ResponseEntity<>(new DocumentDetailsDto(false, "No payment schedule found for this loan_id", null), HttpStatus.OK);
			}
			return new ResponseEntity<>(loanDetailsService.documentDetails(lendingPaymentSchedule.get()), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Exception in settlement---", e);
			return new ResponseEntity<>(new DocumentDetailsDto(false, "Something went wrong", null), HttpStatus.OK);
		}
	}
}
