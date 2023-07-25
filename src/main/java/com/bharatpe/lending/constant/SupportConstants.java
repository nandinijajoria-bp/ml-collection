package com.bharatpe.lending.constant;

public interface SupportConstants {

    String ACTIVE_CREDIT_LINE = "Credit Line merchant, refer details below.";
    String PAN_NOT_ENTERED = "Eligibility Not Checked";
    String PAN_NOT_ENTERED_MESSAGE = "Please go to the Loans section on BharatPe App & Enter PAN, PIN on App to check  eligibility";
    String ACTIVE_LOAN = "Disbursed";
    String ACTIVE_LOAN_MESSAGE = "Your Loan has been Disbursed. To Check your EDI Details click Loan Icon on BharatPe App.";
    String ACTIVE_LOAN_CONDITIONAL_MESSAGE = "Timely Repayments will help minting good Credit Score and that increase chances of getting Higher Loans in future.";
    String NOT_ELIGIBLE = "NOT Eligible";
    String NOT_ELIGIBLE_MESSAGE = "You are not eligible as of now, Keep Transacting with BharatPe to become eligible in future.";
    String ELIGIBLE_NOT_APPLIED = "Eligible but NOT Applied";
    String ELIGIBLE_NOT_APPLIED_MESSAGE = "Please apply for a loan on BharatPe App. You can choose your Loan amount & create custom loan offer. Click on Loan Icon on BharatPe App.";
    String STARTED_APPLICATION_NOT_SUBMITTED = "Started Application but Not Submitted";
    String STARTED_APPLICATION_NOT_SUBMITTED_MESSAGE = "Please Complete your Application on BharatPe App. Click on Loan Icon on Home screen and fill on all the details. Always Do ENACH for faster processing of Loan.";
    String ENACH_PENDING = "Application Partially Completed, ENACH pending";
    String ENACH_PENDING_MESSAGE = "Please do ENACH and complete your application. Verification process will start once ENACH is done.";
    String KYC_VERIFICATION = "Document Verification Pending.";
    String KYC_VERIFICATION_PENDING = "Your Application is in KYC Verification Stage, <Priority_Message>. Track status of the application on BharatPe App , click on Loan Icon for more details";
    String KYC_VERIFICATION_APPROVED = "Your Application KYC is approved, <Priority_Message>. Track status of the application on BharatPe App , click on Loan Icon for more details";
    String KYC_VERIFICATION_REJECTED = "Your KYC is rejected since it does not match Our Document Assessment Criteria. Keep Transacting with BharatPe to avail loan in Future.";
    String CPV_VERIFICATION = "Under Processing";
    String CPV_VERIFICATION_MESSAGE = "Your Application is in Document Verification Stage. <Priority_Message>. Track status of the application on BharatPe App , click on Loan Icon for more details";
    String CPV_VERIFICATION_PENDING = "Your Application is in Document Verification Stage. Our CPV Field agent will get in touch with you shortly. <Priority_Message>. Track status of the application on BharatPe App , click on Loan Icon for more details";
    String CPV_VERIFICATION_APPROVED = "Your Application Physical Verification is approved, <Priority_Message>. Track status of the application on BharatPe App , click on Loan Icon for more details";
    String CPV_VERIFICATION_REJECTED = "Your Application is rejected since it does not match our Document assessment Criteria.";
    String CIBIL_VERIFICATION = "Under Processing";
    String CIBIL_VERIFICATION_MESSAGE = "Your Application is in under Verification. <Priority_Message>. Track status of the application on BharatPe App , click on Loan Icon for more details";
    String CIBIL_VERIFICATION_REJECTED = "Your Application is rejected since it does not match our Document assessment Criteria.";
    String APPROVED_VERIFICATION_CALLING_PENDING = "Under Processing";
    String APPROVED_VERIFICATION_CALLING_PENDING_MESSAGE = "Your Application is in under Verification. <Priority_Message>. Track status of the application on BharatPe App , click on Loan Icon for more details";
    String VERIFICATION_CALLING_READY = "Verification Completed";
    String VERIFICATION_CALLING_READY_MESSAGE = "Your Application with BharatPe is approved. <Priority_Message>";
    String NTB_VERIFICATION_CALLING_PENDING = "Verification Call Pending";
    String NTB_VERIFICATION_CALLING_PENDING_MESSAGE = "Your Application is in under Verification. Our Executive will get in touch with you shortly for verification to process the Disbursement. <Priority_Message> Track status of the application on BharatPe App , click on Loan Icon for more details";
    String P0 = "You will get your loan in next <current_TaT>";
    String P1 = "You will get your loan in next <current_TaT>";
    String P2 = "You will get your loan in next <current_TaT>";
    String P3 = "You will get your loan in next <current_TaT>";
    String P4 = "Your BharatPe loan application is currently on hold. Please increase transactions on BharatPe QR to get your loan application processed";
    String P5 = "Your BharatPe loan application is currently on hold. Please increase transactions on BharatPe QR to get your loan application processed";
    String TAT0_MESSAGE = "You will get your loan soon";
    String DEFAULT_PRIORITY = "Your BharatPe Application is still under processing. Track status of the application on BharatPe App , click on Loan Icon for more details.";
    String ENACH_MESSAGE = "Our lending partners don't give a loan in your registered Bank A/c. Please change your Bank A/c by clicking on your Name on BharatPe app and then clicking on Settings icon.";
    String OGL_MESSAGE = "Your location is currently not serviceable, We will notify you once available.";
    String FRAUD_MESSAGE = "We can not offer you any Loan offer right now, since details provided by you do not fall under BharatPe Loan eligibility Criteria. Keep Transacting with BharatPe to avail loan in Future.";
    String DEFAULT_EXPERIAN_REASON = "You are not eligible as of now, as we found issues with your Credit Report (check the same on Loans section on BharatPe App). Keep Transacting with BharatPe to become eligible in future";
    String REJECTED_MESSAGE = "Your Loan Application is rejected since it does not match our internal Document Assessment Criteria. You can re-apply again using BharatPe app.";
    String NOT_APPLIED = "Not Applied";
    String NOT_APPLIED_MESSAGE = "Please click on Loan section on bharatPe App to check current eligibility.";
    String INACTIVE_LOAN_MESSAGE = "Your loan is INACTIVE since we found issues to transfer amount in your bank account.";
    String NTB_LOAN = "NTB_LOAN";
    String NTB_LOAN_V2 = "NTB_LOAN_V2";
    String CPV = "CPV";
    String TASK_ID = "task_id";
    String MERCHANT_ID = "merchant_id";
    String STATUS = "status";
    String CLIENT_NAME = "clientName";
    String HASH = "hash";
    String ALREADY_REFUNDED = "Processing fee Already refunded";
    String LOAN_NOT_CLOSED = "Refund eligibility of Processing fee will be calculated on "
      + "successful loan closure";
    String NEW_LOAN_ACTIVE = "Processing Fee will be refunded after the closing of TOPUP Loan";
    String FORE_CLOSER_LOAN = "Not eligible for Processing fee refund as the merchant foreclosed "
      + "the loan";
    String MAX_DPD = "Not eligible for Processing fee refund as max dpd was greater than 5";

    String ELIGIBILITY_NOT_CHECKED = "Please enter your PIN & PAN to check eligibility";
    String OGL = "BharatPe Loan is currently not available in your pincode, we'll inform you when we launch in your location.\n" +
            "Please continue transacting on BharatPe.";
    String DEROG = "You are not eligible for a BharatPe loan due to an issue with your credit report.\n" +
            "Please check your eligibility again.";
    String LOW_TRANSACTION = "You are not eligible for a loan due to low number of QR/ Swipe transactions. \n" +
            "\n" +
            "Please continue accepting payments using your BharatPe QR/ Swipe machine.";
    String CHANGE_BANK_ACCOUNT = "Your bank does not allow eNACH. Please change your bank account and retry to be eligible for a loan.";
    String PERMANENT = "You are currently not eligible for a BharatPe loan. \n" +
            "\n" +
            "We'll inform you once your eligibility changes";
    String NOT_STARTED = "You are now eligible for a loan. Please start your application on the BharatPe app.";
    String DRAFT = "You are a few steps away from completing your loan application for a loan. \n" +
            "\n" +
            "Please go to the BharatPe app and complete your loan application.";
    String SUBMITTED = "You are only one step away from completing your loan application.\n" +
            "\n" +
            "Please go to the BharatPe app and complete this step now so that our credit officers can start working on your loan application!";
    String RELEVANT = "Verification is pending for the application and the nach is approved";
    String REJECTED = "Your loan application has been rejected after an internal assesment by our credit team.";
    String ACTIVE_LOAN_COMM = "You currently have an active loan with BharatPe. Please go through loan details on the app";
    String CLOSED_LOAN = "closed loan";
    String IN_PROCESS = "Application is approved and the disbursal is in process.";

}
