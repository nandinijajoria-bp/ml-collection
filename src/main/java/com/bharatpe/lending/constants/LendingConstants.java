package com.bharatpe.lending.constants;

public interface LendingConstants {
	String GUPSHUP_OTP_API_USERID = "2000182191";
	String GUPSHUP_OTP_API_PASSWORD = "uelCIwOHu";
	String GUPSHUP_SMS_SERVICE_URL = "https://enterprise.smsgupshup.com/GatewayAPI/rest";
	
	String GUPSHUP_SEND_OTP_METHOD= "TWO_FACTOR_AUTH";
	String GUPSHUP_VERIFY_OTP_METHOD= "TWO_FACTOR_AUTH";
	String GUPSHUP_API_VERSION = "1.1";
	
	
	String AWS_S3_ACCESS_KEY = "AKIA2INV2XVSLUEBQ4DH";
	String AWS_S3_SECRET_KEY = "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";
	String AWS_S3_BUCKET_NAME = "bharatpe-staging/lending-document";
	
	String X_KARZA_KEY = "IEPHvT1bUPTf4Ow";
	
	String KARZA_PAN_AUTHENTICATION_URL = "https://api.karza.in/v2/pan-authentication";
	String KARZA_KYC_URL = "https://api.karza.in/v3/ocr/kyc";
}
