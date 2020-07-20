package com.bharatpe.lending.service;

import java.util.Date;
import java.util.List;

import com.bharatpe.lending.util.CreditUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.lending.common.dao.CreditAccountBillDao;
import com.bharatpe.lending.common.dao.CreditAccountDao;
import com.bharatpe.lending.common.dao.LendingClTransactionDao;
import com.bharatpe.lending.common.entity.CreditAccount;
import com.bharatpe.lending.common.entity.CreditAccountBill;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.CreditLimitWidget;
import com.bharatpe.lending.dto.DashboardDetailsResponseDto;
import com.bharatpe.lending.dto.RepaymentWidget;
import com.bharatpe.lending.dto.SpendModeWidget;

@Service
public class CreditLineDashboardDetailsService {
	
	@Autowired
	CreditAccountDao creditAccountDao;
	
	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;
	
	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Autowired
	LendingClTransactionDao lendingClTransactionDao;
	
	@Autowired
	CreditAccountBillDao creditAccountBillDao;
	
	@Autowired
	CreditLineService creditLineService;

	@Autowired
	CreditUtil creditUtil;
	
	Logger logger=LoggerFactory.getLogger(CreditLineDashboardDetailsService.class); 
	
	public DashboardDetailsResponseDto getDetailsForDashboard(Merchant merchant){
		
		DashboardDetailsResponseDto dashboardResponse;
		
		try {
		
			if(merchant==null || merchant.getId()==null){
				
				logger.error("Merchant not found");
				
				return getErrorResponse("Bad request");
				
			}
		
			dashboardResponse=populateCreditLineDetails(merchant);
		
		}
		catch(Exception e) {
			
			logger.error("Error occured while fetching dashboard data");
			
			return getErrorResponse("Error occured while fetching dashboard details");
			
		}
		
		return dashboardResponse;
	}
	
	public DashboardDetailsResponseDto getErrorResponse(String message) {
		
		DashboardDetailsResponseDto errorResponse=new DashboardDetailsResponseDto();
		
		errorResponse.setSuccess(false);
		errorResponse.setMessage(message);
		
		return errorResponse;
		
	}
	
	public Boolean areTransactionDone(CreditAccount creditAccount) {
		try {
			Long count=lendingClTransactionDao.getCountOfTransactions(creditAccount.getId());
			if(count!=null) {
				return count!=0;
			}
			else {
				logger.error("Getting null as the count of transactions done");
				return null;
			}
		}
		catch(Exception e){
			logger.error("Error occured while fetching number of transactions done via credit account {} {}",creditAccount.getId(),e);
			return null;
		}
	}
	
	public DashboardDetailsResponseDto populateCreditLineDetails(Merchant merchant){
		
		DashboardDetailsResponseDto dashboardResponse=new DashboardDetailsResponseDto();
		
		try {
			
			logger.info("Fetching credit account details for merchant {}",merchant.getId());
			CreditAccount creditAccount=creditAccountDao.findByMerchantIdForDashBoard(merchant.getId());
			
			if(creditAccount==null) {
				
				logger.error("No credit account found for merchant {}",merchant.getId());
				
				return getErrorResponse("No credit account exists");
			}
			Boolean transactionDone=areTransactionDone(creditAccount);
			if(transactionDone==null) {
				return getErrorResponse("Error occured while checking for transactions");
			}
			dashboardResponse.setTransactionDone(transactionDone);
			logger.info("Fetching details for credit widget");
			CreditLimitWidget creditLimitWidget=new CreditLimitWidget();
			
			creditLimitWidget.setLimit(creditAccount.getLimit());
			creditLimitWidget.setAvailableLimit(Math.round(creditAccount.getAvailableBalance()));
			creditLimitWidget.setUsedLimit(Math.round(creditAccount.getUsedBalance()));
			
			dashboardResponse.setCreditWidget(creditLimitWidget);
			
			logger.info("Fetching details for repayment widget");
			
			RepaymentWidget repaymentWidget=new RepaymentWidget();
			
			Double ediAmount=getEdiAmount(merchant);
			
			if(ediAmount==null){
				
				logger.error("Error occured while calculating edi amount");
				
				return getErrorResponse("Error occured while calculating edi amount");
				
			}
			
			repaymentWidget.setBillDate(creditAccount.getNextBillDate());
			repaymentWidget.setEdiAmount(ediAmount);
			repaymentWidget.setMinDueAmount(creditAccount.getMinimumAmountDue());
			repaymentWidget.setPayableAmount(creditUtil.getPayableAmount(creditAccount));
			CreditAccountBill bill=creditAccountBillDao.findTop1ByAccountIdOrderByIdDesc(creditAccount.getId());
			if(bill!=null && !bill.getBillPaid()) {
				repaymentWidget.setDueDate(bill.getDueDate());
				repaymentWidget.setBillId(bill.getId());
			}
			else if(bill!=null && bill.getBillPaid()){
				repaymentWidget.setDueDate(creditAccount.getDueDate());
				repaymentWidget.setBillId(bill.getId());
			}
			else {
				repaymentWidget.setDueDate(creditAccount.getDueDate());
			}
			
			dashboardResponse.setRepaymentWidget(repaymentWidget);
			dashboardResponse.setSpendModeWidget(new SpendModeWidget());
			dashboardResponse=responseAfterInsertingMerchantBankDetails(merchant, dashboardResponse);
			if(creditAccount.getStatus().equalsIgnoreCase("BLOCKED")){
				dashboardResponse.setAccountBlocked(true);
			}
			
			return dashboardResponse;
			
		}
		catch(Exception e) {
			
			logger.error("Error occured while getting details for dashboard",e);
			
			return getErrorResponse("Error occured while fetching dash board details");

		}
		
	}
	
	public DashboardDetailsResponseDto responseAfterInsertingMerchantBankDetails(Merchant merchant, DashboardDetailsResponseDto dashboardResponse) {
		
		try {
			
			 logger.info("Getting merchant bank details for merchant id {}",merchant.getId());
			 
			 MerchantBankDetail merchantBankDetail=merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			 
			 if(merchantBankDetail==null){
				 
					logger.error("Bank details not available for the merchant {}",merchant.getId());
					return getErrorResponse("Bank details not found");
					
				}
			 
			 dashboardResponse.setMerchantName(merchantBankDetail.getBeneficiaryName());
			 dashboardResponse.setMerchantBankName(merchantBankDetail.getBankName());
			 dashboardResponse.setMerchantAccNo(merchantBankDetail.getAccountNumber());
			 
			 return dashboardResponse;
		}
		catch(Exception e) {
			
			logger.error("Error occured while fetching merchant bank details",e);
			return getErrorResponse("Error occured while fetching merchant bank details");
			
		}
		
	}
	
	public Double getEdiAmount(Merchant merchant){
		
		Double ediAmount=0D;
		try{
			
			logger.info("Fetching lending payment schedule details for merchantId {}",merchant.getId());
			List<LendingPaymentSchedule> lendingPaymentScheduleList=lendingPaymentScheduleDao.findByMerchantIdAndStatusAndCreditLoan(merchant.getId(), "ACTIVE",true);
			
			if(lendingPaymentScheduleList!=null) {
				for(LendingPaymentSchedule paymentSchedule:lendingPaymentScheduleList){
					if(paymentSchedule.getEdiAmount()!=null && DateTimeUtil.getCurrentDayStartTime().compareTo(DateTimeUtil.getStartTimeFromDateTime(paymentSchedule.getCreatedAt()))!=0) {
						ediAmount+=paymentSchedule.getDueAmount();
					}
				}
			}
			
		}
		catch(Exception e){
			logger.error("Error occured while calculation edi amount for merchant {}",merchant.getId(),e);
			return null;
		}
		
		return ediAmount;
	}
	
}
