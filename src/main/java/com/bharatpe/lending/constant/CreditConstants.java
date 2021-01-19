package com.bharatpe.lending.constant;

import java.util.HashMap;
import java.util.Map;

public interface CreditConstants {

	String SIGNZY_LOGIN_URL ="/v2/patrons/login";
	String SIGNZY_IDENTITY_URL ="/v2/patrons";
	String SIGNZY_SNOOP_URL ="/v2/snoops";
	String SIGNZY_FILEEXCHANGE_URL="/base64";
	String PAYMENT_MODE_URL="/bp_balance/payment_modes";
	String BP_BALANCE_CREATE_TXN_URL="/internal/bharatpe_balance/initiate";
	String BP_BALANCE_CONFIRM_TXN_URL="/internal/bharatpe_balance/confirm";
	String BP_BALANCE_RESEND_OTP_URL="/internal/bharatpe_balance/resend_otp";
	String PAYOUT_URL = "http://payout-java.bharatpe.in/lending/payout";
	String APP_NOTIFICATION_DEEPLINK="bharatpe://dynamic?key=credit-line";
	String MESSAGE_NOTIFICATION_LINK="bharatpe.in/creditline";
	String SIGNZY_NAME_MATCH_URL="/v2/patrons/";

	enum SpendMode {
		BANK_TRANSFER, SEND_MONEY, BILL_PAYMENT, ECOMMERCE, CASH_WITHDRAWAL, QR_SETTLEMENT, INTEREST_ACCOUNT, UPI
	}
	enum PaymentType {
		PAYMENT, INTEREST, PENALTY, CL, TL, REFUND, ROLLBACK
	}

	enum PaymentStatus {
		INIT,
		PENDING,
		SUCCESS,
		FAILED,
		CALLBACK_RECEIVED
	}

	enum PaymentSource {
		FP,
		UNSETTLED,
		UPI,
		BT
	}

	enum PaymentMode {
		BPB,
		UPI,
		BT
	}

	enum AccountStatus {
		ACTIVE,
		BLOCKED,
		INACTIVE
	}

	static boolean validSpendMode(String mode) {
		for (SpendMode s : SpendMode.values()) {
			if (s.name().equals(mode)) {
				return true;
			}
		}
		return false;
	}

	Map<String , String> SpendGroup = new HashMap<String , String>() {{
		put("BANK_TRANSFER", "G1");
		put("SEND_MONEY", "G2");
		put("BILL_PAYMENT", "G1");
		put("ECOMMERCE", "G1");
		put("CASH_WITHDRAWAL", "G3");
	}};
	
	Map<String , String> SpendModeFrontEndFormat = new HashMap<String , String>() {{
		put("BANK_TRANSFER", "Bank Transfer");
		put("SEND_MONEY", "Send Money");
		put("BILL_PAYMENT", "Bill Payment");
		put("ECOMMERCE", "E-commerce");
		put("CASH_WITHDRAWAL", "Cash Withdrawal");
		put("QR_SETTLEMENT", "QR Settlement");
		put("INTEREST_ACCOUNT", "Interest Account");
		put("FP", "Interest Account");
		put("UPI", "UPI");
		put("upi", "UPI");
		put("Upi", "UPI");
		put("INSURANCE","Insurance");
		put("CASH","Cash");
		put("INTEREST","Interest");
		
	}};
	Map<String , String> ChargesType = new HashMap<String , String>() {{
		put("INTEREST","Interest Charged");
		put("PENALTY", "Penalty Charged");
		 
		
	}};

	Map<String,Double> EMI=new HashMap<String,Double>() {{
		put("AL",0.025D);
		put("PL",0.035D);
		put("HL",0.01D);
		put("BL",0.05D);
		put("CC",0.05D);
		put("TW",0.02D);
		put("CD",0.03D);
		put("GL",0.02D);
		put("Other",0.03D);
	}};

	Map<String,Double> DBI=new HashMap<String,Double>() {{
		put("AL",0.65D);
		put("PL",0.55D);
		put("HL",0.65D);
		put("BL",0.55D);
		put("CC",0.70D);
		put("TW",0.75D);
		put("CD",0.75D);
		put("GL",1D);
		put("Other",0.55D);
	}};

	Map<String,Double> OTHER_INCOME=new HashMap<String,Double>() {{
		put("HL",30000D);
		put("AL",20000D);
		put("PL",20000D);
		put("BL",20000D);
		put("CC",20000D);
		put("TW",15000D);
		put("CD",10000D);
		put("GL",10000D);
		put("Other",10000D);
	}};
}
