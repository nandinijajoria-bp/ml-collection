package com.bharatpe.lending.constant;

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
	
}
