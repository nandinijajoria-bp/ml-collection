package com.bharatpe.lending.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.common.dao.CreditAccountDao;
import com.bharatpe.lending.common.dao.LendingClTransactionDao;
import com.bharatpe.lending.common.entity.CreditAccount;
import com.bharatpe.lending.common.entity.LendingClTransaction;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dto.SummaryResponseDTO;
import com.bharatpe.lending.dto.SummaryResponseDTO.Summary;
import com.bharatpe.lending.dto.SummaryResponseDTO.Transactions;

@Service
public class CreditSummaryService {

	@Autowired
	LendingClTransactionDao  lendingClTransactionDao;

	@Autowired
	LendingLedgerDao  lendingLedgerDao;

	@Autowired
	CreditAccountDao creditAccountDao;

	Logger logger=LoggerFactory.getLogger(CreditSummaryService.class);

//	@SuppressWarnings("deprecation")
//	public SummaryResponseDTO getSummary(Merchant merchant) {
//
//		List<LendingClTransaction>lendingClTransactionlist=lendingClTransactionDao.findByMerchantIdAndStatusOrderByCreatedAtDesc(merchant.getId(),"SUCCESS");
//		List<LendingLedger>lendingLedgerlist=lendingLedgerDao.findByMerchantIdOrderByDateDesc(merchant.getId());
//		Map<Date,LendingLedger> ledgerlist= populatelist(lendingLedgerlist);
//
//		CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
//		SummaryResponseDTO summaryResponse=new  SummaryResponseDTO();
//		summaryResponse.setSuccess(false);
//		summaryResponse.setMessage("no summary found");
//		if(creditAccount==null)
//			return summaryResponse;
//
//		summaryResponse.setAvaliableAmount(creditAccount.getAvailableBalance());
////	if(lendingClTransactionlist!=null&&lendingClTransactionlist.size()==0)
////		return summaryResponse;
//
//		int day=-1;
//		SummaryResponseDTO.Summary summary=null;
//		SummaryResponseDTO.Transactions transaction=null;
//		List<Summary>summarylist=new  ArrayList<>();
//		List<Transactions>transactionslist=null;
//
//		for(LendingClTransaction lendingClTransaction:lendingClTransactionlist) {
//
//			Date date=lendingClTransaction.getCreatedAt();
//			if(day!=date.getDay())
//			{
//				if(transactionslist!=null)
//				{
//					summary.setTransactionslist(transactionslist);
//					summarylist.add(summary);
//				}
//				transactionslist=new  ArrayList<>();
//				day=date.getDay();
//				summary=new SummaryResponseDTO.Summary();
//				summary.setDate(getStartTimeFromDateTime(date));
//				transaction=new  SummaryResponseDTO.Transactions();
//				transaction.setMode(lendingClTransaction.getMode());
//				if(CreditConstants.ChargesType.containsKey(lendingClTransaction.getType())) {
//					transaction.setType(CreditConstants.ChargesType.get(lendingClTransaction.getType()));
//				}
//				else {
//					String subType=lendingClTransaction.getSubType();
//					if(CreditConstants.SpendModeFrontEndFormat.get(subType)!=null)
//						transaction.setType(CreditConstants.SpendModeFrontEndFormat.get(subType));
//					else
//						transaction.setType(subType);
//					if (CreditConstants.PaymentType.PAYMENT.name().equalsIgnoreCase(lendingClTransaction.getType())) {
//						transaction.setType("Repayment (" + transaction.getType() + ")");
//					}
//				}
//				transaction.setAmount(lendingClTransaction.getAmount());
//				transactionslist.add(transaction);
//
//				if(ledgerlist!=null&&ledgerlist.get(getStartTimeFromDateTime(date))!=null)
//				{
//					transaction=new  SummaryResponseDTO.Transactions();
//					transaction.setMode("CREDIT");
//
//					String subType=ledgerlist.get(getStartTimeFromDateTime(date)).getAdjustmentMode();
//					if(CreditConstants.SpendModeFrontEndFormat.get(subType)!=null)
//						transaction.setType(CreditConstants.SpendModeFrontEndFormat.get(subType));
//					else
//						transaction.setType(subType);
//					if (CreditConstants.PaymentType.PAYMENT.name().equalsIgnoreCase(lendingClTransaction.getType())) {
//						transaction.setType("Repayment (" + transaction.getType() + ")");
//					}
//					transaction.setAmount(ledgerlist.get(getStartTimeFromDateTime(date)).getAmount());
//					transactionslist.add(transaction);
//					ledgerlist.remove(getStartTimeFromDateTime(date));
//				}
//			}
//
//			else
//			{
//				transaction=new  SummaryResponseDTO.Transactions();
//				transaction.setMode(lendingClTransaction.getMode());
//				if(CreditConstants.ChargesType.containsKey(lendingClTransaction.getType())) {
//					transaction.setType(CreditConstants.ChargesType.get(lendingClTransaction.getType()));
//				}
//				else {
//					String subType=lendingClTransaction.getSubType();
//					if(CreditConstants.SpendModeFrontEndFormat.get(subType)!=null)
//						transaction.setType(CreditConstants.SpendModeFrontEndFormat.get(subType));
//					else
//						transaction.setType(subType);
//					if (CreditConstants.PaymentType.PAYMENT.name().equalsIgnoreCase(lendingClTransaction.getType())) {
//						transaction.setType("Repayment (" + transaction.getType() + ")");
//					}
//				}
//				transaction.setAmount(lendingClTransaction.getAmount());
//				transactionslist.add(transaction);
//			}
//
//		}
//		if(summary!=null) {
//			summary.setTransactionslist(transactionslist);
//			summarylist.add(summary);
//		}
//
//		if(ledgerlist!=null&&ledgerlist.size()>0) {
//			for(Map.Entry<Date,LendingLedger> entry :ledgerlist.entrySet())
//			{
//				transactionslist=new  ArrayList<>();
//				summary=new SummaryResponseDTO.Summary();
//				summary.setDate((entry.getKey()));
//				transaction=new  SummaryResponseDTO.Transactions();
//				transaction.setMode("CREDIT");
//				String subType=entry.getValue().getAdjustmentMode();
//				
//				if(CreditConstants.SpendModeFrontEndFormat.get(subType)!=null)
//					transaction.setType(CreditConstants.SpendModeFrontEndFormat.get(subType));
//				else {
//					if(subType==null) {
//						subType="SETTLEMENT";
//					}
//					transaction.setType(subType);
//				}	
//				if (entry.getValue().getAmount()>0) {
//					transaction.setType("Repayment (" + transaction.getType() + ")");
//				}
//				transaction.setAmount(entry.getValue().getAmount());
//				transactionslist.add(transaction);
//				summary.setTransactionslist(transactionslist);
//				summarylist.add(summary);
//			}
//		}
//
//		summaryResponse.setSummarylist(summarylist);
//		summarylist.sort((x,y)->y.getDate().compareTo(x.getDate()));
//		summaryResponse.setSuccess(true);
//		summaryResponse.setMessage("");
//		return summaryResponse;
//
//	}
//
//
//	public  static Map<Date,LendingLedger> populatelist(List<LendingLedger>lendingLedgerlist) {
//
//		Map<Date,LendingLedger> ans=new HashMap<>();
//		if(lendingLedgerlist==null||lendingLedgerlist.size()==0)
//			return null;
//		for(LendingLedger lendingLedger:lendingLedgerlist)
//		{
//			if(lendingLedger.getAdjustmentMode()!=null && lendingLedger.getAdjustmentMode().equalsIgnoreCase("UPI")) {
//				continue;
//			}
//			ans.put(getStartTimeFromDateTime(lendingLedger.getDate()), lendingLedger);
//		}
//		return ans;
//
//	}
//	public static Date getStartTimeFromDateTime(Date date) {
//		Calendar cal = Calendar.getInstance();
//		cal.setTime(date);
//		cal.set(Calendar.HOUR_OF_DAY, 0);
//		cal.set(Calendar.MINUTE, 0);
//		cal.set(Calendar.SECOND, 0);
//		return cal.getTime();
//	}

	public SummaryResponseDTO getSummary(Merchant merchant) {
		try {
			CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
			if(creditAccount!=null) {
				List<Transactions> ledgerTransaction=getTransactionFromLedger(creditAccount, merchant);
				List<Transactions> clTransaction=getTransactionFromLendingClTransaction(creditAccount, merchant);
				if(ledgerTransaction==null || clTransaction==null) {
					return new SummaryResponseDTO(false,"Error occured while fetching transaction");
				}
				return combineAllTransaction(ledgerTransaction, clTransaction, creditAccount);
			}
			else {
				logger.error("No credit account found for merchant {}",merchant);
				return new SummaryResponseDTO(false,"Credit Account not found");
			}
		}
		catch(Exception e) {
			logger.error("Error occured while fetching account summary ",e);
			return new SummaryResponseDTO(false, "Error occured while fetching account summary");
		}
	}
	
	public SummaryResponseDTO combineAllTransaction(List<Transactions> ledgerTransaction,List<Transactions> clTransaction, CreditAccount creditAccount) {
		Map<Date, List<Transactions>> transactionMap=new HashMap<>();
		
		for(Transactions transaction:ledgerTransaction) {
			if(transactionMap.containsKey(transaction.getDate())) {
				transactionMap.get(transaction.getDate()).add(transaction);
			}
			else {
				transactionMap.put(transaction.getDate(), new LinkedList<>());
				transactionMap.get(transaction.getDate()).add(transaction);
			}
		}
		
		for(Transactions transaction:clTransaction) {
			if(transactionMap.containsKey(transaction.getDate())) {
				transactionMap.get(transaction.getDate()).add(transaction);
			}
			else {
				transactionMap.put(transaction.getDate(), new LinkedList<>());
				transactionMap.get(transaction.getDate()).add(transaction);
			}
		}
		SummaryResponseDTO response=new SummaryResponseDTO(true,"");
		response.setAvaliableAmount(creditAccount.getAvailableBalance());
		response.setSummarylist(new LinkedList<>());
		for(Date date:transactionMap.keySet()) {
			Summary summary=new Summary();
			summary.setDate(date);
			summary.setTransactionslist(transactionMap.get(date));
			response.getSummarylist().add(summary);
		}
		response.getSummarylist().sort(Comparator.comparing(Summary::getDate).reversed());

		return response;
	}
	
	public List<Transactions> getTransactionFromLedger(CreditAccount creditAccount,Merchant merchant){
		try {
			List<LendingLedger> ledgerList=lendingLedgerDao.findByMerchantIdOrderByDateDesc(merchant.getId());
			List<Transactions> transactionList=new LinkedList<>();
			for(LendingLedger ledger:ledgerList) {
				if(ledger.getAmount()>=0 && (ledger.getDescription()==null || !ledger.getDescription().equalsIgnoreCase("CREDIT_LINE"))) {
					Transactions transaction=new Transactions();
					transaction.setAmount(ledger.getAmount());
					transaction.setMode("Repayment (" + ledger.getAdjustmentMode() + ")");
					transaction.setType("CREDIT");
					transaction.setDate(DateTimeUtil.getStartTimeFromDateTime(ledger.getDate()));
					transactionList.add(transaction);
				}
			}
			return transactionList;
		}
		catch(Exception e) {
			logger.error("Error occured while fetching transactions from ledger ",e);
			return null;
		}
	}
	
	public List<Transactions> getTransactionFromLendingClTransaction(CreditAccount creditAccount,Merchant merchant){
		try {
			List<LendingClTransaction>lendingClTransactionlist=lendingClTransactionDao.findByMerchantIdAndStatusOrderByCreatedAtDesc(merchant.getId(),"SUCCESS");
			List<Transactions> transactionList=new LinkedList<>();
			for(LendingClTransaction clTransaction:lendingClTransactionlist) {
				Transactions transaction=new Transactions();
				transaction.setAmount(clTransaction.getAmount());
				if(clTransaction.getMode().equalsIgnoreCase("CREDIT")) {
					transaction.setMode("Repayment (" + CreditConstants.SpendModeFrontEndFormat.getOrDefault(clTransaction.getSubType() , clTransaction.getSubType())+ ")");
				}
				else {
					transaction.setMode(CreditConstants.SpendModeFrontEndFormat.getOrDefault(clTransaction.getSubType() , clTransaction.getSubType()));
				}
				transaction.setType(clTransaction.getMode());
				transaction.setDate(DateTimeUtil.getStartTimeFromDateTime(clTransaction.getCreatedAt()));
				transactionList.add(transaction);
			}
			return transactionList;
		}
		catch(Exception e) {
			logger.error("Error occured while fetching transactions from lending_cl_transaction ",e);
			return null;
		}
	}

}
