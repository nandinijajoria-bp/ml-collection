package com.bharatpe.lending.constant;

import java.util.Arrays;
import java.util.List;

public interface LendingConstants {
	String GUPSHUP_OTP_API_USERID = "2000182191";
	String GUPSHUP_OTP_API_PASSWORD = "uelCIwOHu";
	String GUPSHUP_SMS_SERVICE_URL = "https://enterprise.smsgupshup.com/GatewayAPI/rest";
	
	String GUPSHUP_SEND_OTP_METHOD= "TWO_FACTOR_AUTH";
	String GUPSHUP_VERIFY_OTP_METHOD= "TWO_FACTOR_AUTH";
	String GUPSHUP_API_VERSION = "1.1";
	
	String X_KARZA_KEY = "IEPHvT1bUPTf4Ow";
	
	String KARZA_PAN_AUTHENTICATION_URL = "https://api.karza.in/v2/pan-authentication";
	String KARZA_KYC_URL = "https://api.karza.in/v3/ocr/kyc";
	
	String LOAN_APPLICATION_SUCCESS_CODE="BP_200";
	String LOAN_APPLICATION_OGL_CODE="BP_405";
	String LOAN_APPLICATION_SUCCESS_MESSAGE="Successful";
	String LOAN_APPLICATION_OGL_MESSAGE="OGL CASE";
	String DIGIO_ENACH_INITIATION_URL="https://api.digio.in/v3/client/mandate/create_form";
	String DIGIO_ENACH_STATUS_CHECK="https://api.digio.in/v3/client/mandate/";
	int APPLICATION_DEROG_RECHECK_MIN_DAYS = 60;
	String BHARATSWIPE_NEWUSER_IMG="https://d30gqtvesfc1d5.cloudfront.net/ext/product_entry_img/bharatswipe_entry.png";
	String BHARATSWIPE_NEWUSER_DEEPLINK="bharatpe://dynamic?key=bharatpeswipe";
	String FPACCOUNT_NEWUSER_IMG="https://d30gqtvesfc1d5.cloudfront.net/ext/product_entry_img/fp_account_entry.png";
	String FPACCOUNT_NEWUSER_DEEPLINK="bharatpe://dynamic?key=interest_account";
	String ENACH_BANK_MESSAGE="<p class='inel-note'>Note: Your Bank A/C is not eNACH-able and our lending partners don’t process a loan application in this Bank A/C. Please change your Bank A/C by clicking <b>here</b> and try again.</p>";
	String BANK_CHANGE_DEEPLINK="bharatpe://dynamic?key=change_bank";
	String COLLECT_VPA_CREATE_TXN_URL = "/startTxn";
	enum BUREAU_TYPES {
        CRIF, EXPERIAN;
    }
    List<String> FOOD_BEVERAGES = Arrays.asList("Bakery_Namkeen_Sweets","Bakery_Namkeen_Sweets","Food_and_Drink","Fast_Food_Cafe_QSR","Food_","Fast Food_Café_QSR","Fast Food_Cafe_QSR","Food_&_Beverages","Food_Court","Food Court","Ice Cream Vendor","Ice Cream Vendor","Ice Cream Vendor","Ice Cream Vendor","Juice Shop","Juice_Shop","Juice Shop","Restaurant_Fine_Dining","Restaurant_Fine Dining","Roadside_Eatery_Stall_Truck","Roadside Eatery_Stall_Truck","Roadside Eatery_Stall_Truck","Roadside Eatery_Stall_Truck","School_College Canteen","Take Away_Home Delivery");
	List<String> CPV_CITIES = Arrays.asList("Hyderabad", "Bengaluru", "Pune", "Delhi", "Warangal", "Jaipur", "Karimnagar", "Mumbai", "Chennai", "Kolkata", "Faridabad", "Gautam Buddh Nagar", "Ghaziabad", "Gurgaon");
}


