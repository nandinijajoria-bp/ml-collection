package com.bharatpe.lending.controller;

import javax.servlet.http.HttpServletResponse;

import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bharatpe.common.entities.Merchant;

@RestController
@RequestMapping("lending/credit_line/")
public class CreditLineController {
	Logger logger= LoggerFactory.getLogger(CreditLineController.class);
	
	@Autowired
	CreditLineService creditLineService;
	
	@Autowired
	CreditLineDashboardDetailsService creditLineDashboardDetailsService;
	
	@Autowired
	CreditLineLoanDetailsService creditLineLoanDetailsService;
	
	@Autowired
	CreditLineLoanHistoryService creditLineLoanHistoryService;
	
	@Autowired
	CreditLineBillService creditLineBillService;

	@Autowired
	CreditPaymentService creditPaymentService;
	
	@Autowired
	CreditSummaryService creditSummaryService;
	
	@Autowired
	TncService tncService;
	
	@RequestMapping("create")
	public ResponseEntity<ResponseDTO> createCreditLineAccount(@RequestAttribute Merchant merchant, @RequestBody CreateCreditAccountRequestDto requestDto){
		logger.info("create credit_account request : {}", requestDto);
		return new ResponseEntity<>(creditLineService.createCreditLineAccount(requestDto, merchant),HttpStatus.OK);
	}
	
	@RequestMapping(value = "/details", method = RequestMethod.GET)
	public DashboardDetailsResponseDto getDashboardDetails(@RequestAttribute Merchant merchant) {

		return creditLineDashboardDetailsService.getDetailsForDashboard(merchant);
		
	}
	
	@RequestMapping(value="/loanDetails", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<CreditLoanDetailsResponseDto> loanDetails(@RequestAttribute Merchant merchant, @RequestAttribute String clientIp, HttpServletResponse response, @RequestBody(required = false) RequestDTO<IneligibleRequestDTO> requestDTO) {
		logger.info("loanDetails request : {}", requestDTO);
		return new ResponseEntity<>(creditLineLoanDetailsService.getLoanDetails(merchant, requestDTO, clientIp), HttpStatus.OK);
		
	}
	
	@RequestMapping(value = "/history", method = RequestMethod.GET)
	public CreditLineHistoryResponseDto getLoanHistory(@RequestAttribute Merchant merchant) {
		
		return creditLineLoanHistoryService.getLoanHistory(merchant);
		
	}
	
	@RequestMapping(value = "/history/tl/{id}",method = RequestMethod.GET)
	public CreditLineTlHistoryResponseDto getTlLoanHistory(@PathVariable("id") Long id, @RequestAttribute Merchant merchant) {
		return creditLineLoanHistoryService.getTlHistory(id, merchant);
	}
	
	@RequestMapping(value = "/history/cl/{id}",method = RequestMethod.GET)
	public CreditLineClHistoryResponseDto getClLoanHistory(@PathVariable("id") Long id, @RequestAttribute Merchant merchant) {
		return creditLineLoanHistoryService.getClHistory(id, merchant);
	}

	@RequestMapping(value="/spend/details", method = RequestMethod.GET, consumes="application/json", produces="application/json")
	public ResponseEntity<CreditSpendResponseDTO> spendDetails(@RequestAttribute Merchant merchant, @RequestParam(name = "request_id") Long requestId) {
		try {
			return new ResponseEntity<>(creditLineService.getSpendDetails(merchant, requestId), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Exception---", e);
			return new ResponseEntity<>(new CreditSpendResponseDTO(false, "Something went wrong"), HttpStatus.OK);
		}
	}

	@RequestMapping(value="/spend/verify", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<CreditSpendVerifyResponseDTO> spendVerify(@RequestAttribute Merchant merchant, @RequestBody CreditSpendVerifyRequestDTO requestDTO) {
		logger.info("spend verify request : {}", requestDTO);
		try {
			if (requestDTO.getOtp() == null || requestDTO.getRequestId() == null) {
				return new ResponseEntity<>(new CreditSpendVerifyResponseDTO(false, "Invalid request"), HttpStatus.OK);
			}
			return new ResponseEntity<>(creditLineService.verifySpend(merchant, requestDTO), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Exception---", e);
			return new ResponseEntity<>(new CreditSpendVerifyResponseDTO(false, "Something went wrong"), HttpStatus.OK);
		}
	}
	
	@RequestMapping(value = "/repayment/history",method = RequestMethod.GET)
	public CreditLineRepaymentHistoryResponseDto getRepaymentHistroty(@RequestAttribute Merchant merchant) {
		return creditLineService.getRepaymentHistory(merchant);
	}
	
	@RequestMapping(value = "/bill",method = RequestMethod.GET)
	public CreditLineBillResponseDto getBillDetails(@RequestAttribute Merchant merchant) {
		return creditLineBillService.fetchBills(merchant);
	}
	
	@RequestMapping(value="/daily_repayment",method =RequestMethod.GET)
	public DailySettlementResponseDto getAutoDeductionDetails(@RequestAttribute Merchant merchant) {
		return creditLineService.fetchDailySettlementDetail(merchant);
	}
	
	@RequestMapping(value = "/bill/{id}",method = RequestMethod.GET)
	public BillDetailResponseDto getBillDetails(@PathVariable("id") Long id, @RequestAttribute Merchant merchant){
		return creditLineBillService.fetchBillDetail(merchant, id);
	}
	
	@RequestMapping(value = "/repayment/{id}",method = RequestMethod.GET)
	public CreditLineRepaymentDetailResponseDto getRepaymentDetail(@PathVariable("id") Long id, @RequestAttribute Merchant merchant) {
		return creditLineService.getRepaymentDetail(id, merchant);
	}

	@RequestMapping(value="/spend", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<CreditSpendResponseDTO> spend(@RequestAttribute Merchant merchant, @RequestBody CreditSpendRequestDTO requestDTO) {
		logger.info("spend request : {}", requestDTO);
		try {
			return new ResponseEntity<>(creditLineService.createSpend(merchant.getId(), requestDTO), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Exception---", e);
			return new ResponseEntity<>(new CreditSpendResponseDTO(false, "Something went wrong"), HttpStatus.OK);
		}
	}

	@RequestMapping(value="/spend/initiate", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<CreditSpendResponseDTO> spendInitiate(@RequestAttribute Merchant merchant, @RequestBody SpendInitiateRequestDTO requestDTO) {
		logger.info("spend initiate request : {}", requestDTO);
		try {
			return new ResponseEntity<>(creditLineService.initiateSpend(merchant, requestDTO), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Exception---", e);
			return new ResponseEntity<>(new CreditSpendResponseDTO(false, "Something went wrong"), HttpStatus.OK);
		}
	}

	@RequestMapping(value="/getPaymentModes", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<ResponseDTO> getPaymentModes(@RequestHeader("token") String token, @RequestAttribute Merchant merchant, @RequestBody RequestDTO<CreditSpendRequestDTO> requestDTO) {
		logger.info("getPaymentModes request : {}", requestDTO);
		try {
			return new ResponseEntity<>(creditPaymentService.getPaymentModes(requestDTO, token), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Exception---", e);
			return new ResponseEntity<>(new ResponseDTO(false, "Something went wrong"), HttpStatus.OK);
		}
	}

	@RequestMapping(value="/payment/verify", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<CreditSpendResponseDTO> verifyPayment(@RequestHeader("token") String token, @RequestAttribute Merchant merchant, @RequestBody RequestDTO<CreditSpendVerifyRequestDTO> requestDTO) {
		logger.info("payment verify request : {}", requestDTO);
		try {
			return new ResponseEntity<>(creditPaymentService.verifyPayment(requestDTO, merchant, token), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Exception---", e);
			return new ResponseEntity<>(new CreditSpendResponseDTO(false, "Something went wrong"), HttpStatus.OK);
		}
	}

	@RequestMapping(value="/payment/initiate", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<PaymentInitiateResponseDTO> initiatePayment(@RequestHeader("token") String token, @RequestAttribute Merchant merchant, @RequestBody RequestDTO<CreditPaymentRequestDTO> requestDTO) {
		logger.info("payment initiate request : {}", requestDTO);
		try {
			return new ResponseEntity<>(creditPaymentService.initiatePayment(requestDTO, merchant, token), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Exception---", e);
			return new ResponseEntity<>(new PaymentInitiateResponseDTO(false, "Something went wrong"), HttpStatus.OK);
		}
	}

	@RequestMapping(value="/payment/resendOTP", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public ResponseEntity<PaymentInitiateResponseDTO> resendOTP(@RequestHeader("token") String token, @RequestAttribute Merchant merchant, @RequestBody RequestDTO<CreditSpendVerifyRequestDTO> requestDTO) {
		logger.info("payment resend otp request : {}", requestDTO);
		try {
			return new ResponseEntity<>(creditPaymentService.resendOTP(requestDTO, merchant, token), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Exception---", e);
			return new ResponseEntity<>(new PaymentInitiateResponseDTO(false, "Something went wrong"), HttpStatus.OK);
		}
	}

	@RequestMapping(value="/vpa/update", method = RequestMethod.POST, consumes="application/json")
	public void vpaUpdate(@RequestBody VPAResponseDto request) {
		logger.info("vpa update request : {}", request);
		creditPaymentService.updatePaymentStatus(request);
	}
	
	@RequestMapping(value="/summary", method = RequestMethod.GET, consumes="application/json", produces="application/json")
    public SummaryResponseDTO createSummary(@RequestAttribute Merchant merchant) {
     	logger.info("summary request : {}",merchant.getId());
		return creditSummaryService.getSummary(merchant);
    }
	
	@RequestMapping(value="/tnc", method = RequestMethod.POST, consumes="application/json", produces="application/json")
	public TncDto getTnc(@RequestAttribute Merchant merchant,@RequestBody TncRequestDto requestDto) {
		logger.info("tnc request : {}", requestDto);
		return tncService.getTnc(merchant, requestDto);
	}
}
