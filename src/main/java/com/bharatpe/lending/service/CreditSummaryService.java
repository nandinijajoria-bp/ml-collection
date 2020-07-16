package com.bharatpe.lending.service;

import java.util.ArrayList;
import java.util.Calendar;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.common.dao.CreditAccountDao;
import com.bharatpe.lending.common.dao.LendingClTransactionDao;
import com.bharatpe.lending.common.entity.CreditAccount;
import com.bharatpe.lending.common.entity.LendingClTransaction;
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


	@SuppressWarnings("deprecation")
	public SummaryResponseDTO getSummary(Merchant merchant) {

		List<LendingClTransaction>lendingClTransactionlist=lendingClTransactionDao.findByMerchantIdAndStatusOrderByCreatedAtDesc(merchant.getId(),"SUCCESS");
		List<LendingLedger>lendingLedgerlist=lendingLedgerDao.findByMerchantIdOrderByDateDesc(merchant.getId());
		Map<Date,LendingLedger> ledgerlist= populatelist(lendingLedgerlist);

		CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
		SummaryResponseDTO summaryResponse=new  SummaryResponseDTO();
		summaryResponse.setSuccess(false);
		summaryResponse.setMessage("no summary found");
		if(creditAccount==null)
			return summaryResponse;

		summaryResponse.setAvaliableAmount(creditAccount.getAvailableBalance());
//	if(lendingClTransactionlist!=null&&lendingClTransactionlist.size()==0)
//		return summaryResponse;

		int day=-1;
		SummaryResponseDTO.Summary summary=null;
		SummaryResponseDTO.Transactions transaction=null;
		List<Summary>summarylist=new  ArrayList<>();
		List<Transactions>transactionslist=null;

		for(LendingClTransaction lendingClTransaction:lendingClTransactionlist) {

			Date date=lendingClTransaction.getCreatedAt();
			if(day!=date.getDay())
			{
				if(transactionslist!=null)
				{
					summary.setTransactionslist(transactionslist);
					summarylist.add(summary);
				}
				transactionslist=new  ArrayList<>();
				day=date.getDay();
				summary=new SummaryResponseDTO.Summary();
				summary.setDate(getStartTimeFromDateTime(date));
				transaction=new  SummaryResponseDTO.Transactions();
				transaction.setMode(lendingClTransaction.getMode());
				if(CreditConstants.ChargesType.containsKey(lendingClTransaction.getType())) {
					transaction.setType(CreditConstants.ChargesType.get(lendingClTransaction.getType()));
				}
				else {
					String subType=lendingClTransaction.getSubType();
					if(CreditConstants.SpendModeFrontEndFormat.get(subType)!=null)
						transaction.setType(CreditConstants.SpendModeFrontEndFormat.get(subType));
					else
						transaction.setType(subType);
					if (CreditConstants.PaymentType.PAYMENT.name().equalsIgnoreCase(lendingClTransaction.getType())) {
						transaction.setType("Repayment (" + transaction.getType() + ")");
					}
				}
				transaction.setAmount(lendingClTransaction.getAmount());
				transactionslist.add(transaction);

				if(ledgerlist!=null&&ledgerlist.get(getStartTimeFromDateTime(date))!=null)
				{
					transaction=new  SummaryResponseDTO.Transactions();
					transaction.setMode("CREDIT");

					String subType=ledgerlist.get(getStartTimeFromDateTime(date)).getAdjustmentMode();
					if(CreditConstants.SpendModeFrontEndFormat.get(subType)!=null)
						transaction.setType(CreditConstants.SpendModeFrontEndFormat.get(subType));
					else
						transaction.setType(subType);
					if (CreditConstants.PaymentType.PAYMENT.name().equalsIgnoreCase(lendingClTransaction.getType())) {
						transaction.setType("Repayment (" + transaction.getType() + ")");
					}
					transaction.setAmount(ledgerlist.get(getStartTimeFromDateTime(date)).getAmount());
					transactionslist.add(transaction);
					ledgerlist.remove(getStartTimeFromDateTime(date));
				}
			}

			else
			{
				transaction=new  SummaryResponseDTO.Transactions();
				transaction.setMode(lendingClTransaction.getMode());
				if(CreditConstants.ChargesType.containsKey(lendingClTransaction.getType())) {
					transaction.setType(CreditConstants.ChargesType.get(lendingClTransaction.getType()));
				}
				else {
					String subType=lendingClTransaction.getSubType();
					if(CreditConstants.SpendModeFrontEndFormat.get(subType)!=null)
						transaction.setType(CreditConstants.SpendModeFrontEndFormat.get(subType));
					else
						transaction.setType(subType);
					if (CreditConstants.PaymentType.PAYMENT.name().equalsIgnoreCase(lendingClTransaction.getType())) {
						transaction.setType("Repayment (" + transaction.getType() + ")");
					}
				}
				transaction.setAmount(lendingClTransaction.getAmount());
				transactionslist.add(transaction);
			}

		}
		summary.setTransactionslist(transactionslist);
		summarylist.add(summary);

		if(ledgerlist!=null&&ledgerlist.size()>0) {
			for(Map.Entry<Date,LendingLedger> entry :ledgerlist.entrySet())
			{
				transactionslist=new  ArrayList<>();
				summary=new SummaryResponseDTO.Summary();
				summary.setDate((entry.getKey()));
				transaction=new  SummaryResponseDTO.Transactions();
				transaction.setMode("CREDIT");
				String subType=entry.getValue().getAdjustmentMode();
				if(CreditConstants.SpendModeFrontEndFormat.get(subType)!=null)
					transaction.setType(CreditConstants.SpendModeFrontEndFormat.get(subType));
				else
					transaction.setType(subType);

				transaction.setAmount(entry.getValue().getAmount());
				transactionslist.add(transaction);
				summary.setTransactionslist(transactionslist);
				summarylist.add(summary);
			}
		}

		summaryResponse.setSummarylist(summarylist);
		summarylist.sort((x,y)->y.getDate().compareTo(x.getDate()));
		summaryResponse.setSuccess(true);
		summaryResponse.setMessage("");
		return summaryResponse;

	}


	public  static Map<Date,LendingLedger> populatelist(List<LendingLedger>lendingLedgerlist) {

		Map<Date,LendingLedger> ans=new HashMap<>();
		if(lendingLedgerlist==null||lendingLedgerlist.size()==0)
			return null;
		for(LendingLedger lendingLedger:lendingLedgerlist)
		{
			ans.put(getStartTimeFromDateTime(lendingLedger.getDate()), lendingLedger);
		}
		return ans;

	}
	public static Date getStartTimeFromDateTime(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		return cal.getTime();
	}



}
