package com.bharatpe.lending.service;

//import java.util.Date;
//import java.util.HashMap;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//
//import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
//import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
//import com.bharatpe.lending.common.service.merchant.service.MerchantService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

//import com.bharatpe.lending.common.dao.CreditAccountBillDao;
//import com.bharatpe.lending.common.dao.CreditAccountDao;
//import com.bharatpe.lending.common.dao.CreditDayEndBalanceDao;
//import com.bharatpe.lending.common.dao.LendingClPaymentBreakupDao;
//import com.bharatpe.lending.common.dao.LendingClTransactionDao;
//import com.bharatpe.lending.common.entity.CreditAccount;
//import com.bharatpe.lending.common.entity.CreditAccountBill;
//import com.bharatpe.lending.common.entity.CreditDayEndBalance;
//import com.bharatpe.lending.common.entity.LendingClPaymentBreakup;
//import com.bharatpe.lending.common.entity.LendingClTransaction;
//import com.bharatpe.lending.constant.CreditConstants;
//import com.bharatpe.lending.dto.BillDetailResponseDto;
//import com.bharatpe.lending.dto.BillDetailResponseDto.Transaction;
//import com.bharatpe.lending.dto.CreditLineBillResponseDto;
//import com.bharatpe.lending.dto.CreditLineBillResponseDto.Bill;
//import com.bharatpe.lending.handlers.S3BucketHandler;

@Service
public class CreditLineBillService {
	
//	@Autowired
//	CreditAccountBillDao creditAccountBillDao;
//
//	@Autowired
//	CreditAccountDao creditAccountDao;
//
//	@Autowired
//	LendingClTransactionDao lendingClTransactionDao;
//
//	@Autowired
//	CreditDayEndBalanceDao creditDayEndBalanceDao;
//
//	@Autowired
//	S3BucketHandler s3BucketHandler;
//
//	@Autowired
//	LendingClPaymentBreakupDao lendingClPaymentBreakupDao;
//
//	@Autowired
//	CreditUtil creditUtil;
//
//	@Value("${aws.s3.bucket_bill}")
//	private String bucket;
//
//	@Autowired
//	MerchantService merchantService;
//
//	Logger logger=LoggerFactory.getLogger(CreditLineBillService.class);
//
//	public CreditLineBillResponseDto fetchBills(BasicDetailsDto merchant) {
//		try {
//			CreditLineBillResponseDto response=new CreditLineBillResponseDto();
//			CreditAccount creditAccount=creditAccountDao.findByMerchantIdForDashBoard(merchant.getId());
//			if(creditAccount==null){
//				return getErrorResponseForBill("No account found");
//			}
//			List<Bill> bills=new LinkedList<>();
//
//			Map<String,List<Bill>> unPaidBills=getUnPaidBills(creditAccount);
//			if(unPaidBills==null){
//				return getErrorResponseForBill("Error occured while fetching unpaid bills");
//			}
//			bills.addAll(unPaidBills.get("generated"));
//			bills.addAll(unPaidBills.get("due"));
//			List<Bill> piadBills=getPaidBill(creditAccount);
//			if(piadBills==null) {
//				return getErrorResponseForBill("Error occured while fetching paid bills");
//			}
//			bills.addAll(piadBills);
//			response.setBill(bills);
//
//			return response;
//		}
//		catch(Exception e) {
//			logger.error("Error occured while fetching bills",e);
//			return getErrorResponseForBill("Error occured while fetch bills");
//		}
//	}
//
//	public Map<String,List<Bill>> getUnPaidBills(CreditAccount creditAccount){
//		try {
//			logger.info("Fetching unpaid bills for account {}",creditAccount.getId());
//			List<CreditAccountBill> creditAccountBills=creditAccountBillDao.findByAccountIdAndBillPaidOrderByBillDateDesc(creditAccount.getId(),false);
//			Map<String,List<Bill>> billMap=new HashMap<String, List<Bill>>();
//			billMap.put("due", new LinkedList<>());
//			billMap.put("generated", new LinkedList<>());
//			for(CreditAccountBill creditAccountBill:creditAccountBills){
//				Bill bill=new Bill();
//				bill.setId(creditAccountBill.getId());
//				bill.setBillAmount(creditAccountBill.getAmount());
//				bill.setMinDueAmount(creditAccountBill.getMinimumAmountDue());
//				bill.setBillCycleStartDate(creditAccountBill.getBillStartDate());
//				bill.setBillCycleEndDate(creditAccountBill.getBillEndDate());
//				bill.setDueDate(creditAccountBill.getDueDate());
//				bill.setGeneratedDate(creditAccountBill.getBillDate());
//				//generated case
//				if(creditAccountBill.getDueDate().after(new Date())){
//					bill.setState("GENERATED");
//					bill.setPayableAmount(creditUtil.getPayableAmount(creditAccount));
//					bill.setPayableMad(creditAccount.getMinimumAmountDue());
//					billMap.get("generated").add(bill);
//				}
//				else {
//				//due case
//					bill.setState("DUE");
//					bill.setPayableAmount(creditUtil.getPayableAmount(creditAccount));
//					bill.setPayableMad(creditAccount.getMinimumAmountDue());
//					billMap.get("due").add(bill);
//				}
//			}
//			return billMap;
//		}
//		catch(Exception e){
//			logger.error("Error occured while fetching unpaid bill details",e);
//			return null;
//		}
//	}
//
//	public List<Bill> getPaidBill(CreditAccount creditAccount){
//		try {
//			logger.info("Fetching paid bill for account {}",creditAccount.getId());
//			List<CreditAccountBill> creditAccountBills=creditAccountBillDao.findByAccountIdAndBillPaidOrderByBillDateDesc(creditAccount.getId(),true);
//			List<Bill> paidBills=new LinkedList<>();
//			for(CreditAccountBill creditAccountBill:creditAccountBills) {
//				Bill bill=new Bill();
//				bill.setBillAmount(creditAccountBill.getAmount());
//				bill.setId(creditAccountBill.getId());
//				bill.setPaidAmount(creditAccountBill.getPaidAmount());
//				bill.setMinDueAmount(creditAccountBill.getMinimumAmountDue());
//				bill.setPaidDate(creditAccountBill.getBillPaidDate());
//				bill.setState("PAID");
//				bill.setGeneratedDate(creditAccountBill.getBillDate());
//				bill.setBillCycleStartDate(creditAccountBill.getBillStartDate());
//				bill.setBillCycleEndDate(creditAccountBill.getBillEndDate());
//				paidBills.add(bill);
//			}
//			return paidBills;
//		}
//		catch(Exception e) {
//			logger.error("Error occured while fetching paid bill details",e);
//			return null;
//		}
//	}
//
//	public CreditLineBillResponseDto getErrorResponseForBill(String message){
//
//		CreditLineBillResponseDto errorResponse=new CreditLineBillResponseDto();
//		errorResponse.setSuccess(false);
//		errorResponse.setMessage(message);
//		return errorResponse;
//	}
//
//	public BillDetailResponseDto fetchBillDetail(BasicDetailsDto merchant,Long id) {
//
//		try {
//			final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
//			BankDetailsDto merchantBankDetail = null;
//			if (bankDetailsDtoOptional.isPresent())
//				merchantBankDetail = bankDetailsDtoOptional.get();
//			logger.info("Fetching bill details for bill {}",id);
//			CreditAccount creditAccount=null;
//			BillDetailResponseDto response=new BillDetailResponseDto();
//			Optional<CreditAccountBill> billOptional=creditAccountBillDao.findById(id);
//			if(billOptional==null | !billOptional.isPresent()){
//				return getBillDetailErrorResponse("Bill not found");
//			}
//			CreditAccountBill creditAccountBill=billOptional.get();
//			if(!creditAccountBill.getBillPaid()) {
//				Optional<CreditAccount> creditOptional=creditAccountDao.findById(creditAccountBill.getAccountId());
//				if(creditOptional==null || !creditOptional.isPresent()){
//					return getBillDetailErrorResponse("Credit account not found for id "+creditAccountBill.getAccountId());
//				}
//				creditAccount=creditOptional.get();
//				response.setPayableMad(creditAccount.getMinimumAmountDue());
//				response.setPayableAmount(creditUtil.getPayableAmount(creditAccount));
//			}
//			CreditDayEndBalance creditDayEndBalance=creditDayEndBalanceDao.findByAccountIdAndDate(creditAccountBill.getAccountId(),creditAccountBill.getBillDate());
//			if(creditDayEndBalance==null){
//				if(creditAccount==null) {
//					Optional<CreditAccount> creditOptional=creditAccountDao.findById(creditAccountBill.getAccountId());
//					if(creditOptional==null || !creditOptional.isPresent()){
//						return getBillDetailErrorResponse("Credit account not found for id "+creditAccountBill.getAccountId());
//					}
//					creditAccount=creditOptional.get();
//				}
//				response.setAvailableLimit(creditAccount.getAvailableBalance());
//			}
//			else {
//				response.setAvailableLimit(creditDayEndBalance.getAvailableBalance());
//			}
//			response.setBillCycleStartDate(creditAccountBill.getBillStartDate());
//			response.setBillCycleEndDate(creditAccountBill.getBillEndDate());
//			response.setDueDate(creditAccountBill.getDueDate());
//			response.setInterestPayable(creditAccountBill.getInterestAmount());
//			response.setUsedLimit(creditAccountBill.getAmount()-creditAccountBill.getInterestAmount()-creditAccountBill.getPenalty());
//			response.setMerchantName(merchantBankDetail.getBeneficiaryName());
//			response.setMinAmountDue(creditAccountBill.getMinimumAmountDue());
//			response.setTotalPayable(creditAccountBill.getAmount());
//			response.setMobile(merchant.getMobile());
//			if(creditAccountBill.getBillKey()!=null) {
//				response.setPdfUrl(s3BucketHandler.getTemporaryPublicURL(creditAccountBill.getBillKey(),bucket));
//			}
//
//			List<LendingClTransaction> transactions=lendingClTransactionDao.findClDetailsByCreditAccountIdAndTypeBetweenDates(creditAccountBill.getAccountId(),creditAccountBill.getBillStartDate(), creditAccountBill.getBillEndDate());
//			List<Transaction> transactionList=new LinkedList<>();
//			for(LendingClTransaction lendingClTransaction:transactions) {
//
//				Transaction transaction=new Transaction();
//				if(lendingClTransaction.getType().equalsIgnoreCase("PAYMENT")) {
//					Double paidAmount=getRepaidAmount(lendingClTransaction);
//					if(paidAmount!=null && paidAmount!=0D) {
//						transaction.setAmount(paidAmount);
//					}
//					else {
//						continue;
//					}
//				}
//				else {
//					transaction.setAmount(lendingClTransaction.getAmount());
//				}
//				transaction.setDate(lendingClTransaction.getCreatedAt());
//				transaction.setSubType(CreditConstants.SpendModeFrontEndFormat.getOrDefault(lendingClTransaction.getSubType(), lendingClTransaction.getSubType()));
//				transaction.setType(lendingClTransaction.getType());
//				transactionList.add(transaction);
//
//			}
//			Double balanceFromLastBill=getBalanceFromLastBill(creditAccountBill.getAccountId(),id);
//			if(balanceFromLastBill!=null) {
//				Transaction transaction=new Transaction();
//				transaction.setDate(creditAccountBill.getBillStartDate());
//				transaction.setSubType("Balance from last bill");
//				transaction.setType("Balance from last bill");
//				transaction.setAmount(balanceFromLastBill);
//				transactionList.add(transaction);
//			}
//			response.setTransactions(transactionList);
//			return response;
//		}
//		catch(Exception e){
//			logger.error("Error occured while fetching bill details",e);
//			return getBillDetailErrorResponse("Error occured while fetching bill details");
//		}
//
//	}
//
//	public Double getBalanceFromLastBill(Long creditId,Long id) {
//		try {
//			CreditAccountBill creditAccountBill=creditAccountBillDao.getPreviousBill(creditId,id);
//			if(creditAccountBill!=null) {
//				return creditAccountBill.getAmount()-creditAccountBill.getInterestAmount()-creditAccountBill.getPrincipleAmount();
//			}
//		}
//		catch(Exception e) {
//			logger.error("Error occured while fetching balance from last bill",e);
//		}
//		return null;
//	}
//
//	public Double getRepaidAmount(LendingClTransaction lendingClTransaction) {
//		try {
//			LendingClPaymentBreakup lendingClPaymentBreakup=lendingClPaymentBreakupDao.findByLendingClTransactionAndPaymentType(lendingClTransaction,"CL");
//			if(lendingClPaymentBreakup!=null){
//				return lendingClPaymentBreakup.getAmount();
//			}
//			return 0D;
//		}
//		catch(Exception e) {
//			logger.error("Error occured while calculating prepaid amount",e);
//			return null;
//		}
//	}
//
//	public BillDetailResponseDto getBillDetailErrorResponse(String message) {
//
//		BillDetailResponseDto billDetailResponseDto=new BillDetailResponseDto();
//		billDetailResponseDto.setSuccess(false);
//		billDetailResponseDto.setMessage(message);
//		return billDetailResponseDto;
//	}
}
