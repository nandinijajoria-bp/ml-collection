package com.bharatpe.lending.constant;

import java.util.*;

public interface LendingConstants {
	Integer MAX_LOAN_AMOUNT_INTEGER = 1000000;
	Double MAX_LOAN_AMOUNT_DOUBLE = 1000000d;
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
	String BANK_CHANGE_DEEPLINK="bharatpe://dynamic?key=add-bank-account&wroute=easy-loans";
	String COLLECT_VPA_CREATE_TXN_URL = "/startTxn";
	enum BUREAU_TYPES {
        CRIF, EXPERIAN;
    }
    List<String> FOOD_BEVERAGES = Arrays.asList("Bakery_Namkeen_Sweets","Bakery_Namkeen_Sweets","Food_and_Drink","Fast_Food_Cafe_QSR","Food_","Fast Food_Café_QSR","Fast Food_Cafe_QSR","Food_&_Beverages","Food_Court","Food Court","Ice Cream Vendor","Ice Cream Vendor","Ice Cream Vendor","Ice Cream Vendor","Juice Shop","Juice_Shop","Juice Shop","Restaurant_Fine_Dining","Restaurant_Fine Dining","Roadside_Eatery_Stall_Truck","Roadside Eatery_Stall_Truck","Roadside Eatery_Stall_Truck","Roadside Eatery_Stall_Truck","School_College Canteen","Take Away_Home Delivery");

	String NACH_PROVIDER_URL = "/enach/provider";
	String INTERNAL_NACH_PROVIDER_URL = "/v2/internal/provider";
	String NACH_INITIATE_URL = "/enach/initiate";
	String NACH_SUBMIT_URL = "/enach/submit";
	String LENDING_REFUND_URL = "/refund/init";
	String LENDING_INCENTIVE_URL = "/incentive/init";
	List<String> CPV_CITIES = Arrays.asList("Hyderabad", "Bengaluru", "Pune", "Delhi", "Warangal", "Jaipur", "Karimnagar", "Mumbai", "Chennai", "Faridabad", "Gautam Buddh Nagar", "Ghaziabad", "Gurgaon", "Visakhapatnam", "Bhopal", "Indore", "Ahmedabad", "Vijaywada", "Vadodara", "Thane");
	Long DIGIO_FAILED_LIMIT = 3L;
	String FOS_ATTRIBUTION = "/api/v1/external/task";
	Set<String> OTHER_LENDING_APPS = new HashSet<>(Arrays.asList("Credit Hub","Loan Purse","Credit Score","CredR Store","Credit Card Tips","Loan Calculator","Creditrupee","Lendingkart","Credit Cash","Loan EMI Calculator","Loan Money","Loan Bazaar","Loan Calculator","Loan On AadharCard","Credit Debit","Loan calculators","LoanCard","Credai Nashik","Loan Time","Loan Time","Loan Plus","Loan EMI Calculator","Loan Credit Money","loan For Cibil Defaulter Things Should Know TIPS","Loan EMI Calculator","Credit Card Validator","Credit Score","Loan In 59 Minutes","Loan Repayments","Credzee","Credential Manager","LoanCompareIndia","Loan Calculator","Credit Card Scanner","Credit Report","Credit Card Apply Of All Bank","Credit Score Report Check - 2020","Credit Note","LoanAssist","Loan Application","Loan Bazaar","Loan Calculator","Loan Calculator Professional","Loan Credit Score","Credit Score Report","Lendme","Loanwalle","Loans for New Businesses","Loans Referral","Loans","Credit Ok Pro","LoanTap","Credit Cash","Loan Origin Application Pilot","CredR Care","Credit Card Calculator","Lending Bazaar","Credit Report","Credit Want","Loan Credit Score","Credit hub","Credit Karma","Credtree","Loan Basket","Credit Card Reader Pro","Credila Education Loan","Loan On Aadhar Card","Loan Calc","Credai Maharashtra","LoanKlub-Partner","Loan GuRu - Easy Personal Loan Quick Online","Loan Calculator","Credit Score","Credit Fair","CreditCash","Credihealth","Loan Calculator","Loana","Loan","LoanApps","Loan Calc","Credit Score Report","Loans Free","Loan Calculator","Credit Score Report Check: Loan Credit Score","Credas","Loan Calc","LoanAnalysis","loanday","Loan Calculator","LoanZone","Loan Calculator IQ","Loan On Aadharcard","Credit Score Reports","Loan bazaar","LoanU","Loan Calculator","Loan SIP Calculator","Credit Card Reader","Loan Calculator","Credit Card Payoff","Loan In a One Minute","Credit Reminder","Credits Pay","Loan Insta: The Smart Loan Consultation","Credit Card Identifier","Loan Now","Loan & Credits","Credit Mithra","Loan Assist","loanplus","Credit Score Pr Loan Paye","Loan Contract","Loans - Instant Loans to Mpesa","Loanhub","Loan Calculator","Loan Calculator","Loan Zoom","Loan Pro","Loan Assist","LoanGram","LoanBook","Lendtek","LoanCloud 1","CreditKasa","Loan Repayment","Loans","loan fortune","CreditU","CreditClub","Loan Calculator Buddy","Credit & Debit Manager 2019","LoanMall","LoanPe","LoanInsure","LoanGo","Loan Time","CredRightSales","CreditClub","loan app","Credit Score","Loan EMI Calculator","CreditBee Instant Personal Loan","Loan Bus","CrediPay","Credit Cash","Loan Emi Calculator","Credit Debit Accounting- Digital Ledger Cash Book","Credit Card Validator","Lendbox","Loan Calculator","Loan Calculator","Credo Parent","Loans Bazaar Partner","Loan Town","Loans india","LoanOffice","loaner","Credimond","Loans","Loan Kingdom","Credit Score","LoanMoney-Associate App","Loan_Solution","LoanTracker","LoanMart","Credit42","Credit Score Report","CrediBook","Credit Debit","Loan Lelo","LoanKing","Lenda","Lend Borrow Recorder","Creditfair","Credit Log","Lend Bazaar","Loan Rupee","Credit Fair","Credit Score | Loan Credit Score Report","Loan Buddy","Credit Score","Loan On Aadhar App","Lend.in-sit","CredFlow","Credit Debit Cards","CreditMantri","CreditCash","Loan EMI Calculator","Credit Card Customer Care Numbers","Loan Bus 4","Loan Credit Score","Loan Money","Credit Sesame","lendingmax","Lender App","Credit Card Deal","Loan Yojna By PM","Creditlah","Credit Partner","Loan Suvidha","LoanCalculator","Lend & Borrow money Manager","Loanbazar","CredR Connect","LoanLe","Credit X","Credit Score Check And Get Loan","Loan For Business","LoanWala","CredApp","Loan Pro","Loan calculator","Credit Score Report","Creditcards","LoanCellar","Loan Guide","Credit Expert LLC","LoanSimple","Loan Credit Score","LoanCalc by Dtailcode","LoanMaster - Personal Loan App Online","LoanGrow","CreditKaro","Credit Card Bill Payment","CreditClub","Loan On Aadhar Card Guide","Loan EMI Calculator","Credit Score Report","LoanPay","Credit Card Guide","LoanRadar","CreditKart-Fincom","Loan calculator","Creditpay","LoanAppRN","LoanWix","Loan Amortization","LoanEZ","Loan App 882","Loan Wallet","Credit Card Girl","Credit Card Checker","LoanBus 1","LoanBudy","Loan Finder","Loan Application Form Pdf Download - Fill Online, Printable, Fillable, Blank | pdfFiller","Loan Time","Credit Card Manager","Loan Calculator","Loan Today USA","Loan EMI Calculator","Loan Shark","Loan EMI Calculator","LoanBus","Credin","Lending Cash","Loan Calc","Cred S","Credime","CreditRupee","Credit Loans","Loan EMI Calculator","Cred","Lendkash","CreditFinch","loanplus","Credit2Bank","Creditap","Credit Setu","Loan Credit Score","Creditap","Credit Customer","Loan Money Online","Credit Score Report Guide","Loan Credit Score","Credit Score","Credit Score Report Check","Loan Star","Loan Go","CreditBuilder","Loan Calculator","Loan Calculator","LoanIt","Loan Calculator","LoanStar","Credit Score","Loan on Credit Score","CreditFunds","Loan Cloud","LoanKaraDo","Credito","Credit Cash","credicxo","Credoc","Credy | Préstamos personales app8","Loan","Credit Card Reader","LoanStar","Credit Bazaar","Credit Card","Credit Card Validator","CreditStacks","Credent TV","Loan Rocket","Loan Credit Score","Credit Card Reader","Creditt","Credit Score","LoanTime","Loan Max","Loan On AadharCard","CredApp Vendor","Loan Credit Score","Loans Bazaar","Loan Wala","CreditCard Manager","Loan EMI Calculator All Bank","CredoWeb","Loans Directory Kenya","Loan FI","Credit Score Check","Cred","Loan On Aadhar Card Guide","Lendy Loans","LoanTm","Credit Score Check And Get Loan","Loansafe","Loan 24","LoanShark","CreditBee","LoanMarket","Cred","LoanEmiGSTCalculator","Credit Card Terminal","LoanCollection","Loan Credit Score","Loan EMI Calculator","Loan EMI Calculator","Credit Cash","Loan EMI","Loan Bus 2","Loanadda","Credit n Debit Card Offers @ Deal4loans.com","Lend Borrow","LoanOffice","loanplus","Loan Calculator","Loan Yojna By PM","LoanPlateForm","Loanwalle","Credit Card Bill Payment","credit Mr","Loans Application","Loan Credit Loan","CreditWise Sales","Credit Rupee Loan","Loan Calc","LoanKing","Loan Tree","Loan Chacha","CrediMOND LITE","Loan Manager","Loans in Inadia","Credy","Credit Score Report Check: Loan Credit Score","Credit/Debit Card Pin Store","Loan Masala","Loans Guide","Credit Bazzar","CreditCard BillPay","Loan EMI Calc","LoanPayter","Loan Finder","LoanCalculator","Credito","LoanFit","Credit Card Apply Online Guide","Loan On Aadhar","Loans & Debts","LoanAdda","Loans For Bad Credit","Loans","credencApp","Loan Rocket","LoanHome","loan box","LoanGuru","Credit Card Manager","Loan calculator","Loanplus","LoanBro","LoanGate_IND","Credgenics","Loans - Easily Cash loan","Loanbaba","CredyOps","LoanFirst","Loan On Aadhar Card","Loan Helper","LoanCalculation","Credit_Book","Loan On AadharCard","Loan Frame","LoanTM","Loan EMI Calculator","Credit Score Report Check: Loan Credit Score","Credit Card Apply Online - Guide","Credit card Guide","Loan Home","Credit Card Bill Payment","Loans between friends","Loan Recovery","Loan Calc","LendKaro","Credo Joy","Loan Cash","CreditBus","Loan Card Plus","Credit Borrow","LoanGo","lendpapa","Lend bazaar","LoanSimple Team","Loans","Credime","loan_app","Credit Score Report Check : Loan Credit Score","Credenc","Loan Compare","Loan Calc","Loan request form","LoanMela","Credit Dhani","Lender Borrower","Credit Note","Loan Day USA","LendingAdda","LoanCalculatorPro","Loan Online Counsultant","Loan Manager","Lendmark Mobile","Loan Buzz","CreditFree","Loan Finder","LoanBro","Loan Credit Tree","LoanCred","Loan_Bank","Loan 360","Credit Loan","Credit Master","LoanZio","loanpocket","Loan On Aadhar Card","LoanKaraDo","Credit Score Check","LendNowRs","Loan Manager","Loan Credit Check Finance Check Cal 2019–20","Credit and Deposit Calculator","Loan Manager","LoanMall","Credit Converter","Loant","LoanMoney-Associate App","Credit Rating and Credit Score Check","Loan Pro","Loan Bus","LoanPe Pro","Loan Credit Score","Loan Wala","Credit Score Test","Creditorecharge","credit100","CredMerchant","Loan Manager","Credit Card Processing","Credit Pundits","Loans Application","Loanagainstcard.com","Loan EMI Calculator","Credit Hunk","Loan Guide","Credit Card Payoff","Loan Calculator","Loan Consultant","Credit Manager","Loan Calculator","CreditBean","Credit Light","lendCase","Loan Repayment","Credit Score","CrediMax","Loan EMI Mortage Calculator","CreditVana","Loan","Loanwiser","Loanflix","Loano","CredMerchantDev","Credit Track","Loans","Credit Wallet","Loan cash","LoanWallet","Credit Karma - Credit Score","Loanlelo","LendNow","Credit Card Assistant","Loan & Interest Calculator Pro","Credit Pro","Loan IRR Calculator","Loan Amortization","Loan On Aadhar Card","Loan Calculator","Loan Applica","Loan EMI Calculator","Loan Today USA","Credit Score Report","Loan on Aadhar Card","Loan Now","Lendbox","LoanPlus","Loans Guide","Loan Application","Credit Card Bill Payment","Loan Finder-Pro","Loans","CreditCardValidator","LoanCash App","LoanmantraCLUE","LoanGo","Loan Calculator","Credit score report","loan online","Loan Calculator","Loan On Aadharcard","Lending360","LoanMart","Loan Approval","Credit Card NetBanking","Credit Card Admin","Credit Card Manager","Loans Online","LoanSack","Loan For Students Everything You Need to Know","LoansGO - payday loans finder","Loan Credit Score","Loan Laws","Loan Calculator","Lendbox Microloans","Credit Score","LoanXpress","Credit Kiya","CredCashIND","Credila Manager","LoanBin - CashLoan for Individuals","Loan Market","Loan Eligibility Calculator","Loan Calculator","Credit Card Loan Guide","LoanMart","LoanPump","LoanGo Pro","Loan Calculator","Loan cash","Credit and Debit","Credit Cash","loanmart","Cred24","Loan Calc","LoanOnAdharCard","Loan4Fleets","Credit Khata","Loan Box","CreditWise","Credit+","Loan Calculator","CredR Refresh","Credit Score | Loan Credit Score Report","Credicard","Credit Europe Bank","LoanIndia","Credentials","Loan, EMI Calculator","Credit Cibil","Credit Report","LoanSmart","Credit Score Report","LoanCalculation","Loan Calculator","LoanTop","LoanMax","Lenders Cash App","Loan Rupee","CredAvenue","Loanfast1","Lendbox","CreditMoney","Credit and Debit","LoanCal","Loan For Self Employed","LendingMate","Loan EMI Calculator","Loan Buzz","Loan & Interest Calculator","LoanBazaar","Loan Guide","Credit Cash","LendersClinic","Loan Rcv Finance: 2 Min Loan Approved","Loan Mama","LoanPro","Loan Yojna By PM","Credit Cash","Lendbox","LoanMarket","Credit Debit","creditcash","Lending Adda","Credicxo","CreditSathi","Loan Calc","Loans","Loans Cash","Loan calculator","Loanunbox.com","Loans Shwari","LendVip","Loan For Cibil Defaulter","Loan On Aadhaar Card - Guide","credit score","Loan Calculators","Loan EMI Calculator","CrediPay","Loan Bro","Lenden & Daily Hisab","Loan 24h","Credy Key","Loan2Wheels","Loan Guide","Creditum","CrediMe","credit king","CreditDay","Credit Mate","Credit2U","Loans UK","Credit Score Report","Loans Bar","LoansClub","Credo World","Credit Card Apply Easily Of All Bank","LoanCalculator","Loan on Aadhar Card","loan Urgent Emergency Things Should Know TIPS","Credit Up","Loanwiser","Credit Score Checker App","Loan King","Credit Score","Loan Calculator","Credit Score Estimator","Loan Globally","CreditMate","Credit7","loan","LendingDirector","Loan Bazaar","Loans","Credence Learning","Loan Easy","Credit Card Miss Call Balance","Credit Linked Subsidy Scheme","Cred.","Credit Report","Loan on Aadhar Card Upaay","LoanMoneyGuide","CreditKiya","Credit Card Manager Pro","LendingCola","LoanAddaCRM","Loan Statement","LoanLO","Loan Lelo","Credit Verification","Loan Cash","Loan Calc","Loan List","Credit Score Reports","CredSet","Loan Calculator","Loan In","Loan money guide - loans for bad credit - get loan","CreditMantri","LoanWala","Credits Pay","LoanKwik","Loan on Aadhar Guide","Loan Manager","CredApp Vendor","Credit Card Reader Pro","Credit Score","Loan Calculator","CreditKart-Fincom","Credit Mithra","Loan Shark Calculator","LoanMojo","LoanFrom","LendVest","loan manager","LoanPay","CreditDoctor","CredIn","LoanZen","Credi Pay","Loan","Loan Calculator","LendList","Loan Lelo","LoanX","Credit Score Check And Get Loan","LoanOnPanCard","Creditos Rapidos","LoanBus Guide","Credit Card Verifier","Loan Calc","Credit Score Check","Credit Card Swiper","LoanReady","CredR Connect","Loan Raja","Loanhub","LoanCloud","Credexy","CreditMantri","CreditO","Credit Score Report 2019","Credit Card Bill Payment Online","Loan To Value","Loan Manager","Credit Score","Credit Pro","Loan EMI Calculator","CreditHouse","Crediti","LoanRaahi","Loan24/7","CreditCash","Lenden Instant Loan","Loan Calculator","Credit score report","Credix Mobile Loans","Credit Score","LoanBean","Loan On Aadharcard","Loan Calculator","Loan Credit Score Check","LoanApp","Loans - Instant Online Loans","Loan on Aadhar Guide","LendingBazaar","Credit360","Loan finder","Loans App","LoanUnbox","Loan On Aadhaar Card","CreditClub Plus","Loan Up","Credit Score","CreditRupee","Loans","Credit Card Bill Pay","Credit Fair","Credit Papa","Loanstar","Credit Peso","Loan Raja","Credit Card Management","LendPal","Loan Amortization Calc","Credit Notebook","Credence Edutech","Loan24 App","Loan Shop","Credit365","CreditQ","CreditUpchar","Loan Calk","Loan Calculator","Loan Credit Score","LoanEmiCalculator","LoanBean","Loan on Aadhar Card","Loan For Unemployed Everything You Need to Know","Loan Ap","Credit Karma Advice","Creditme","Credinks Merchant","CredofastRetailer","Loan Eligibility Check","Credit Loan","cred","Loans","CredoNX","Creditz","Loan Guide","Loan EMI Calculator","LoanTop","Loan Details","Cred","Loan Durian","Credit Rupee","Credit Score","Credflow","CreditCard Bill Pay","Credit One","Loan Calculator","Loan Calculator","Loan Calculator","Credle","Credit Mantra","Loan Boss","Loans for bad credit Guide","Credit cash","CreditBee","Cred","Creditor's App","LoanApp","LoanRupee","Creditap","Creda SSY-4","Lender","Credit Card Bill Payment","Loan EMI Calculator","Loanmart","Loan Seva Kendra","Loanflix","LoanSolo","LoanBus","Credit Card Repayment Calculator","Credit Score Report Check","Loan Guide for Unemployed Youth","LoanRecovery","LoanSpot","LendCash","Loan Man","Loan Buzz","LoanFarms","Loan EMI Calculator : Loan & Financial Planner","Loan Assitant","Loan Daily","Loanmeet","Loan Calculator","Loan Calc","LoanAdharGuide","Loans - Instant Mpesa Loans","Loan on Aadhar Card","Lending adda","Creditloan","Credit Monitoring & Follow-up","LendingBean","Loan Online Counsultant","Loans Bar","Credit SUKI","Loan Time Pro","Loan Bizz","Credit 365","Lendbox","Loans","LoanView","Credit Report","Credbizz","Loans Bar","LoanTools","Loan bazaar","LoanFront","Loan Dost","Cred","Credit Card Calculator","Lendbox","Loan EMI Calculator","Loans","Credit Book","Loan CalC","Lendbox","Loan Network","Loanus","Loan Paisa","Loan EMI Calculator","CreditMate","Loan klub","CreditHub","Credit Bureau Check","LoanBro","Credit Card Payoff","Loan Baba","Credit Score Report","loan msme","CreditBi","Loan Tiger","CreditProfiler","CreditLoan","LoanMaster","Loan Calculator","Lender","Loan Pro Go","Loan Credit Score","LoanMax","Credit Plus","LoanBazaar","Credit Operations","creditman","Loan Calculator","Loan@Home","Loan Credit Reward","Loani","Loan Adda","Loan Details/Enqiry","Loan For Self Employed Things Should Knows TIPS","Loan calculator","Creditt","Loan EMI Calculator","Loan Calculator","Loan On AadharCard","Credit card Guide","Loan.Android","LendersClinic","CreditBee Instant Personal Loan Picker","CreditRupee","CreditKart","Credit Report","Loan Bus 1","Loan Guide","Loan Free Interest","LoanFresh","Loanguru - Intstant personal loan online","Loan Mall","Credit Score Report","Credit CRED","Loan calculator","Loan Calculator","CreditHouse-Instant Personal Credit Loan App","Loan Agent","Loan Bazaar","Loan Cash USA","loan2grow Customer App","Credit Card Loan","Credai","CredRight","Loan Point","Lender","Lender easy loan","CredKeeper","Credit Pay","Credit Score","Loan Part Payment Calculator","LendingBeanPro","Credit Club","Loan Calculator","CrediApp","Loan EMI Calculator","Lendbox","LoanOnAdharCard","Loan Guide and Services","Loans Chap Chap","CredTap","Loan Calc","Credit365","Loan Credit Score","CreditSudhaar","Loan On Mobile Instant Guide","Credit Loan","Loan Spot","Loan Pe Phone Partner","Loan Credit Score","LendMN","Loan@Home","Loans Guide","Credit Score","Credimond Fund Transfer","CredRightSales","Credit Score Alerts","Credit Card Shop","Loan Dairy","Loan Calculator","Loan on Aadhar Card","Loan Bus","Loan:Info","LoanOnMind - HomeLoan,CarLoan","credencapp","Loan Center","Loans - Instant Loans to Mpesa","LoanPay","Loan Calculator","Lending Hub","LoanClub","Loans Samadhan","Loan Instant Personal Loan App - MobileLoan","LendingBox","LoanBro","Credit Card Verifier","Loan Chacha Partners","Loan Agent","CreditBee Instant Personal Loan Picker"));
	String CANCEL_ENACH_URL = "/mandate/cancel";
	List<String> ESSENTIAL_CATEGORIES = Arrays.asList("Dairy","Dairy_Fresh_Products","Dairy/Fresh Products","Doctor_Clinic","FMCG","Fresh Produce","Fresh_Produce","Fuel","Fuel & Gas","Fuel_","Fuel_&_Gas","Gas_Agency","Grocery","Grocery_General_Store","Grocery_Kirana_Shop","Grocery/General Store","Medical","Medical_Equipment","Medical_Health_Care","Medical_Shop_Chemist","Medical/Health Care","Petrol_CNG_Pump","Vegetable_Fruits","Water_Supply");
	String CREATE_PG_TXN_V2 = "/v2/client/transaction";
	String CREATE_PG_TXN_V1 = "/v1/client/transaction";
	String PG_STATUS_CHECK = "/v1/client/transaction?orderId=";
	String HEADER_CLIENT_NAME = "clientName";
	String HEADER_HASH = "hash";
	String KYC_DOC_URL = "/api/v2/internal/get-document";
	String KYC_INITIATE_URL = "/api/v2/internal/initiate-kyc";
	String KYC_PAN_NO_URL = "/api/v1/internal/pan-details";
	String PAN_NAME = "/api/v1/internal/pan-verify";
	String PAN_VERIFY_V3_INTERNAL = "/api/v3/internal/pan-verify";
	String PAN_VERIFY = "/api/v3/pan-verify";
	String PAN_FETCH = "/api/v3/pan-fetch";
	String PAN_FETCH_INTERNAL = "/api/v3/internal/pan-fetch";
	String APPLICATION_EVENT_TOPIC = "LENDING_EVENT_APPLICATION_UPDATE";
	String APPLICATION_DS_EVENT_TOPIC = "LENDING_EVENT_DS_DATA";
  String CREDIT_CARD_STATUS_URL = "/credit_card/status?merchant_id=";
  String GOLD_LOAN_DUE_URL = "/api/live-loan/due-amount?merchantId=";
	Long DEFAULT_REFERENCE_LIMIT = 10L;
	Integer MINIMUM_CONTACTS_NEEDED = 50;
	String GET_MERCHANTS_REFERENCES_CACHE_KEY = "MERCHANT_REFERENCES_FROM_DE_";
	String INITIATE_KYC_CACHE_KEYWORD = "INITIATE_KYC_CALLED_";

	String ELIGIBILITY_IFRAME_KYC_CACHE_KEYWORD = "ELIGIBILITY_IFRAME_";
	String POA_PROVIDER = "DIGIO";
	String CUSTOM_OFFER_DOWNGRADE = "CUSTOM_OFFER_DOWNGRADE";
	String PENDING_DISBURSAL = "PENDING_DISBURSAL";
	String SEND_TO_NBFC = "SEND_TO_NBFC";
	String PENDING_DISBURSAL_SKIPPED = "PENDING_DISBURSAL_SKIPPED";
	String PUBLISH_LOAN_DISBURSAL_KAFKA_TOPIC = "lending_disbursal";

	String REJECTION_REASON_1 = "not_enough_references";

	String REJECTION_REASON_2 = "not_enough_references_de";

	String TOPUP_PILOT_IDENTIFIER = "TOPUP_V3";
	String SIX_DAY_MODEL_OFF_DAY = "SUNDAY";

	String UPI_AUTOPAY_ADJUSTMENT_MODE = "UPI_AUTOPAY";
	String UPI = "UPI";

	String PENNYDROP_LOCK_PREFIX = "LENDING_PENNYDROP_";
    String BUREAU_CONSENT_KEY_PREFIX="BUREAU_CONSENT_DETAILS_";
	String LENDING_SOURCE = "EASY_LOANS";

	Long BUSINESS_CATEGORY_LMS_FIELD_ID = 40L;
	Long BUSINESS_SUBCATEGORY_LMS_FIELD_ID = 41L;

	String NEGATIVE_BUSINESS_CATEGORY_REJECTION = "Negative category as per lender";

	String PAYTM = "PAYTM";

	String PAN_VERIFICATION_VERSION="v2";

	Map<String , List<String>> ResubmitReasonMap = new HashMap<String , List<String>>() {{
		put("SHOP_PHOTO", Arrays.asList("SHOP_PHOTO","SHOP_INFERED_DISTANCE", "SHOP_OPERATIONAL"));
		put("SHOP_INFERED_DISTANCE",  Arrays.asList("SHOP_PHOTO","SHOP_INFERED_DISTANCE", "SHOP_OPERATIONAL"));
		put("SHOP_OPERATIONAL",  Arrays.asList("SHOP_PHOTO","SHOP_INFERED_DISTANCE", "SHOP_OPERATIONAL"));
		put("BUSINESS_NAME",  Arrays.asList("BUSINESS_NAME","SHOP_BOARD_NOT_MATCHING_BUSINESS_NAME"));
		put("SHOP_BOARD_NOT_MATCHING_BUSINESS_NAME", Arrays.asList("BUSINESS_NAME","SHOP_BOARD_NOT_MATCHING_BUSINESS_NAME"));
		put("SHOP_ADDRESS_INCORRECT", Collections.singletonList("SHOP_ADDRESS_INCORRECT"));
		put("INCORRECT_SELFIE", Collections.singletonList("INCORRECT_SELFIE"));
	}};

	String NONE_LENDER = "NONE";
}



