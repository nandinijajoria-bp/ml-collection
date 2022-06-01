package com.bharatpe.lending.service;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import com.bharatpe.lending.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.util.CreditUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.IfscDao;
import com.bharatpe.common.entities.Ifsc;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.CreditAccountDao;
import com.bharatpe.lending.common.dao.CreditDayEndBalanceDao;
import com.bharatpe.lending.common.dao.LendingClTransactionDao;
import com.bharatpe.lending.common.dao.LendingTlDetailsDao;
import com.bharatpe.lending.common.entity.CreditAccount;
import com.bharatpe.lending.common.entity.CreditDayEndBalance;
import com.bharatpe.lending.common.entity.LendingClTransaction;
import com.bharatpe.lending.common.entity.LendingTlDetails;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.CreditLineClHistoryResponseDto;
import com.bharatpe.lending.dto.CreditLineClHistoryResponseDto.Narration;
import com.bharatpe.lending.dto.CreditLineHistoryResponseDto;
import com.bharatpe.lending.dto.CreditLineHistoryResponseDto.Loan;
import com.bharatpe.lending.dto.CreditLineTlHistoryResponseDto;
import com.bharatpe.lending.dto.CreditLineTlHistoryResponseDto.IndividualSettlement;

@Service
public class CreditLineLoanHistoryService {
	
	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;
	
	@Autowired
	CreditAccountDao creditAccountDao;
	
	@Autowired
	LendingClTransactionDao lendingClTransactionDao;
	
	@Autowired
	LendingLedgerDao lendingLedgerDao;
	
	@Autowired
	LendingTlDetailsDao lendingTlDetailsDao;
	
	@Autowired
	IfscDao ifscDao;
	
	@Autowired
	CreditDayEndBalanceDao creditDayEndBalanceDao;
	
	Logger logger=LoggerFactory.getLogger(CreditLineLoanHistoryService.class);

	@Autowired
	CreditUtil creditUtil;
	
	public CreditLineHistoryResponseDto getLoanHistory(BasicDetailsDto merchant){
		
	   try{
		  
		   logger.info("Fetching loan history for merchant {}",merchant);
		   	CreditLineHistoryResponseDto creditLineHistoryResponseDto=new CreditLineHistoryResponseDto();
		   	
			if(merchant!=null && merchant.getId()!=null) {
				
				CreditAccount creditAccount=creditAccountDao.findByMerchantIdForDashBoard(merchant.getId());
				if(creditAccount==null) {
					return getErrorResponse("No credit account found");
				}
				
				creditLineHistoryResponseDto.setAvailableBalance(creditAccount.getAvailableBalance());
				creditLineHistoryResponseDto.setTotalPayable(creditUtil.getPayableAmount(creditAccount));
				List<Loan> termLoans=getTermLoanDetails(merchant);
				List<Loan> creditLoans=getCreditLoanDetails(creditAccount);
				
				if(termLoans==null || creditLoans==null) {
					return getErrorResponse("Error occured while fetching loan details");
				}
				creditLineHistoryResponseDto.setCL(creditLoans);
				creditLineHistoryResponseDto.setTL(termLoans);
			
			return creditLineHistoryResponseDto;
			
			}
			else {
					return getErrorResponse("Bad request");
				}
			}
		catch(Exception e){
			
				logger.error("Error occured while fetching credit line loans history",e);
				return getErrorResponse("Error occured while fetching credit line loans history");
				
		}
			
	}
	
	public List<Loan> getCreditLoanDetails(CreditAccount creditAccount) {
		
		try {

			logger.info("Fetching credit loan for credit account {}",creditAccount);
			List<Loan> loanList=new LinkedList<>();
			List<LendingClTransaction> creditloanList=lendingClTransactionDao.findActiveTransactionByCreditAccountIdAndModeAndType(creditAccount.getId(),"DEBIT","CL");
			
			if(creditloanList!=null && !creditloanList.isEmpty()) {
				
				for(LendingClTransaction creditAccountTransaction:creditloanList) {
					
					Loan loan=new Loan();
					loan.setAmount(creditAccountTransaction.getAmount());
					loan.setDate(creditAccountTransaction.getCreatedAt());
					loan.setIcon(creditAccountTransaction.getIcon());
					if(CreditConstants.SpendModeFrontEndFormat.containsKey(creditAccountTransaction.getSubType())) {
						loan.setSpendMode(CreditConstants.SpendModeFrontEndFormat.get(creditAccountTransaction.getSubType()));
					}
					else {
						loan.setSpendMode(creditAccountTransaction.getSubType());
					}
					loan.setId(creditAccountTransaction.getId());
					
					loanList.add(loan);
				}
				
			}
			loanList.sort((x,y)->y.getDate().compareTo(x.getDate()));	
			return loanList;
		}
		catch(Exception e){
			logger.error("Error occured while fetching credit loan details",e);
			return null;
		}
	}
	
	public List<Loan> getTermLoanDetails(BasicDetailsDto merchant) {
		try {

			logger.info("Fetching active term loan from lending_payment_schedule table for merchant {}",merchant);
			List<Loan> loanList=new LinkedList<>();
			List<LendingPaymentSchedule> termLoanList=lendingPaymentScheduleDao.findByMerchantIdAndCreditLoan(merchant.getId(), true);
			
			for(LendingPaymentSchedule lendingPaymentSchedule:termLoanList) {
				
				Optional<LendingTlDetails> lendingTlDetailsOptional=lendingTlDetailsDao.findById(lendingPaymentSchedule.getTlDetailsId());
				
				if(lendingTlDetailsOptional!=null && lendingTlDetailsOptional.isPresent()) {
					
					LendingTlDetails lendingTlDetails=lendingTlDetailsOptional.get();
					Loan loan=new Loan();
					loan.setAmount(lendingPaymentSchedule.getLoanAmount());
					loan.setEdi(lendingPaymentSchedule.getEdiAmount());
					loan.setId(lendingPaymentSchedule.getId());
					loan.setIcon(lendingTlDetails.getLendingClTransaction().getIcon());
					if(CreditConstants.SpendModeFrontEndFormat.containsKey(lendingTlDetails.getLendingClTransaction().getSubType())) {
						loan.setSpendMode(CreditConstants.SpendModeFrontEndFormat.get(lendingTlDetails.getLendingClTransaction().getSubType()));
					}
					else {
						loan.setSpendMode(lendingTlDetails.getLendingClTransaction().getSubType());
					}
					loan.setTenure(getTenure(lendingPaymentSchedule.getEdiCount()));
//					if(lendingPaymentSchedule.getInterestOnlyStartDate()!=null) {
//						loan.setDate(lendingPaymentSchedule.getInterestOnlyStartDate());
//					}
//					else {
//						loan.setDate(lendingPaymentSchedule.getStartDate());
//					}
					loan.setDate(lendingPaymentSchedule.getCreatedAt());
					loan.setStatus(lendingPaymentSchedule.getStatus());
					loanList.add(loan);
					
				}
				else {
					logger.error("LendingTLDetail not found for lps {}",lendingPaymentSchedule);
					return null;
				}
				
			}
			loanList.sort((x,y)->y.getDate().compareTo(x.getDate()));
			return loanList;
			
		}
		catch(Exception e) {
			logger.error("Error occured while processing term loan details",e);
			return null;
		}
	
	}
	
	public Integer getTenure(Integer ediCount) {
		
		return (ediCount+1)/26;
		
	}
	
	public CreditLineHistoryResponseDto getErrorResponse(String message) {
		
		CreditLineHistoryResponseDto creditLineHistoryResponseDto=new CreditLineHistoryResponseDto();
		creditLineHistoryResponseDto.setSuccess(false);
		creditLineHistoryResponseDto.setMessage(message);
		return creditLineHistoryResponseDto;
		
	}
	
	public CreditLineTlHistoryResponseDto getTlHistory(Long id, BasicDetailsDto merchant){
	
		try {
			CreditLineTlHistoryResponseDto creditLineTlHistoryResponseDto=new CreditLineTlHistoryResponseDto();
			Optional<LendingPaymentSchedule> optional=lendingPaymentScheduleDao.findById(id);
			
			if(optional!=null && optional.isPresent()) {
				
				LendingPaymentSchedule lendingPaymentSchedule=optional.get();
				Optional<LendingTlDetails> Tloptional=lendingTlDetailsDao.findById(lendingPaymentSchedule.getTlDetailsId());
				if(Tloptional!=null && Tloptional.isPresent()){
					LendingTlDetails lendingTlDetails=Tloptional.get();
					creditLineTlHistoryResponseDto.setInterestRate(lendingTlDetails.getInterestRate());
					if(CreditConstants.SpendModeFrontEndFormat.containsKey(lendingTlDetails.getLendingClTransaction().getSubType())) {
						creditLineTlHistoryResponseDto.setSpendMode(CreditConstants.SpendModeFrontEndFormat.get(lendingTlDetails.getLendingClTransaction().getSubType()));
					}
					else {
						creditLineTlHistoryResponseDto.setSpendMode(lendingTlDetails.getLendingClTransaction().getSubType());
					}			
				}
				else {
					logger.error("lendingTlDetails not found for LPS {}",lendingPaymentSchedule);
					return getErrorMessageForTlHistory("Details not found");
				}
				creditLineTlHistoryResponseDto.setEdiAmount(lendingPaymentSchedule.getEdiAmount());
				creditLineTlHistoryResponseDto.setLoanAmount(lendingPaymentSchedule.getLoanAmount());
				creditLineTlHistoryResponseDto.setTenure(getTenure(lendingPaymentSchedule.getEdiCount()));
				creditLineTlHistoryResponseDto.setRepaid(lendingPaymentSchedule.getPaidAmount());
				creditLineTlHistoryResponseDto.setStatus(lendingPaymentSchedule.getStatus());
				if(lendingPaymentSchedule.getStatus().equalsIgnoreCase("CLOSED")) {
					creditLineTlHistoryResponseDto.setRepaymentAmount(0D);
				}
				else {
					creditLineTlHistoryResponseDto.setRepaymentAmount(lendingPaymentSchedule.getLoanAmount()-(lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0)+(lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0));
				}
				
				if(lendingPaymentSchedule.getInterestOnlyStartDate()!=null) {
					creditLineTlHistoryResponseDto.setEdiStartDate(lendingPaymentSchedule.getInterestOnlyStartDate());
				}
				else {
					creditLineTlHistoryResponseDto.setEdiStartDate(lendingPaymentSchedule.getStartDate());
				}
				
				List<IndividualSettlement> dailySettlementList=getIndividualTlSettlements(lendingPaymentSchedule);
				if(dailySettlementList==null) {
					return getErrorMessageForTlHistory("Error occured while fetching loan details");
				}
				creditLineTlHistoryResponseDto.setSchedule(dailySettlementList);
				return creditLineTlHistoryResponseDto;
			}
			else {
				logger.error("LPS not found for id {}",id);
				return getErrorMessageForTlHistory("No laon found for the given id");
			}
		}
		catch(Exception e) {
			logger.error("Error occured while fetching loan history",e);
			return getErrorMessageForTlHistory("Error occured while fetching loan history");
		}
	}
	
	public CreditLineTlHistoryResponseDto getErrorMessageForTlHistory(String message) {
		
		CreditLineTlHistoryResponseDto creditLineTlHistoryResponseDto=new CreditLineTlHistoryResponseDto();
		creditLineTlHistoryResponseDto.setSuccess(false);
		creditLineTlHistoryResponseDto.setMessage(message);
		return creditLineTlHistoryResponseDto;
	}
	
	public List<IndividualSettlement> getIndividualTlSettlements(LendingPaymentSchedule lendingPaymentSchedule){
		
		try {
			List<LendingLedger> ledgerList=lendingLedgerDao.findByLendingPaymentScheduleOrderByDateAscAmountAsc(lendingPaymentSchedule);
			if(ledgerList!=null){
				List<IndividualSettlement> settlementsList=new LinkedList<>();
				Double positiveSum=0D;
				Double negativeSum=0D;
				Double dueAmount=0D;
				for(int i=0;i<ledgerList.size();i++) {
					String mode=null;
					LendingLedger firstLedger=ledgerList.get(i);
					double ediDue=0f;
					//double ediPaid=0f;
					List<IndividualSettlement> localSettlementList=new LinkedList<>();
					if(firstLedger.getAmount()>0){
						localSettlementList.add(new IndividualSettlement(firstLedger.getDate(),firstLedger.getAmount(),null,getMode(firstLedger.getAdjustmentMode())));
						positiveSum+=firstLedger.getAmount();
					}
					else {
						negativeSum+=firstLedger.getAmount();
						ediDue+=-1*firstLedger.getAmount();
					}
					
					while(i+1<ledgerList.size() && firstLedger.getDate().getDate()==ledgerList.get(i+1).getDate().getDate()){

						LendingLedger secondLedger=ledgerList.get(i+1);
						if(secondLedger.getAmount()>0){
							localSettlementList.add(new IndividualSettlement(secondLedger.getDate(),secondLedger.getAmount(),null,getMode(secondLedger.getAdjustmentMode())));
							positiveSum+=secondLedger.getAmount();
						}
						else {
							negativeSum+=secondLedger.getAmount();
							ediDue+=-1*secondLedger.getAmount();
						}
						i++;

					}
					Double negativeAmount=ediDue+dueAmount;
					localSettlementList.forEach(settlemet->settlemet.setEdiDue(negativeAmount));
					if(localSettlementList.isEmpty()) {
						localSettlementList.add(new IndividualSettlement(firstLedger.getDate(),0D,negativeAmount,"Settlement"));
					}
					settlementsList.addAll(localSettlementList);
					if((positiveSum+negativeSum)<0) {
						dueAmount=-1*(positiveSum+negativeSum);		
					}
					else {
							positiveSum=0D;
							negativeSum=0D;
							dueAmount=0D;
					}
				}
				settlementsList.sort((x,y)->y.getDate().compareTo(x.getDate()));
				return settlementsList;
			}
			else {
				return null;
			}
		}
		catch(Exception e) {
			logger.error("Error occured while getting term loan edi payment history",e);
			return null;
		}
	}
	
	public String getMode(String mode) {
		if(mode==null) {
			return "Settlement";
		}
		else if(mode.equals("SETTLEMENT")){
			return "QR deduction";
		}
		else {
			return CreditConstants.SpendModeFrontEndFormat.getOrDefault(mode,mode);
		}
	}
	
	public Double getPastAvailableLimit(LendingClTransaction lendingClTransaction){
		try {
			Calendar date = Calendar.getInstance();
			date.setTime(lendingClTransaction.getCreatedAt());
			date.set(Calendar.HOUR_OF_DAY, 0);
			date.set(Calendar.MINUTE, 0);
			date.set(Calendar.SECOND, 0);
			date.set(Calendar.MILLISECOND, 0);
			Date startTime=date.getTime();
			date.set(Calendar.HOUR_OF_DAY, 23);
			date.set(Calendar.MINUTE, 59);
			date.set(Calendar.SECOND, 59);
			Date endTime=date.getTime();
			logger.info("Fetching end day details for date {}",startTime);
			List<CreditDayEndBalance> creditDayEndBalanceList=creditDayEndBalanceDao.findByAccountIdBetweenDate(lendingClTransaction.getCreditAccountId(),startTime,endTime);
			//for one day there will only be one entry in endDayBalance table
			if(creditDayEndBalanceList.isEmpty()) {
				return null;
			}
			CreditDayEndBalance creditDayEndBalance=creditDayEndBalanceList.get(0);
			return creditDayEndBalance.getAvailableBalance();
		}
		catch(Exception e) {
			logger.error("Error occured while fetching available limit on {}",lendingClTransaction.getCreatedAt());
			return null;
		}
	}
	
	public CreditLineClHistoryResponseDto getClHistory(Long id, BasicDetailsDto merchant) {
		try {
			Optional<LendingClTransaction> optional=lendingClTransactionDao.findById(id);
			if(optional!=null && optional.isPresent()) {
				
				CreditLineClHistoryResponseDto creditLineClHistoryResponseDto=new CreditLineClHistoryResponseDto();
				LendingClTransaction lendingClTransaction=optional.get();
				creditLineClHistoryResponseDto.setAmount(lendingClTransaction.getAmount());
				creditLineClHistoryResponseDto.setDate(lendingClTransaction.getCreatedAt());
				if(CreditConstants.SpendModeFrontEndFormat.containsKey(lendingClTransaction.getSubType())) {
					creditLineClHistoryResponseDto.setSpendMode(CreditConstants.SpendModeFrontEndFormat.get(lendingClTransaction.getSubType()));
				}
				else {
					creditLineClHistoryResponseDto.setSpendMode(lendingClTransaction.getSubType());
				}
				
				creditLineClHistoryResponseDto.setTransactionId(lendingClTransaction.getBankReferenceId());
				creditLineClHistoryResponseDto.setStatus(lendingClTransaction.getStatus());
				Double availaleLimit=getPastAvailableLimit(lendingClTransaction);
				if(availaleLimit==null){
					
					Optional<CreditAccount> creditOptional=creditAccountDao.findById(lendingClTransaction.getCreditAccountId());
					if(creditOptional==null || !creditOptional.isPresent()) {
						return getClHistoryErrorMessage("Credit account not found");
					}
					creditLineClHistoryResponseDto.setAvailableLimit(creditOptional.get().getAvailableBalance());
				}
				else {
					creditLineClHistoryResponseDto.setAvailableLimit(availaleLimit);
				}
				
				Narration narration=null;
				if(lendingClTransaction.getSubType().equalsIgnoreCase("BANK_TRANSFER")) {
					narration=getLoanDetailForBankTransfer(lendingClTransaction);
				}
				else if(lendingClTransaction.getSubType().equalsIgnoreCase("SEND_MONEY")){
					narration=getLoanDetailForSendMoney(lendingClTransaction);
				}
				else if(lendingClTransaction.getSubType().equalsIgnoreCase("BILL_PAYMENT")) {
					narration=getLoanDetailForBillPayment(lendingClTransaction);
				}
				else {
					narration=getGeneralNarration(lendingClTransaction);
				}
				if(narration==null){
					logger.error("Narration not found");
					return getClHistoryErrorMessage("Error occured while fetching transaction detail");
				}
				creditLineClHistoryResponseDto.setDetail(narration);
				return creditLineClHistoryResponseDto;
			}
			else {
				return getClHistoryErrorMessage("Details not found");
			}
		}
		catch(Exception e) {
			logger.error("Error occured while fetching transaction detail",e);
			return getClHistoryErrorMessage("Error occured while fetching transaction detail");
		}
	}
	
	public Narration getLoanDetailForBankTransfer(LendingClTransaction lendingClTransaction) {
		try {
			Narration detail=new Narration();
			Ifsc bank=ifscDao.findTop1ByIfscOrderByIdDesc(lendingClTransaction.getIfscCode());
			if(bank==null) {
				logger.warn("No bank found for the ifsc",lendingClTransaction.getIfscCode());
				return null;
			}
			detail.setNarrationHeading("Transferred to following bank A/c:");
			detail.setNarration1("Mr "+lendingClTransaction.getBeneficiaryName());
			detail.setNarration2("XX-"+lendingClTransaction.getAccountNumber() + " (" + bank.getBank() + ")");
			detail.setNarration3("Branch - " + bank.getBranch());
			detail.setIcon(lendingClTransaction.getIcon());
			
			return detail;
			
		}
		catch(Exception e){
			logger.error("Error occured while fetching bank transfer details",e);
			return null;
		}
	}
	
	public Narration getLoanDetailForSendMoney(LendingClTransaction lendingClTransaction) {
		try {
			
			Narration detail=new Narration();
			detail.setNarrationHeading("Sent to the following Mobile No:");
			detail.setNarration1(lendingClTransaction.getNarration1());
			detail.setNarration2("+91 "+lendingClTransaction.getNarration2());
			detail.setIcon(lendingClTransaction.getIcon());
			
			return detail;
			
		}
		catch(Exception e){
			logger.error("Error occured while fetching send money details",e);
			return null;
		}
	}
	
	public Narration getLoanDetailForBillPayment(LendingClTransaction lendingClTransaction) {
		try {
			Narration detail=new Narration();
			//header to be changed 
			detail.setNarrationHeading("Bill paid for:");
			detail.setNarration1(lendingClTransaction.getNarration1());
			detail.setNarration2(lendingClTransaction.getNarration2());
			detail.setNarration3(lendingClTransaction.getNarration3());
			detail.setIcon(lendingClTransaction.getIcon());
			
			return detail;
		}
		catch(Exception e) {
			logger.error("Error occured while fetching send mone",e);
			return null;
		}
	}
	
	public Narration getGeneralNarration(LendingClTransaction lendingClTransaction) {
		try {
			Narration detail=new Narration();
			detail.setNarrationHeading("Following transaction done:");
			detail.setNarration1(lendingClTransaction.getNarration1());
			detail.setNarration2(lendingClTransaction.getNarration2());
			detail.setNarration3(lendingClTransaction.getNarration3());
			detail.setIcon(lendingClTransaction.getIcon());
			
			return detail;
		}
		catch(Exception e) {
			logger.error("Error occured while fetching send mone",e);
			return null;
		}
	}
	
	public CreditLineClHistoryResponseDto getClHistoryErrorMessage(String message) {
		
		CreditLineClHistoryResponseDto creditLineClHistoryResponseDto=new CreditLineClHistoryResponseDto();
		creditLineClHistoryResponseDto.setSuccess(false);
		creditLineClHistoryResponseDto.setMessage(message);
		
		return creditLineClHistoryResponseDto;
	}

}