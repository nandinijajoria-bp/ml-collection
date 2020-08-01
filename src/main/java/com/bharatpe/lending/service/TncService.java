package com.bharatpe.lending.service;

import java.util.Date;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.common.dao.CreditApplicationAddressDao;
import com.bharatpe.lending.common.dao.CreditApplicationDao;
import com.bharatpe.lending.common.entity.CreditApplication;
import com.bharatpe.lending.common.entity.CreditApplicationAddress;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dto.TncDto;
import com.bharatpe.lending.dto.TncRequestDto;
import com.bharatpe.lending.util.LoanUtil;

@Service
public class TncService {
	
	@Autowired
	CreditApplicationDao creditApplicationDao;
	
	@Autowired
	CreditApplicationAddressDao creditApplicationAddressDao;
	
	public TncDto getTnc(Merchant merchant,TncRequestDto requestDto) {
		TncDto tncDto=new TncDto();
		String htmlString=null;
		Optional<CreditApplication> creditApplicationOptional=creditApplicationDao.findById(requestDto.getApplicationId());
		if(creditApplicationOptional==null || !creditApplicationOptional.isPresent()) {
			tncDto.setSuccess(false);
			tncDto.setMessage("Credit application not found");
			return tncDto;
		}
		if(requestDto.getType().equalsIgnoreCase("APPLICATION")) {
			htmlString=getTncForApplication(merchant,creditApplicationOptional.get(),requestDto);
		}
		else {
			
			if(requestDto.getType().equalsIgnoreCase("FIXED")) {
				htmlString=getTncForFixed(merchant,creditApplicationOptional.get(),requestDto); 
			}
			else if(requestDto.getType().equalsIgnoreCase("FLEXIBLE")){
				htmlString=getTncForFlexible(merchant,creditApplicationOptional.get(),requestDto);
			}
		}
		
		if(htmlString==null) {
			tncDto.setSuccess(false);
			tncDto.setMessage("Error occured while fetching tnc");
			return tncDto;
		}
		tncDto.setHtmlString(htmlString);
		return tncDto;
	}
	
	public String getTncForApplication(Merchant merchant,CreditApplication creditApplication,TncRequestDto requestDto) {
		CreditApplicationAddress creditApplicationAddress=creditApplicationAddressDao.findByMerchantIdAndApplicationId(merchant.getId(), creditApplication.getId());
		if(creditApplicationAddress==null) {
			return null;
		}
		String html="<p class=\"p1\" style=\"text-align: center;\"><span class=\"s1\"><strong>Credit Line Details</strong></span></p>\n" + 
				"   <style>\n" + 
				"        table.t1{\n" + 
				"            width: 100%;\n" + 
				"        }\n" + 
				"        .new-table1 td{\n" + 
				"            border: 1px solid #000;\n" + 
				"            width: 33%;\n" + 
				"            text-align: center;\n" + 
				"        }\n" + 
				"        .new-table2 td{\n" + 
				"            border: 1px solid #000;\n" + 
				"            width: 16.6%;\n" + 
				"            text-align: center;\n" + 
				"        }\n" + 
				"        .new-table3 td{\n" + 
				"            border: 1px solid #000;\n" + 
				"            width: 50%;\n" + 
				"            text-align: center;\n" + 
				"        }\n" + 
				"    </style>\n" + 
				"    <p class=\"p2\">Credit Line ID: <span class=\"data-insert\">"+(creditApplication.getExternalLoanId()==null?"":creditApplication.getExternalLoanId())+"</span></p>\n" + 
				"    <p class=\"p0\">Credit Lime Amount (INR):  <span class=\"data-insert\">"+(creditApplication.getAmount()==null?"":creditApplication.getAmount())+"</span></p>\n" + 
				"    <p class=\"p2\">BharatPe Registered Mobile Number: <span class=\"data-insert\">"+(merchant.getMobile()==null?"":merchant.getMobile())+"</span></p>\n" + 
				"    <p class=\"p2\">Location: <span class=\"data-insert\">"+(creditApplicationAddress.getCity()==null?"":creditApplicationAddress.getCity())+"</span></p>\n" + 
				"    <p class=\"p2\">Shop/Business Address: <span class=\"data-insert\">"+(creditApplicationAddress.getShopNumber()==null?"":(creditApplicationAddress.getShopNumber()+","))+(creditApplicationAddress.getStreetAddress()==null?"":(creditApplicationAddress.getStreetAddress()+","))+(creditApplicationAddress.getArea()==null?"":(creditApplicationAddress.getArea()+","))+(creditApplicationAddress.getCity()==null?"":(creditApplicationAddress.getCity()+","))+(creditApplicationAddress.getState()==null?"":(creditApplicationAddress.getState()+","))+(creditApplicationAddress.getPincode()==null?"":(creditApplicationAddress.getPincode()))+"</span></p>\n" + 
				"    <p class=\"p2\">Landmark: <span class=\"data-insert\"></span>"+(creditApplicationAddress.getLandmark()==null?"":creditApplicationAddress.getLandmark())+"<span>&nbsp;</span> PIN: <span class=\"data-insert\">"+(creditApplicationAddress.getPincode()==null?"":creditApplicationAddress.getPincode())+"</span></p>\n" + 
				"    <p class=\"p2\">City: <span class=\"data-insert\"></span>"+(creditApplicationAddress.getCity()==null?"":creditApplicationAddress.getCity())+"<span>&nbsp;</span> State: <span class=\"data-insert\">"+(creditApplicationAddress.getState()==null?"":creditApplicationAddress.getState())+"</span></p>\n" + 
				"    <p class=\"p3\">Email: <span class=\"data-insert\">"+(creditApplication.getEmail()==null?"":creditApplication.getEmail())+"</span></p>\n" + 
				"    <p class=\"p4\">&nbsp;</p>\n" + 
				"    <p class=\"p3\">Shop/ Business Phone Number: <span class=\"data-insert\"></span>"+(creditApplication.getMobile()==null?"":creditApplication.getMobile())+"</p>\n" + 
				"    <p class=\"p4\">&nbsp;</p>\n" + 
				"    <p class=\"p4\">&nbsp;</p>\n" + 
				"   <p class=\"p5\"><strong>Declaration / Undertaking/Representation by Borrower</strong></p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li6\">I/We hereby apply for a finance facility as proposition made by <strong>Resilient Innovation Private Limited (&ldquo;BharatPe&rdquo;)</strong> as in terms of Agreements, if any and declare that all the particulars, information and details provided and other documents submitted by me/us are true, correct, complete and up-to-date in all respects and that I/We have not withheld any material information.</li>\n" + 
				"   <li class=\"li6\">I/We hereby authorize <span class=\"s2\">Lender</span>/BharatPe/Liquiloans to exchange or share information and details relating to my application to its group companies or any third party, as may be required or deemed fit, for the purpose of processing this loan application and/or related offerings or other products / services that I/We may apply for from time to time.</li>\n" + 
				"   <li class=\"li6\">By submitting this application, I/We hereby expressly authorize <span class=\"s2\">Lender</span>/BharatPe/Liquiloans to send me communications regarding loans, insurance and other products from <span class=\"s2\">Lender</span>/BharatPe, its group<span class=\"Apple-converted-space\">&nbsp; </span>companies and / or third parties through telephone calls / SMSs / emails / post etc. including but not limited to promotional, transactional communications. I/We confirm that I shall not challenge receipt of such communications by me as unsolicited communication, defined under TRAI Regulations on Unsolicited Commercial Communications.</li>\n" + 
				"   <li class=\"li6\">I authorize BharatPe / <span class=\"s2\">Lender</span> /Liquiloans to evaluate my transaction history on the BharatPe platform in order to check my eligibility for the loan and understand and acknowledge that <span class=\"s2\">Lender</span>/BharatPe /Liquiloans has the absolute discretion, without assigning any reasons to reject my application and that <span class=\"s2\">Lender</span>/BharatPe is not answerable / liable to me, in any manner whatsoever,<span class=\"Apple-converted-space\">&nbsp; </span>for<span class=\"Apple-converted-space\">&nbsp; </span>rejecting<span class=\"Apple-converted-space\">&nbsp; </span>my application.</li>\n" + 
				"   <li class=\"li6\">I / We agrees and accept that <span class=\"s2\">Lender</span>/BharatPe /Liquiloans may in its sole discretion, by its self or through authorised persons, advocate, agencies, bureau, etc. verify any information given, check credit references, employment details and obtain credit reports to determine creditworthiness from time to time.</li>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p7\">&nbsp;</p>\n" + 
				"   <p class=\"p8\"><strong>LINE OF CREDIT LOAN AGREEMENT</strong></p>\n" + 
				"   <p class=\"p9\">&nbsp;</p>\n" + 
				"   <p class=\"p10\">&nbsp;</p>\n" + 
				"   <p class=\"p11\">This <strong>Line of Credit</strong> <strong>Loan Agreement</strong> (&ldquo;<strong>Agreement</strong>&rdquo;) is made and executed at the place mentioned in <strong>Schedule I</strong> (<em>Terms of the Credit Facility</em>) and on the date mentioned in <strong>Schedule I</strong> (<em>Terms of the Credit Facility</em>) by and between:</p>\n" + 
				"   <p class=\"p11\"><strong>NDX P2P Private Limited</strong>, a non-banking finance company, having its registered office at 012, Lachiram Plaza, C Wing, GAKV Marg, Goregaon East, Mumbai 400063 (hereinafter referred to as the &ldquo;<strong>Liquiloans</strong>&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include its successor(s) and permitted assign(s)) of the One Part;</p>\n" + 
				"   <p class=\"p11\"><strong>AND</strong></p>\n" + 
				"   <p class=\"p11\"><strong><em>[Details from the Schedule I]</em></strong>, hereto as the borrower and co-borrower (if any) (wherever the context so requires) (hereinafter referred to as the &ldquo;<strong>Borrower</strong>&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include his/her/their heir(s), successor(s), legal representative(s), executor(s), administrator(s) and permitted assign(s)) of the Other Part.</p>\n" + 
				"   <p class=\"p11\">The Lender/Liquiloans<span class=\"s3\">/BharatPe</span> and the Borrower are hereinafter collectively referred to as the &ldquo;<strong>Parties</strong>&rdquo; and individually as the &ldquo;<strong>Party</strong>&rdquo;.</p>\n" + 
				"   <p class=\"p11\"><strong>WHEREAS</strong>:</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">Liquiloans is a non-banking finance company, registered with the RBI, bearing registration no. [N-13.02280] and having its registered office at 012, Lachiram Plaza, C Wing, GAKV Marg, Goregaon East, Mumbai 400063 is <em>inter alia</em> engaged in the business of advancing loans and other financial facilities. It operates a peer to peer lending platform under the brand name &lsquo;<strong>Liquiloans</strong>&rsquo; (hereinafter referred to as the &ldquo;<strong>Platform</strong>&rdquo;).</li>\n" + 
				"   <li class=\"li11\">The Borrower has approached to Liquiloans through the Platform/BharatPe and has requested for grant of Credit Facility (<em>defined hereinafter</em>) as set out in <strong>Schedule I</strong> and in reliance on the acceptance of the terms, conditions, assurances, representations and warranties of the Borrower, the Lenders through Liquiloans have agreed to grant Credit Facility as set out in <strong>Schedule I</strong>, subject to the terms and conditions contained in this Agreement.</li>\n" + 
				"   <li class=\"li11\">The Parties hereto are now desirous of <em>inter alia</em> entering into this Agreement to set out the terms and conditions in relation to the Credit Facility.</li>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p11\"><strong>Now, therefore, in view of the foregoing and in consideration of the mutual covenants and agreements herein set forth, the parties hereby agree as follows:</strong></p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li12\">DEFINITIONS AND INTERPRETATION</li>\n" + 
				"   </ul>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\"><strong>Definitions</strong></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p14\"><span class=\"s4\">&ldquo;<strong>Borrower Account</strong>&rdquo; </span>means the following bank account of the Borrower <strong><em>as mentioned in Schedule I</em></strong>, unless otherwise notified by the Borrower in writing<span class=\"s4\">. </span></p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Business Days</strong>&rdquo; means a day (other than Saturday and Sunday) on which banks are normally open for business.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Credit Facility</strong>&rdquo; means the line of credit facility amount mentioned in <strong>Schedule I</strong> (<em>Terms of the Credit Facility</em>).</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Debt</strong>&rdquo; shall mean the aggregate of all present and future indebtedness of the Borrower (including the Credit Facility) and obligations to pay or repay monies (whether principal, interest, fees, as letters of credit or otherwise) whether secured or unsecured, including any subordinate debt, loan or borrowing from, or issue of debentures by the Borrower, to any Person (including the Lender/Liquiloans).</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Drawdown</strong>&rdquo; means any drawdown of the amounts permitted under the Credit Facility by the Borrower in accordance with this Agreement.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Drawdown Notice</strong>&rdquo; means a notice from the Borrower to the Lender/Liquiloans requesting that all or a portion of the Credit Facility be disbursed to the Borrower.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Events of Default</strong>&rdquo; has the meaning ascribed to it under Clause 15.1 of this Agreement.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Final Settlement Date</strong>&rdquo; means the date on which all the Outstanding Amounts have been fully paid and the Credit Facility has been irrevocably discharged to the satisfaction of the Lender/Liquiloans/BharatPe.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Financing Documents</strong>&rdquo; means this Agreement and such other documents as may be executed or required to be executed between the Lender/Liquiloans/BharatPe and/or the Borrower in order to perfect or validate this Agreement.</p>\n" + 
				"   <p class=\"p16\">&ldquo;<strong>Government Authority</strong>&rdquo; means any governmental department, commission, board, bureau, agency, regulatory authority, instrumentality, court or other judicial, quasi-judicial or administrative body, whether central, state, provincial or local, having jurisdiction over the subject matter or matters in question. For avoidance of doubt, it is hereby clarified that the term &ldquo;Government Authority&rdquo; does not include any bank/financial institution acting solely in its capacity as a lender to the Borrower.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>GST</strong>&rdquo; shall include the Central Goods and Services Tax (&lsquo;CGST&rsquo;), the State Goods and Services Tax (&lsquo;SGST&rsquo;), Integrated Goods and Services Tax(&lsquo;IGST&rsquo;), Union Territory Goods and Services Tax (&lsquo;UTGST&rsquo;) and any other taxes levied under the GST related legislations in India as may be applicable. The term &lsquo;GST legislation/s&rsquo; should be accordingly interpreted.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Interest Rate</strong>&rdquo; means the rate of interest mentioned in <strong>Schedule I</strong> (<em>Terms of the Credit Facility</em>).</p>\n" + 
				"   <p class=\"p16\">&ldquo;<strong>Laws</strong>&rdquo; means any statute, law, regulation, ordinance, rule, judgment, order, decree, bye-laws, rule of law, directives, guidelines policy, requirement, or any governmental restriction or any similar form of decision of, or determination by, or any interpretation or administration having the force of law of any of the foregoing, by any Government Authority having jurisdiction over the matter in subject, whether in effect as of the date of this Agreement or hereafter.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Loan Application</strong>&rdquo; means the application made by the Borrower in the form specified by Lender/Liquiloans<span class=\"s3\">/BharatPe</span> for availing the Credit Facility and where the context so requires, all other information, particulars submitted by the Borrower to the Lender/Liquiloans/BharatPe with a view to avail the Credit Facility.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Material Adverse Effect</strong>&rdquo; means adverse effect on: (a) the ability of the Borrower to observe and perform in a timely manner their respective obligations under any of the Financing Documents to which it is or would be a party or; (b) the legality, validity, binding nature or enforceability of any of the Financing Documents; or (c) the Business or financial condition of the Borrower which is reasonably likely to impair its ability to service the Credit Facility as and when becoming due; or the rights and remedies of the Lender/Liquiloans/BharatPe under the Financing Documents.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Outstanding Amounts</strong>&rdquo; mean principal amount of the Credit Facility outstanding from time to time, and all interests, Penal Interest, costs, commissions, fees &amp; charges, expenses and other amounts due under or in respect of this Agreement.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Person</strong>&rdquo; shall, unless specifically provided otherwise, mean any individual, corporation, partnership, association of persons, company, joint stock company, trust or Government Authority, as the context may admit.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Purpose</strong>&rdquo; means the purpose for which the Credit Facility has been agreed to be utilised by the Borrower, as mentioned in <strong>Schedule I</strong> (<em>Terms of the Credit Facility</em>) to this Agreement.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>RBI</strong>&rdquo; means the Reserve Bank of India.</p>\n" + 
				"   <p class=\"p15\">&ldquo;<strong>Tax</strong>&rdquo; means any tax, levy, impost, duty or other charge or withholding of a similar nature (including any penalty or interest payable in connection with the failure to pay or delay in paying any of the same).</p>\n" + 
				"   <p class=\"p17\">&ldquo;<strong>Term</strong>&rdquo; or &ldquo;<strong>Tenure</strong>&rdquo; means the period as specified in <strong>Schedule I</strong> (<em>Terms of the Credit Facility</em>) of this Agreement, within which the Credit Facility has to be repaid by the Borrower to the Lender/Liquiloans along with interest, cost, expenses, fees &amp; charges and other amount as specified in this Agreement.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li5\"><strong>Principles of Interpretation</strong>: In this Agreement, unless the context otherwise requires:</li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p16\"><strong>T</strong>he headings are for convenience or reference only and shall not be used in and shall not affect the construction or interpretation of this Agreement.</p>\n" + 
				"   <p class=\"p16\"><strong>T</strong>he words &ldquo;include&rdquo; and &ldquo;including&rdquo; are to be construed without limitation.</p>\n" + 
				"   <p class=\"p16\"><strong>W</strong>ords importing a particular gender shall include all genders.</p>\n" + 
				"   <p class=\"p16\"><strong>R</strong>eferences to any law shall include references to such law as it may, after the date of this Agreement, from time to time be amended, supplemented or re-enacted.</p>\n" + 
				"   <p class=\"p16\"><strong>T</strong>he Schedule(s) annexed to this Agreement form an integral part of this Agreement and will be of full force and effect as though they were expressly set out in the body of the Agreement;</p>\n" + 
				"   <p class=\"p16\"><strong>R</strong>eference to any agreement, including this Agreement, deed, document, instrument, rule, regulation, notification, statute or the like shall mean a reference to the same as may have been duly amended, modified or replaced. For the avoidance of doubt, a document shall be construed as amended, modified or replaced only if such amendment, modification or replacement is executed in compliance with the provisions of such document(s);</p>\n" + 
				"   <p class=\"p18\"><strong>I</strong>n the event of any disagreement or dispute between the Lender/Liquiloans/BharatPe and the Borrower regarding the materiality or reasonableness of any matter, the opinion of Liquiloans/BharatPe as to the materiality shall be final and binding on the Borrower.</p>\n" + 
				"   <p class=\"p18\"><strong>E</strong>ach Party represents and warrants that it has read and understood the contents of this Agreement and that this Agreement will not be construed in favor of or against either Party due to that Party drafting the Agreement.</p>\n" + 
				"   <p class=\"p19\">&nbsp;</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li12\">CREDIT FACILITY</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li12\"><strong>The Borrower has requested the Lender/Liquiloans/BharatPe to provide, and the Lender, at the request of the Borrower, has agreed to grant to the Borrower, the Credit Facility, on the basis and subject to the covenants and terms and conditions set forth herein. </strong></li>\n" + 
				"   <li class=\"li12\"><strong>The Lender/Liquiloans/BharatPe shall have the right to adjust and/or set off any Outstanding Amounts or other dues against any subsequent amount of the Facility due to be disbursed by the Lender through Liquiloans to the Borrower.</strong></li>\n" + 
				"   <li class=\"li12\"><strong>Notwithstanding anything stated herein, the continuation of the Facility shall be at sole and absolute discretion of the Lender/Liquiloans/BharatPe and the Lender/Liquiloans/BharatPe may at any time in its sole discretion and without assigning any reason call upon the Borrower to pay the Outstanding Balance and upon such demand by the Lender/Liquiloans/BharatPe, the Borrower shall, within 48 hours of being so called upon, pay the whole of the Outstanding Balance to the Lender without any delay or demur. </strong></li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\">DRAWDOWN</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li12\"><strong>During the Term, the Borrower may drawdown money, from time to time, up to the limit of the Credit Facility granted to the Borrower in the manner as set out in this Agreement. Each Drawdown shall be made upon the Borrower delivering a Drawdown Notice to the Liquiloans/BharatPe. The Drawdown Notice shall clearly specify the amount of Credit Facility to be drawn.</strong></li>\n" + 
				"   <li class=\"li12\"><strong>Without prejudice to any other provision of this Agreement, the Liquiloans/BharatPe shall proceed with Drawdown of the Credit Facility only upon the Borrower duly providing all KYC related details and documents, the PDC (<em>defined hereinafter</em>) and satisfying such other conditions as required by the Liquiloans/BharatPe including without limitation delivery of the duly completed Drawdown Notice in respect thereof. </strong></li>\n" + 
				"   <li class=\"li12\"><strong>Liquiloans/BharatPe shall record appropriate entries in its books in relation to the Credit Facility and such entries shall be final and binding upon the Borrower and the Lender.</strong></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p20\">&nbsp;</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li12\">MODE OF DISBURSAL</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li12\"><strong>Within [3 (three) Business Days] from the receipt of Drawdown Notice, the Lender through Liquiloans shall transfer the amount of Credit Facility as specified in the Drawdown Notice. Disbursement shall be made directly and only to Borrower or as instructed by Borrower in writing. </strong></li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\">INTEREST</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li12\"><strong>The Borrower shall pay interest on the principal amount of the Credit Facility outstanding from time to time at the Interest Rate mentioned in </strong>Schedule I<strong> (<em>Terms of the Credit Facility</em>) to this Agreement. Such interest shall be paid in arrears, with daily rests.</strong></li>\n" + 
				"   <li class=\"li12\"><strong>Interest on the Credit Facility will begin to accrue in favour of the Lender/Liquiloans as and from the date of disbursal of amount of Credit Facility. Interest shall accrue from day to day and shall be computed on the basis of 365 days a year (irrespective of leap year) and the actual number of days elapsed. </strong></li>\n" + 
				"   <li class=\"li12\"><strong>Without prejudice to the Lender/Liquiloans/BharatPe&rsquo;s rights, Interest and any other charges shall be charged/debited to the Borrower Account. </strong></li>\n" + 
				"   <li class=\"li12\"><strong>Lender/Liquiloans/BharatPe at its sole discretion, may change in the prevailing rate of interest on the Facility, either due to change in its policies, or issuance of RBI guidelines and notifications with respect to the same or for any other reason whatsoever and in such an event the term 'Interest Rate' shall for all purposes mean the revised interest rate, which shall always be construed as agreed to be paid by the Borrower and hereby secured.</strong></li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\">REPAYMENT</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\">Subject to the terms and conditions of this Agreement, the Borrower shall repay the Outstanding Amounts, in full, on the Due Date; unless the Outstanding Amounts have otherwise been repaid to the Lender/Liquiloans/BharatPe on demand prior to the Due Date pursuant to a Financing Document.</li>\n" + 
				"   <li class=\"li13\">Subject to the terms and conditions of this Agreement, the Borrower may, at its option, at any time or from time to time, prepay the Credit Facility, in whole or in part, without premium or penalty.</li>\n" + 
				"   <li class=\"li13\">No notice, reminder or intimation in any manner shall be given by the Lender/Liquiloans/BharatPe to the Borrower regarding its obligation and responsibility to ensure repayment of the Outstanding Amounts to the Lender in terms of this Agreement. It shall be entirely the Borrower&rsquo;s responsibility to ensure repayment of all Outstanding Amounts.</li>\n" + 
				"   <li class=\"li13\">The Lender/Liquiloans have appointed Resilient Innovations Private Limited (BharatPe) having registered office at 90/20, Malviya Nagar, New Delhi 110017 as its collection agent and for such other services as agreed between the Lender through Liquiloans and BharatPe, from time to time. All Outstanding Balance shall be payable/paid as may be directed &amp; advised by Lender/BharatPe.</li>\n" + 
				"   <li class=\"li13\">The Borrower agrees that the repayment of the amount of Facility together with interest, Penal Interest, if any, and all such other sums due and payable by the Borrower to the Lender/Liquiloans/BharatPe shall be payable to the Lender/Liquiloans/BharatPe by way of a Payment Mechanism approved by the Lender/Liquiloans/BharatPe, provided that the Lender/Liquiloans/BharatPe may, at its sole discretion, require the Borrower to adopt or switch to any alternative mode of payment and the Borrower shall comply with such request, without demur or delay. The Borrower undertakes to remit all Outstanding Amounts to the Lender on the respective Due Date.</li>\n" + 
				"   <li class=\"li13\">Any instruction to repay the Outstanding Amounts which is revoked/ dishonoured shall make the Borrower liable for payment of charges, in addition to any Penal Interest that may be levied by the Lender/Liquiloans/BharatPe and without prejudice to the Lender/Liquiloans/BharatPe s right to take appropriate legal action against the Borrower for such revocation / dishonour.</li>\n" + 
				"   <li class=\"li13\">The Lender/Liquiloans/BharatPe expressly reserves its right to call upon the Borrower to pay the whole or part of the Outstanding Amounts at any time after the date of first Drawdown in the event of a default by the Borrower under any Financing Document.</li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\">PENAL INTEREST</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li12\"><strong>Upon occurrence of any of the events mentioned in Clause 15 below, the Borrower shall be liable to pay Penal Interest which shall be in addition to the Interest payable by the Borrower under Clause 5.1.</strong></li>\n" + 
				"   <li class=\"li12\"><strong>The Borrower expressly agrees that the rate of Penal Interest is a fair estimate of the loss likely to be suffered by the Lender/Liquiloans</strong> <strong>by reason of such delay/default on the part of the Borrower.</strong></li>\n" + 
				"   <li class=\"li12\"><strong>Penal Interest shall accrue from day to day and shall be computed on the basis of 365 (three hundred and sixty) days a year (irrespective of leap year).</strong></li>\n" + 
				"   <li class=\"li12\"><strong>Penal Interest shall be computed for (i) in case the Penal Interest is payable due to default/delay in any payment, then the period commencing from the Due Date of payment of the amount in default/delay up to the payment of amount in default/delay along-with Penal Interest and (ii) in case of occurrence of any other Event of Default, for the period during which the Event of Default or breach, as the case may be, persists.</strong></li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\">FEES AND OPERATING EXPENSES</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li12\"><strong>The Borrower shall, on or before the Drawdown of the Credit Facility, pay to Lender/Liquiloans/BharatPe<span class=\"Apple-converted-space\">&nbsp; </span>processing/service fee calculated at the rate provided in </strong>Schedule I<strong> (<em>Terms of the Credit Facility</em>) to this Agreement, on the amount of the sanctioned Credit</strong> <strong>Facility along-with applicable GST. The processing/service fee shall be non-refundable. Lender/Liquiloans shall be entitled to recover the non-refundable processing fees and GST by way of deduction from Drawdown(s).</strong></li>\n" + 
				"   <li class=\"li12\"><strong>The Borrower shall, on or before or after the disbursement of the Credit</strong> <strong>Facility, bear, pay and reimburse to Lender/Liquiloans/BharatPe all cost, fee, charges, including stamp duty charges, applicable on the Financing Documents, on a full indemnity basis, in connection with the Credit Facility.</strong></li>\n" + 
				"   <li class=\"li11\">All fees and charges payable by the Borrower to the Lender/Liquiloans<span class=\"s3\">/BharatPe</span> under this Clause shall be reimbursed by the Borrower within 7 (seven) days from the date of notice of demand from Lender/Liquiloans<span class=\"s3\">/BharatPe</span> and shall carry interest at the same rate as payable on the Credit Facility from the date of payment till reimbursement.</li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\">APPROPRIATION OF PAYMENTS</li>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">Unless otherwise determined by the Lender/Liquiloans/BharatPe, any payments made by the Borrower towards payments due and payable under this Agreement shall be appropriated in the following order, <em>viz</em>.:</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\">Interest on costs, charges, expenses and other monies, if any;</li>\n" + 
				"   <li class=\"li13\">costs, charges, expenses and other monies incurred by the Lender, if any;</li>\n" + 
				"   <li class=\"li13\">Penal Interest, if any;</li>\n" + 
				"   <li class=\"li13\">Interest;</li>\n" + 
				"   <li class=\"li13\">Repayment of the principal amount of the Credit Facility.</li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\">TAXES</li>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p17\">The Borrower shall make all payments to be made by it hereunder without and free from any Tax deduction and/or other deduction and/or withholding and/or statutory levies/duties/charges (&ldquo;<strong>Withholding</strong>&rdquo;), unless a Withholding is required by Law.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li12\">PURPOSE OF THE CREDIT FACILITY</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\">The Borrower undertakes and confirms that the entire Credit Facility amount shall be utilized/ deployed only for the Purpose and for no other purpose that shall include without limitation to invest in share market, real estate or in any subsidiary/ associates of the Borrower.</li>\n" + 
				"   <li class=\"li13\">Any default, fraud, legal incompetence during the currency of the limits, non-compliance of agreed terms and conditions, non-submission of required papers, any other irregularities by the Borrower will enable the Lender through Liquiloans to recall the Credit Facility.</li>\n" + 
				"   <li class=\"li13\">The Borrower further confirms and/or undertakes that the Credit Facility shall not be utilized for the following:</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">Subscription to or purchase of shares/debentures;</li>\n" + 
				"   <li class=\"li11\">Extending unsecured loans to subsidiary company/ associates or for making inter corporate deposits;</li>\n" + 
				"   <li class=\"li11\">Any speculative purposes or any anti-social purpose or any unlawful purpose.</li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\">AFFIRMATIVE COVENANTS</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\">The Borrower agrees to promptly notify, in writing, the Lender/Liquiloans/BharatPe about any litigation, arbitration, investigative, regulatory or administrative proceeding/action having a Material Adverse Effect.</li>\n" + 
				"   <li class=\"li13\">The Borrower declares that all the amounts including the amount of own contribution paid/ payable in connection with the Credit Facility, is/ shall be through legitimate source and does not/ shall not constitute an offence of money laundering under the Prevention of Money Laundering Act, 2002.</li>\n" + 
				"   <li class=\"li13\">The Borrower shall perform, on request of the Lender/Liquiloans/BharatPe, such acts as may be necessary to carry out the intent of the Financing Documents.</li>\n" + 
				"   <li class=\"li13\">The Borrower shall deliver to the Lender/Liquiloans/BharatPe in form and detail, such information, to the satisfaction of the Lender/Liquiloans/BharatPe.</li>\n" + 
				"   <li class=\"li13\">In case the Borrower is a body corporate, it shall not induct any person on the board of directors or as partners who have been identified as a wilful defaulter by the RBI. The Borrower confirms that neither it nor any member of its organisation has been declared as wilful defaulter.</li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\">NEGATIVE COVENANTS</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li12\"><strong>The Borrower hereby agrees, undertakes and covenants that, so long as the Credit</strong> <strong>Facility or any part thereof is outstanding and an Event of Default has occurred and continuing, until full and final payment of all money owing hereunder, the Borrower </strong>SHALL NOT<strong>:</strong></li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">Grant any loans; grant any credit (except in the ordinary course of business) to or for the benefit of any Person other than itself.</li>\n" + 
				"   <li class=\"li11\">Allow its principal shareholders/ directors/ promoters/ partners to withdraw monies brought in by them or withdraw the profits earned in the business/capital invested in the business.</li>\n" + 
				"   <li class=\"li11\">Undertake guarantee obligations on behalf of any other borrower or any third Person.</li>\n" + 
				"   <li class=\"li11\">Cancel the Credit Facility or refuse to accept disbursement of the Credit Facility, except with the prior written consent of the Lender/Liquiloans<span class=\"s3\">/BharatPe</span>.</li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\"><span class=\"s1\"><strong>Further Undertakings</strong></span><strong>:</strong></li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">If applicable, it is the responsibility of the Borrower to communicate the GSTIN number of particular state for the purpose of billing.</li>\n" + 
				"   <li class=\"li11\">In case of Borrowers, the address given under Borrower&rsquo;s Details as mentioned in <strong>Schedule I</strong> (<em>Terms of the Credit Facility</em>) shall be considered as the registered place of business for the purpose of computation of GST.</li>\n" + 
				"   <li class=\"li11\">The Borrower will forthwith report to the Lender/Liquiloans<span class=\"s3\">/BharatPe</span> all frauds/defalcations along with the action taken/proposed.</li>\n" + 
				"   <li class=\"li11\">The Borrower expressly agrees and understands and hereby undertakes and commits that:</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">the Borrower shall at no time close the Borrower Account from which the PDC has been issued or under no circumstances shall issue any communication to the concerned banker for stop payment of the PDC;</li>\n" + 
				"   <li class=\"li11\">the said PDC on presentation for encashment, shall be duly honoured/encashed on the Due Date;</li>\n" + 
				"   <li class=\"li11\">it will not change or resolve to change the authorized signatory for the bank account from which the PDC is issued. In the event of any change in the authorized signatory, as aforesaid, the Borrower shall promptly replace the PDC;</li>\n" + 
				"   <li class=\"li11\">non-presentation of any PDC for any reason whatsoever will not release the Borrower from any liability to pay the monies under this Agreement on the due date. The Borrower agrees to replace the PDC/issue fresh cheques if required by the Lender;</li>\n" + 
				"   <li class=\"li11\">The Borrower fully understands that he/she/it and its directors shall be liable for prosecution under section 138 read with Section 141 of The Negotiable Instruments Act, 1881 as amended and up-to-date, if any PDC is returned unpaid on presentation. The Borrower further acknowledges that in addition to the criminal liability under section 138 read with Section 141 of The Negotiable Instruments Act, 1881, the Borrower will also be liable for civil liability and damages.</li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\">REPRESENTATIONS AND WARRANTIES</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">The Borrower hereby represents, warrants and covenants to the Lender on a continuing basis that:</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\"><span class=\"s1\"><strong>Confirmation of Loan Application</strong></span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">The Borrower acknowledges and confirms that all the factual information provided by the Borrower to the Lender/Liquiloans/BharatPe in the Loan Application or otherwise in order to avail the Credit Facility and any prior or subsequent information or explanation given to the Lender/Liquiloans/BharatPe in this regard is true and accurate in all material respects as at the date it was provided and does not omit to state a material fact necessary in order to make the statements contained therein misleading in the light of the circumstances under which such statements were or are made.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\"><span class=\"s1\"><strong>Disclosure of material changes</strong></span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">Since the date of the last audited balance sheet of the Borrower (or end of last financial year in case of persons/entities not requiring an audited balance sheet), if applicable, there has been no material change in the financial condition of the Borrower which is likely to materially and/or adversely affect the ability of the Borrower to perform its obligation under the Financing Documents.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\"><span class=\"s1\"><strong>Compliance with Laws</strong></span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">The Borrower has complied with all the applicable Laws and is not a party to any litigation, arbitration or administrative or regulatory proceedings or investigations of a material character and that the Borrower is not aware, to the best of its knowledge and belief, of any facts likely to give rise to such litigation, arbitration or administrative or regulatory proceedings or investigations or to material claims against the Borrower.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\"><span class=\"s1\"><strong>Litigation</strong></span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">Where applicable, the Borrower shall supply to the Lender/Liquiloans/BharatPe, promptly upon becoming aware of them, details of any filing by any creditor (financial creditor or operational creditor) which are made or threatened against them, in accordance with the provisions of the Insolvency and Bankruptcy Code, 2016 or any analogous laws.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\"><span class=\"s1\"><strong>Compliance of Know Your Customer (KYC) Policy:</strong></span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">The Borrower is fully aware of the KYC Policy of the Lender/Liquiloans and RBI and confirms that the information/clarification/documents/signage provided by it on its identity, address, authorised signatory, board resolution, PAN and all other material facts are true and correct and the transaction, etc. are <em>bonafide </em>and as per Law. The Borrower further confirms that it has disclosed all facts/information as are required to be disclosed for the adherence and compliance of the provisions related to the KYC Policy. The Lender/Liquiloans/BharatPe reserves the right to recall the Credit Facility or close the account in case the required documents are not provided by the Borrower to the Lender/Liquiloans/BharatPe.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\"><strong>The Borrower is entitled and empowered to borrow the Credit</strong> <strong>Facility and all other Financing Documents and upon execution, the same will create legal and binding obligations on the Borrower enforceable in accordance with their respective terms, and the person(s) executing such document(s) on behalf of the Borrower have been duly authorised to do so.</strong></li>\n" + 
				"   <li class=\"li13\"><strong>All the necessary approvals, for availing the Credit</strong> <strong>Facility from the Lender/Liquiloans/BharatPe have been obtained and/or shall be obtained at the required stage by the Borrower.</strong></li>\n" + 
				"   <li class=\"li13\"><strong>The execution and delivery of this Agreement and documents to be executed in pursuance hereof, and the performance of the Borrower&rsquo;s obligations hereunder and thereunder does not and will not (i) contravene any applicable Law, statute or regulation or any judgment or decree to which any of the Borrowers and/or their Assets and/or business and/or their undertaking is subject, or (ii) conflict with or result in any breach of, any of the terms of or constitute default of any covenants, conditions and stipulations under any existing agreement or contract or binding to which any of the Borrowers are a party or subject or (iii) conflict or contravene any provision of the memorandum and the articles of association and/or any constituting/governing documents of Borrowers. </strong></li>\n" + 
				"   <li class=\"li13\"><span class=\"s1\"><strong>No</strong> <strong>default</strong></span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p22\">The Borrower and/or its group companies, affiliates have no over dues/not defaulted in repayment of any amount due and payable to any other bank/financial institutions.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\"><span class=\"s1\"><strong>Insolvency</strong></span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p22\">No corporate action, legal proceeding or other procedure or step proceedings or any other creditors&rsquo; process has been taken or is currently pending or threatened in relation to the Borrower.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\"><span class=\"s1\"><strong>Material Adverse Effect</strong></span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p22\">There are no facts or circumstances, conditions or occurrences, which could collectively or otherwise be expected to result in the Borrower being unable to perform their respective obligations under the Financing Documents to which they are expressed to be a party, or which could affect the legality, validity, binding nature or enforceability of this Agreement or other Financing Documents or is otherwise expected to have a Material Adverse Effect.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li12\">EVENT OF DEFAULT AND CONSEQUENCES</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">The Borrower expressly and irrevocably hereby agrees and declares that each of the following events or events similar thereto shall constitute an &ldquo;<strong>Events of Default</strong>&rdquo;:</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\">Non-Payment</li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">If the Borrower fails to pay within the time stipulated and in the manner specified in this Agreement for any sum due hereunder, whether principal, interest, Penal Interest, costs, fees, charges, expenses or otherwise any other sum due in pursuance hereof a Financing Document.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\">General Default</li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">The breach of, or omission to observe, or default or delay by the Borrower in observing any of its, obligations, commits any breach of any of the terms, representations, warranties, convenience, covenants or undertakings or any term, condition, provision of the Financing Documents.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\">Misrepresentation</li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">Any representation or warranty or assurance or covenant on the part of the Borrower made to the Lender/BharatPe or their employees,<span class=\"Apple-converted-space\">&nbsp; </span>or deemed to be made or repeated in pursuant to this Agreement or in any notice, certificate or statement or other writing referred to herein or delivered hereunder comes to knowledge of the Lender/BharatPe to be incorrect or misleading in any material respect or concealment of necessary or essential information by the Borrower based on which the Lender/BharatPe has been prompted to act and enter into any Financing Document.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\">Cross Default</li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">If any cross default as below occurs:</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">Any Debt (other than the Credit Facility) becomes due and payable prior to its stated maturity for any reason whatsoever or any such Debt is not paid at its originally stipulated maturity date;</li>\n" + 
				"   <li class=\"li11\">If the Borrower admits in writing its inability to pay its debts as they mature, or stops, suspends or threatens to stop or suspend payment of all or any part of its Debts.</li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li13\">Cessation of Business</li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">The Borrower ceases or threatens to cease to carry on the business it carries as on the date hereof.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\">Insolvency</li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">An application is filed by any financial creditor or any operational creditor of the Borrower or itself for the insolvency resolution process under the Insolvency and Bankruptcy Code, 2016.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Consequences of Event of Default</span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">On and any time after the occurrence of Event of Default, the Lender may, without prejudice to any other rights that it may have under this Agreement or applicable Law (including right to accelerate payment obligations of the Borrower under the Financing Documents) take one or more of the following actions:</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li13\"><strong>recall or declare the Outstanding Amounts to be forthwith due and payable, whereupon such amounts shall become forthwith due and payable without presentment, demand, protest or any other notice of any kind, all of which are hereby expressly waived, anything contained herein to the contrary notwithstanding;</strong></li>\n" + 
				"   <li class=\"li13\"><strong>exercise any and all rights specified in the Financing Documents including, without limitation, to enforce any security created/provided;</strong></li>\n" + 
				"   <li class=\"li13\"><strong>to initiate, appropriate proceedings for recovery of its dues by invoking the jurisdiction of appropriate court at its sole discretion, in addition to taking further action or actions under any other statute in force; and/or </strong></li>\n" + 
				"   <li class=\"li13\"><strong>exercise such other remedies as permitted or available under applicable law in the sole discretion of the Lender; and/or</strong></li>\n" + 
				"   <li class=\"li13\"><strong>disclose the name of the Borrower, and its promoters/directors/partners to RBI, TransUnion CIBIL and/or any other authorised agency.</strong></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\">TERMINATION</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">This Agreement shall stand terminated if: (i) the Borrower has failed to issue the Drawdown Notice and/or complete or adhere to the conditions for disbursement in the manner contemplated in this Agreement, or (ii) the Credit Facility has not been availed before the expiry of Availability Period, or such later date as may be agreed to in writing by the Lender.</li>\n" + 
				"   <li class=\"li11\">This Agreement shall expire on the Final Settlement Date, and thereupon the Lender shall forthwith return/cancel/destroy the relevant PDC.</li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\">SUCCESSION</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">In case of the death of the Borrower, where the Borrower is an individual and the Lender/Liquiloans agrees to continue extending the Credit Facility, the legal representative of the Borrower shall do the following:</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">Replace the PDC for fees, charges etc signed by the deceased Borrower, in the same manner as provided in this Agreement as if he were the Borrower in the first instance.</li>\n" + 
				"   <li class=\"li11\">Execute a fresh Agreement, powers of attorney and such other documents as required by the Lender/Liquiloans<span class=\"s3\">/BharatPe</span>.</li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li12\">MISCELLANEOUS</li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Benefit of Agreement</span></li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">This Agreement shall be binding upon and ensure to the benefit of and be enforceable by the respective successors and assigns of the Parties hereto; provided, however, that the Borrower shall not assign or transfer any of their rights or obligations hereunder without the prior written consent of the Lender/Liquiloans<span class=\"s3\">/BharatPe</span>.</li>\n" + 
				"   <li class=\"li11\">The Lender/Liquiloans<span class=\"s3\">/BharatPe</span> may, without the consent of the Borrower or any other Person, assign any or all of its rights and benefits hereunder or transfer or novate in all or part of its rights, benefits and obligations hereunder or under other Financing Documents, on the same terms and conditions as contained herein/therein.</li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Governing Law and Jurisdiction</span></li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">This Agreement and the rights and obligations of the Parties hereunder shall be construed in accordance with and be governed by the laws of India.</li>\n" + 
				"   <li class=\"li11\">The Parties agree that the courts in New Delhi shall have exclusive jurisdiction to settle any disputes which may arise out of or in connection with the Financing Documents and that accordingly any suit, action or proceedings (together referred to as &ldquo;<strong>Proceedings</strong>&rdquo;) arising out of or in connection with the Financing Documents may be brought in such courts and the Borrower irrevocably submit to and accept, unconditionally, the jurisdiction of those courts.</li>\n" + 
				"   <li class=\"li11\">The Borrower and the Lender irrevocably waive any objection, now or in future, to the venue of any Proceedings being the courts at New Delhi or any claim that any such Proceedings have been brought in an inconvenient forum.</li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Arbitration</span></li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">Without prejudice to the other legal remedies available to the Lender/Liquiloans<span class=\"s3\">/BharatPe</span> under applicable law (including under the SARFAESI Act, 2002 and Insolvency and Bankruptcy Code, 2016), any dispute arising out of or in connection with the Financing Documents shall be referred to and finally resolved by arbitration under the Arbitration and Conciliation Act, 1996 (as amended from time to time).</li>\n" + 
				"   <li class=\"li11\">The arbitration shall be referred to a sole arbitrator appointed by the Liquiloans/<span class=\"s3\">BharatPe</span>. The seat and venue of the arbitration shall be New Delhi. The language of the arbitration and the award of the arbitrator shall be in the English language. The award of the arbitrator shall be final and binding on the Parties and the expenses of the arbitration shall be borne in such manner as the arbitrator may determine.</li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Indemnity</span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p17\">Without prejudice to and in addition to other provisions contained in the Financing Documents, the Borrower hereby agrees to indemnify the Lender/Liquiloans<span class=\"s3\">/BharatPe</span> and its directors, officers, representatives and agents against any losses, liabilities, claims, damages or the like (including, without limitation, reasonable attorneys&rsquo; fees and expenses) which may be sustained or incurred by any of them as a result of, or in connection with, or arising out of:</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">the Borrower failing to comply with the provisions of any Financing Documents and applicable Laws; and / or</li>\n" + 
				"   <li class=\"li11\">the occurrence of any Event of Default; and / or</li>\n" + 
				"   <li class=\"li11\">levy by any Government Authority of any charge, Taxes or penalty in connection with regularising or perfecting any of the Financing Documents as may be required under applicable Law at any time during the currency of the Credit Facility, or getting any of the Financing Documents admitted into evidence, or relying on any Financing Documents for proving any claim; and/or</li>\n" + 
				"   <li class=\"li11\">the exercise of any of the rights by the Lender/Liquiloans<span class=\"s3\">/BharatPe</span> under this Agreement and any of the Financing Documents; and/or</li>\n" + 
				"   <li class=\"li11\">any of the representations and warranties of the Borrower or the Guarantor under the Financing Documents are found to be false or untrue or incorrect on a future date.</li>\n" + 
				"   </ul>\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Confidentiality</span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p17\">Any information supplied by a Party to another Party pursuant hereto which is by its nature can reasonably be construed to be proprietary or confidential or is marked &ldquo;confidential&rdquo; (&ldquo;<strong>Confidential Information</strong>&rdquo;) shall be kept confidential by the recipient unless or until compelled to disclose the same (i) by judicial or administrative process, or (ii) by law, or unless the same (iii) is in or is a part of public domain, or (iv) is required to be furnished to the bankers or investors or potential investors in the either Party, or (v) is required to be furnished to any Government Authority having jurisdiction over the recipient, or (vi) can be shown by the receiving Party to the reasonable satisfaction of the disclosing Party to have been known to the receiving Party prior to it being disclosed by the disclosing Party to the receiving Party, or (vii) subsequently comes lawfully into the possession of the receiving Party from a third party, and in such cases the confidentiality obligations shall cease to the extent required under the foregoing circumstances.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Amendments and Waivers</span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p17\">This Agreement (including the schedules, annexure and appendices hereto) may not be amended, supplemented or modified and no other Financing Document may be amended, supplemented or modified and no term or condition thereof may be waived without the written consent of the Parties to such Financing Document.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Severability</span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p17\">Any provision of this Agreement or any other Financing Document which is prohibited or unenforceable shall be ineffective to the extent of prohibition or unenforceability but shall not invalidate the remaining provisions of this Agreement or any Financing Document.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Survival</span></li>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">This Agreement shall be in force until all the Outstanding Amounts under this Agreement have been fully and irrevocably paid in accordance with the terms and provisions hereof.</li>\n" + 
				"   <li class=\"li11\">The obligations of the Borrower under the Financing Documents will not be affected by:</li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\">any unenforceability, illegality or invalidity of any obligation of any Person under a Financing Document; or</li>\n" + 
				"   <li class=\"li11\">the breach, frustration or non-fulfilment of any provisions of, or claim arising out of or in connection with a Financing Document.</li>\n" + 
				"   </ul>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Delay not to Impair the Rights of the Lender</span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p17\">Any delay in exercising or omission to exercise any right, power or remedy accruing to the Lender/Liquiloans<span class=\"s3\">/BharatPe</span> upon any default under this Agreement and/or any other Financing Document shall not impair any such right, power or remedy or shall be construed to be a waiver thereof or any acquiescence in such default, nor shall the action or inaction of the Lender/Liquiloans<span class=\"s3\">/BharatPe</span> in respect of any default or any acquiescence by it in any default shall affect or impair any right, power or remedy of the Lender/Liquiloans<span class=\"s3\">/BharatPe</span> in respect of any other default.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Right of Set-off</span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p17\">In addition to any rights now or hereafter granted under Applicable Law or otherwise, and not by way of limitation of any such rights, upon the occurrence and continuation of an Event of Default, the Lender/Liquiloans<span class=\"s3\">/BharatPe</span> is hereby authorised by the Borrower to, from time to time, without presentment, demand, protest or other notice of any kind to the Borrower, or to any other Person, set off and/or appropriate and/or apply any and all deposits (general or special) at any time held or owing by the Lender/Liquiloans<span class=\"s3\">/BharatPe</span> (including, without limitation, by any branches and agencies other than the lending office of Lender) to or for the credit or the account of the Borrower against and on account of the obligations and liabilities of the Borrower to the Lender/Liquiloans<span class=\"s3\">/BharatPe</span> under this Agreement or under any of the other Financing Documents.</p>\n" + 
				"   <p class=\"p23\">&nbsp;</p>\n" + 
				"   <p class=\"p23\">&nbsp;</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Counterparts</span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p17\">This Agreement may be executed in any number of counterparts and by the different Parties hereto on separate counterparts, each of which when so executed and delivered shall be effective for purposes of binding the Parties hereto, but all of which shall together constitute one and the same instrument.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Notices</span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p17\">All notices and other communications provided at various places in this Agreement shall be in writing and (a) sent by hand delivery, or (b) prepaid registered post with acknowledgment due, or (c) by e-mail followed by prepaid registered post with acknowledgment due, at the address and/or email first above written. All such notices and communications shall be deemed to have been delivered effective: (i) if sent by email, when sent (provided the email enters the sent folder of the sender), (ii) if sent by prepaid registered post, 3 (three) Business Days after its dispatch.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Effectiveness</span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">This Agreement shall become binding on the Parties hereto on and from the date hereof and shall be in force and effect till the Final Settlement Date.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Entire Agreement</span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">This Agreement and other Financing Documents shall represent the entire understanding of the Parties on the subject matter hereof and shall override all the previous understanding and agreement between the Parties hereto.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\"><span class=\"s1\">No Discrimination</span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">The Borrower shall, at all times during the term of this Agreement, ensure that no fraudulent preference is given to other Lender/Liquiloans/BharatPe of the Borrower, both present and future, so as to defeat Lender/Liquiloans/BharatPe&rsquo;s rights, either present and future under this Agreement or to fraudulently service the dues owed to other lenders in preference to the dues owed to the Lender/Liquiloans/BharatPe or to wilfully act in or consent to any third party acting in a manner as would cause a Material Adverse Effect.</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li11\"><span class=\"s1\">Remedies Cumulative</span></li>\n" + 
				"   </ul>\n" + 
				"   </ul>\n" + 
				"   <p class=\"p21\">The rights, power and remedies herein or in the other Financing Documents or expressly provided are cumulative and not exclusive of any rights, power or remedies which the Lender/Liquiloans/BharatPe would otherwise have under applicable Laws and Financing Documents.</p>\n" + 
				"   <p class=\"p24\">&nbsp;</p>\n" + 
				"   <p class=\"p24\">&nbsp;</p>\n" + 
				"   <p class=\"p25\">&nbsp;</p>\n" + 
				"   <p class=\"p25\">&nbsp;</p>\n" + 
				"   <p class=\"p26\"><em>[Space specifically left blank] </em></p>\n" + 
				"   <p class=\"p7\">&nbsp;</p>\n" + 
				"   <p class=\"p26\" style=\"text-align: center;\"><strong>SCHEDULE I</strong></p>\n" + 
				"   <p class=\"p27\">&nbsp;</p>\n" + 
				"   <p class=\"p26\" style=\"text-align: center;\"><strong>TERMS OF THE CREDIT FACILITY</strong></p>\n" + 
				"   <p class=\"p9\">&nbsp;</p>\n" + 
				"   <table class=\"t1 new-table1\" cellspacing=\"0\" cellpadding=\"0\">\n" + 
				"   <tbody>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p28\"><strong>S. NO.</strong></p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p29\"><strong>PARTICULARS</strong></p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p30\"><strong>DETAILS</strong></p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">1&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Date of Agreement</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p32\"><span class=\"s5\">"+(creditApplication.getAgreementAt()==null?"":creditApplication.getAgreementAt())+"</span></p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">2&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Place of Agreement</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p32\"><span class=\"s5\">"+(creditApplicationAddress.getCity()==null?"":creditApplicationAddress.getCity())+"</span></p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">3&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Credit Line Agreement No.</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p32\"><span class=\"s5\">"+(creditApplication.getExternalLoanId()==null?"":creditApplication.getExternalLoanId())+"</span></p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">4&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Name of Borrower</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p32\"><span class=\"s5\">"+(merchant.getBeneficiaryName()==null?(merchant.getMerchantName()==null?"":merchant.getMerchantName()):merchant.getBeneficiaryName())+"</span></p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">5&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Address of Borrower</p>\n" + 
				"   <p class=\"p33\">&nbsp;</p>\n" + 
				"   <p class=\"p31\">Email Address of Borrower</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p32\"><span class=\"s5\">"+(creditApplicationAddress.getShopNumber()==null?"":(creditApplicationAddress.getShopNumber()+","))+(creditApplicationAddress.getStreetAddress()==null?"":(creditApplicationAddress.getStreetAddress()+","))+(creditApplicationAddress.getArea()==null?"":(creditApplicationAddress.getArea()+","))+(creditApplicationAddress.getCity()==null?"":(creditApplicationAddress.getCity()+","))+(creditApplicationAddress.getState()==null?"":(creditApplicationAddress.getState()+","))+(creditApplicationAddress.getPincode()==null?"":(creditApplicationAddress.getPincode()))+"</span></p>\n" + 
				"   <p class=\"p31\">"+(creditApplication.getEmail()==null?"":creditApplication.getEmail())+"</p>\n" +
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">6&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Borrower&rsquo;s constitution</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p34\">"+(creditApplication.getMerchantStoreId()==null?"Individual":"Propreitor")+"</p>\n" + 
				"   <p class=\"p35\">&nbsp;</p>\n" + 
				"   <p class=\"p34\"></p>\n" + 
				"   <p class=\"p35\">&nbsp;</p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">7&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Purpose of the Credit Facility/ Proposed utilization of the Credit Facility</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p32\"><span class=\"s5\">"+(creditApplication.getMerchantStoreId()==null?"For General":"Business Use")+"</span></p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">8&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Amount of Line of Credit</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p34\"><span class=\"s5\">Rs. "+(creditApplication.getAmount()==null?"":creditApplication.getAmount())+"</span></p>\n" + 
				"   <p class=\"p35\">&nbsp;</p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">9&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Availability Period/Term/Tenure</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p34\">The period of<span class=\"s1\"> __12___ </span>months commencing from the date of execution of this Agreement.</p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">10&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Business of the Borrower</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p32\"><span class=\"s5\">"+(merchant.getBusinessCategory()==null?"":merchant.getBusinessCategory())+"</span></p>\n" + 
				"   <p class=\"p36\">&nbsp;</p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">11&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Penal Interest Rate</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p32\"><span class=\"s5\">0.15% per day if used as Flexible / monthly repayment option.\n" + 
				"This can change in future post as per BharatPe Policy</span></p>\n" + 
				"   <p class=\"p36\">&nbsp;</p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">12&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Interest Rate</p>\n" + 
				"   <ul class=\"ul1\">\n" + 
				"   <li class=\"li6\">Interest chargeable (In case of Floating Rate Loans)</li>\n" + 
				"   <li class=\"li6\">Interest chargeable (In case of Fixed Rate Loans)</li>\n" + 
				"   </ul>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p36\">(a) 2% per month\n" + 
				"(b) 0.1% per day;</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p36\">&nbsp;</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p32\">Fixed</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p36\">&nbsp;</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p32\">Variable</p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">13&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p33\">&nbsp;</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p36\">&nbsp;</p>\n" + 
				"   <p class=\"p32\">(a) ..........% (MCLR/........... +&hellip;&hellip;&hellip;)</p>\n" + 
				"   <p class=\"p36\">&nbsp;</p>\n" + 
				"   <p class=\"p32\">(b) %</p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">14&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Non-refundable Processing Fees /</p>\n" + 
				"   <p class=\"p31\">service charge</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p32\"><span class=\"s5\">Nil</span></p>\n" + 
				"   <p class=\"p36\">&nbsp;</p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">15&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">PDC</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p32\"><span class=\"s5\">To be collected during Physical Verification</span> (cheque number &amp; bank details)</p>\n" + 
				"   <p class=\"p36\">&nbsp;</p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">16&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Due Date</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p34\">Will be communicated at the time of Drawdown / Usage of\n" + 
				"\n" + 
				"Credit Line</p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">17&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p37\">Mode of communication of changes in interest rates</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p32\">App alerts</p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">18&nbsp;</td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p31\">Date on which annual outstanding balance statement will be issued</p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"   <p class=\"p32\">Annually Sent through E-mail ID</p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   </tbody>\n" + 
				"   </table>\n" + 
				"   <p class=\"p25\">&nbsp;</p>\n" + 
				"   <p class=\"p26\" style=\"text-align: center;\"><strong>[TABLE OF CHARGES]</strong></p>\n" + 
				"   <table class=\"t1 new-table3\" cellspacing=\"0\" cellpadding=\"0\">\n" + 
				"   <tbody>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p5\"><strong>Registration Fee</strong></p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p6\"><span class=\"s5\">Nil</span></p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p5\"><strong>Processing Fee</strong></p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p6\"><span class=\"s5\">Nil</span></p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p5\"><strong>Service Fee</strong></p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p6\"><span class=\"s5\">Nil</span></p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p5\"><strong>Late payment Charges</strong></p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p6\"><span class=\"s5\">0.15% per day if used as Flexible / Monthly repayment option.\n" + 
				"This can change in future post as per BharatPe Policy</span></p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p5\"><strong>Stamp Duty Charges</strong></p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p6\"><span class=\"s5\">Nil</span></p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   <tr>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p5\"><strong>Ledger Folio Charges</strong></p>\n" + 
				"   </td>\n" + 
				"   <td class=\"td1\" valign=\"middle\">\n" + 
				"   <p class=\"p6\"><span class=\"s5\">Nil</span></p>\n" + 
				"   </td>\n" + 
				"   </tr>\n" + 
				"   </tbody>\n" + 
				"   </table>\n" + 
				"   <p class=\"p38\">&nbsp;</p>\n" + 
				"   <p class=\"p38\">&nbsp;</p>\n" + 
				"   <p class=\"p25\">&nbsp;</p>";
		return html;
	}
	
	public String getTncForFixed(Merchant merchant,CreditApplication creditApplication,TncRequestDto requestDto) {
		CreditApplicationAddress creditApplicationAddress=creditApplicationAddressDao.findByMerchantIdAndApplicationId(merchant.getId(), creditApplication.getId());
		if(creditApplicationAddress==null) {
			return null;
		}
		String html="<p class=\"p1\" style=\"text-align: center;\"><span class=\"s1\"><strong>Loan Details</strong></span></p>\n" + 
				"   <style>\n" + 
				"        table.t1{\n" + 
				"            width: 100%;\n" + 
				"        }\n" + 
				"        .new-table1 td{\n" + 
				"            border: 1px solid #000;\n" + 
				"            width: 33%;\n" + 
				"            text-align: center;\n" + 
				"        }\n" + 
				"        .new-table2 td{\n" + 
				"            border: 1px solid #000;\n" + 
				"            width: 16.6%;\n" + 
				"            text-align: center;\n" + 
				"        }\n" + 
				"    </style>\n" + 
				"   <p class=\"p2\">Loan ID: <span class=\"data-insert\">"+(creditApplication.getExternalLoanId()==null?"":creditApplication.getExternalLoanId())+"</span></p>\n" + 
				"   <p class=\"p0\">Date:  <span class=\"data-insert\">"+new Date()+"</span></p>\n" + 
				"   <p class=\"p2\">Loan Amount (INR):<span class=\"Apple-converted-space\">&nbsp; </span> <span class=\"data-insert\">"+(requestDto.getAmount())+"</span></p>\n" + 
				"   <p class=\"p2\">Tenure (Months):<span class=\"Apple-converted-space\">&nbsp; &nbsp; </span><span class=\"data-insert\">"+requestDto.getTenure()+"</span></p>\n" + 
				"   <p class=\"p2\">Flat Rate of Interest (% per month)<span class=\"data-insert\">2</span></p>\n" + 
				"   <p class=\"p2\">Flat Rate of Interest<span class=\"Apple-converted-space\">&nbsp; </span>(% per annum)<span class=\"data-insert\">24</span></p>\n" + 
				"   <p class=\"p2\">Amount of EDI <span class=\"data-insert\">"+getEdiAmount(requestDto.getTenure(), requestDto.getAmount())+"</span></p>\n" + 
				"   <p class=\"p2\">BharatPe Registered Mobile Number: <span class=\"data-insert\">"+(merchant.getMobile()==null?"":merchant.getMobile())+"</span></p>\n" + 
				"   <p class=\"p2\">Location: <span class=\"data-insert\"></span>"+(creditApplicationAddress.getCity()==null?"":creditApplicationAddress.getCity())+"</p>\n" + 
				"   <p class=\"p2\">EDI Due Date &ndash; Everyday from Monday to Saturday from the successive day of disbursal</p>\n" + 
				"   <p class=\"p2\">Shop/Business Address: <span class=\"data-insert\">Shop no "+(creditApplicationAddress.getShopNumber()==null?"":(creditApplicationAddress.getShopNumber()+","))+(creditApplicationAddress.getStreetAddress()==null?"":(creditApplicationAddress.getStreetAddress()+","))+(creditApplicationAddress.getArea()==null?"":(creditApplicationAddress.getArea()+","))+(creditApplicationAddress.getCity()==null?"":(creditApplicationAddress.getCity()+","))+(creditApplicationAddress.getState()==null?"":(creditApplicationAddress.getState()+","))+(creditApplicationAddress.getPincode()==null?"":(creditApplicationAddress.getPincode()))+"</span></p>\n" + 
				"   <p class=\"p2\">Landmark: <span class=\"data-insert\">"+(creditApplicationAddress.getLandmark()==null?"":creditApplicationAddress.getLandmark())+"</span><span>&nbsp;</span> PIN: <span class=\"data-insert\">"+(creditApplicationAddress.getPincode()==null?"":creditApplicationAddress.getPincode())+"</span></p>\n" + 
				"   <p class=\"p2\">City: <span class=\"data-insert\">"+(creditApplicationAddress.getCity()==null?"":creditApplicationAddress.getCity())+"</span><span>&nbsp;</span> State: <span class=\"data-insert\">"+(creditApplicationAddress.getState()==null?"":creditApplicationAddress.getState())+"</span></p>\n" + 
				"   <p class=\"p3\">Email: <span class=\"data-insert\">"+(creditApplication.getEmail()==null?"":creditApplication.getEmail())+"</span></p>\n" + 
				"   <p class=\"p4\">&nbsp;</p>\n" + 
				"   <p class=\"p3\">Shop/ Business Phone Number: <span class=\"data-insert\">"+(creditApplication.getMobile()==null?"":creditApplication.getMobile())+"</span></p>\n" + 
				"   <p class=\"p4\">&nbsp;</p>\n" + 
				"   <p class=\"p4\">&nbsp;</p>\n" + 
				"    <p class=\"p5\"><strong>Declaration / Undertaking/Representation by Borrower</strong></p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li6\">I/We hereby apply for a finance facility as proposition made by <strong>Resilient Innovation Private Limited (&ldquo;BharatPe&rdquo;)</strong> as in terms of Loan Agreement as below and declare that all the particulars, information and details provided and other documents submitted by me/us are true, correct, complete and up-to-date in all respects and that I/We have not withheld any material information.</li>\n" + 
				"    <li class=\"li6\">I/We hereby authorize <span class=\"s2\">Lender</span>/BharatPe/Liquiloans to exchange or share information and details relating to my application to its group companies or any third party, as may be required or deemed fit, for the purpose of processing this loan application and/or related offerings or other products / services that I/We may apply for from time to time.</li>\n" + 
				"    <li class=\"li6\">By submitting this application, I/We hereby expressly authorize <span class=\"s2\">Lender</span>/BharatPe to send me communications regarding loans, insurance and other products from <span class=\"s2\">Lender</span>/BharatPe/Liquiloans, its group<span class=\"Apple-converted-space\">&nbsp; </span>companies and / or third parties through telephone calls / SMSs / emails / post etc. including but not limited to promotional, transactional communications. I/We confirm that I shall not challenge receipt of such communications by me as unsolicited communication, defined under TRAI Regulations on Unsolicited Commercial Communications.</li>\n" + 
				"    <li class=\"li6\">I authorize BharatPe / <span class=\"s2\">Lender</span>/Liquiloans to evaluate my transaction history on the BharatPe platform in order to check my eligibility for the loan and understand and acknowledge that <span class=\"s2\">Lender</span>/BharatPe has the absolute discretion, without assigning any reasons to reject my application and that <span class=\"s2\">Lender</span>/BharatPe /Liquiloans is not answerable / liable to me, in any manner whatsoever,<span class=\"Apple-converted-space\">&nbsp; </span>for<span class=\"Apple-converted-space\">&nbsp; </span>rejecting<span class=\"Apple-converted-space\">&nbsp; </span>my application.</li>\n" + 
				"    <li class=\"li6\">I / We agrees and accept that <span class=\"s2\">Lender</span>/BharatPe /Liquiloans may in its sole discretion, by its self or through authorised persons, advocate, agencies, bureau, etc. verify any information given, check credit references, employment details and obtain credit reports to determine creditworthiness from time to time.</li>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p7\">&nbsp;</p>\n" + 
				"    <p class=\"p8\" style=\"text-align: center;\"><strong>LOAN AGREEMENT</strong></p>\n" + 
				"    <p class=\"p9\">&nbsp;</p>\n" + 
				"    <p class=\"p10\">&nbsp;</p>\n" + 
				"    <p class=\"p11\">This <strong>Loan Agreement</strong> (&ldquo;<strong>Agreement</strong>&rdquo;) is made and executed at the place mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) and on the date mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) by and between:</p>\n" + 
				"    <p class=\"p11\">HINDON MERCANTILE LIMITED, a non-banking finance company, having its registered office at Unit No 307, Third Floor Plot\n" +
				"No. H-1 Garg Tower, NSP, Pitampura Delhi (hereinafter referred to as the &ldquo;<strong>Lender</strong>&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include its successor(s) and permitted assign(s)) of the One Part;</p>\n" +
				"    <p class=\"p11\"><strong>AND</strong></p>\n" + 
				"    <p class=\"p11\"><strong><em>[Details from the Schedule I]</em></strong>, hereto as the borrower and co-borrower (if any) (wherever the context so requires) (hereinafter referred to as the &ldquo;<strong>Borrower</strong>&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include his/her/their heir(s), successor(s), legal representative(s), executor(s), administrator(s) and permitted assign(s)) of the Other Part.</p>\n" + 
				"    <p class=\"p11\">The Lender and the Borrower are hereinafter collectively referred to as the &ldquo;<strong>Parties</strong>&rdquo; and each individually as the &ldquo;<strong>Party</strong>&rdquo;.</p>\n" + 
				"    <p class=\"p11\"><strong>WHEREAS</strong>:</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\">The Lender is a non-banking financing company, registered with the Reserve Bank of India, having registration no. B-14-00518],\n" +
				"and is <em>inter alia</em> engaged in the business of advancing loans and other financial facilities.</li>\n" +
				"    <li class=\"li11\">The Borrower has approached the Lender and has requested for grant of loan facility for the purpose of <strong><em>as mentioned in Schedule I </em></strong>and in reliance on the acceptance of the terms, conditions, assurances, representations and warranties of the Borrower, the Lender has agreed to grant loan facility to the Borrower, subject to the terms and conditions contained in this Agreement.</li>\n" + 
				"    <li class=\"li11\">The Parties hereto are now desirous of <em>inter alia</em> entering into this Agreement to set out the terms and conditions in relation to the Facility.</li>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p11\"><strong>Now, therefore, in view of the foregoing and in consideration of the mutual covenants and agreements herein set forth, the parties hereby agree as follows:</strong></p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li12\">DEFINITIONS AND INTERPRETATION</li>\n" + 
				"    </ul>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li13\"><strong>Definitions</strong></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p14\">&ldquo;<strong>Borrower Account</strong>&rdquo; means the following bank account of the Borrower <strong><em>as mentioned in Schedule I</em></strong>, unless otherwise notified by the Borrower in writing.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Due Date</strong>&rdquo; means the date(s) on which any amounts from the Borrower to the Lender including the principal amounts of the Facility, interest and/or any other Outstanding Amounts, fall due as per <strong>Schedule I</strong> (<em>Terms of the Facility</em>) of this Agreement or any other Facility Document, or as demanded by the Lender in accordance with a Facility Document.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Events of Default</strong>&rdquo; shall have the meaning ascribed to it under the terms herein.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Facility</strong>&rdquo; means the facility amount mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>).</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Final Settlement Date</strong>&rdquo; means the date on which all the Outstanding Amounts have been fully paid and the Facility has been irrevocably discharged to the satisfaction of the Lender.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Financing Documents</strong>&rdquo; means this Agreement and such other documents as may be executed or required to be executed between the Lender and/or the Borrower in order to perfect or validate this Agreement.</p>\n" + 
				"    <p class=\"p14\">&ldquo;<strong>Government Authority</strong>&rdquo; means any governmental department, commission, board, bureau, agency, regulatory authority, instrumentality, court or other judicial, quasi-judicial or administrative body, whether central, state, provincial or local, having jurisdiction over the subject matter or matters in question. For avoidance of doubt, it is hereby clarified that the term &ldquo;Government Authority&rdquo; does not include any bank/financial institution acting solely in its capacity as a lender to the Borrower.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Interest Rate</strong>&rdquo; means the rate of interest mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>).</p>\n" + 
				"    <p class=\"p14\">&ldquo;<strong>Laws</strong>&rdquo; means any statute, law, regulation, ordinance, rule, judgment, order, decree, bye-laws, rule of law, directives, guidelines policy, requirement, or any governmental restriction or any similar form of decision of, or determination by, or any interpretation or administration having the force of law of any of the foregoing, by any Government Authority having jurisdiction over the matter in subject, whether in effect as of the date of this Agreement or hereafter.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Loan Application</strong>&rdquo; means the application made by the Borrower in the form specified by the Lender for availing the Facility and where the context so requires, all other information, particulars submitted by the Borrower to the Lender with a view to avail the Facility.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Material Adverse Effect</strong>&rdquo; means adverse effect on: (a) the ability of the Borrower to observe and perform in a timely manner their respective obligations under any of the Financing Documents to which it is or would be a party or; (b) the legality, validity, binding nature or enforceability of any of the Financing Documents; or (d) the Business or financial condition of the Borrower which is reasonably likely to impair its ability to service the Facility as and when becoming due; or (e) the rights and remedies of the Lender under the Financing Documents.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Outstanding Amounts</strong>&rdquo; mean principal amount of the Facility outstanding from time to time, and all interests, Penal Interest, prepayment charges, costs, commissions, fees &amp; charges, expenses and other amounts due under or in respect of this Agreement.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Payment Mechanism</strong>&rdquo; means ECS, ACH, NEFT, RTGS or payment by way of cheque, as the case may be.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Person</strong>&rdquo; shall, unless specifically provided otherwise, mean any individual, corporation, partnership, association of persons, company, joint stock company, trust or Government Authority, as the context may admit.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Prepayment</strong>&rdquo; means the premature repayment of the Facility as per the terms and conditions approved by the Lender in this regard and prevailing at the time of such premature repayment by the Borrower.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Purpose</strong>&rdquo; means the purpose for which the Facility has been agreed to be utilised by the Borrower, as mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) to this Agreement.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>RBI</strong>&rdquo; means the Reserve Bank of India.</p>\n" + 
				"    <p class=\"p15\">&ldquo;<strong>Tax</strong>&rdquo; means any tax, levy, impost, duty or other charge or withholding of a similar nature (including any penalty or interest payable in connection with the failure to pay or delay in paying any of the same).</p>\n" + 
				"    <p class=\"p16\">&ldquo;<strong>Term</strong>&rdquo; or &ldquo;<strong>Tenure</strong>&rdquo; means the period as specified in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) of this Agreement, within which the Facility has to be repaid by the Borrower to the Lender along with interest, cost, expenses, fees &amp; charges and other amount as specified in this Agreement.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li5\"><strong>Principles of Interpretation</strong>: In this Agreement, unless the context otherwise requires:</li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p14\"><strong>T</strong>he headings are for convenience or reference only and shall not be used in and shall not affect the construction or interpretation of this Agreement.</p>\n" + 
				"    <p class=\"p14\"><strong>T</strong>he words &ldquo;include&rdquo; and &ldquo;including&rdquo; are to be construed without limitation.</p>\n" + 
				"    <p class=\"p14\"><strong>W</strong>ords importing a particular gender shall include all genders.</p>\n" + 
				"    <p class=\"p14\"><strong>R</strong>eferences to any law shall include references to such law as it may, after the date of this Agreement, from time to time be amended, supplemented or re-enacted.</p>\n" + 
				"    <p class=\"p14\"><strong>T</strong>he Schedule(s) annexed to this Agreement form an integral part of this Agreement and will be of full force and effect as though they were expressly set out in the body of the Agreement;</p>\n" + 
				"    <p class=\"p14\"><strong>R</strong>eference to any agreement, including this Agreement, deed, document, instrument, rule, regulation, notification, statute or the like shall mean a reference to the same as may have been duly amended, modified or replaced. For the avoidance of doubt, a document shall be construed as amended, modified or replaced only if such amendment, modification or replacement is executed in compliance with the provisions of such document(s);</p>\n" + 
				"    <p class=\"p17\"><strong>I</strong>n the event of any disagreement or dispute between the Lender and the Borrower regarding the materiality or reasonableness of any matter, the opinion of Lender as to the materiality shall be final and binding on the Borrower.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li12\">FACILITY</li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li12\"><strong>The Lender at the request of the Borrower agrees to grant to the Borrower and the Borrower agrees to borrow from the Lender, the Facility, on the basis and subject to the covenants and terms and conditions set forth herein. </strong></li>\n" + 
				"    <li class=\"li12\"><strong>If in future, the Borrower approaches the Lender for grant of an additional facility or increase in the amount of Facility, the Lender shall have the sole discretion for granting the same and the Lender can either proceed with<span class=\"Apple-converted-space\">&nbsp; </span>the execution of fresh loan agreement with the Borrower or execute a supplemental loan agreement.</strong></li>\n" + 
				"    <li class=\"li12\"><strong>Disbursement shall be made directly and only to Borrower. </strong></li>\n" + 
				"    <li class=\"li12\"><strong>The Lender shall have the right to adjust and/or set off any Outstanding Amounts or other dues against any subsequent amount of the Facility due to be disbursed by the Lender to the Borrower.</strong></li>\n" + 
				"    <li class=\"li12\"><strong>Notwithstanding anything stated herein, the continuation of the Facility shall be at sole and absolute discretion of the Lender and the Lender may at any time in its sole discretion and without assigning any reason call upon the Borrower to pay the Outstanding Balance and upon such demand by the Lender, the Borrower shall, within 48 hours of being so called upon, pay the whole of the Outstanding Balance to the Lender without any delay or demur. </strong></li>\n" + 
				"    <li class=\"li12\"><strong>The Lender may, at its discretion, maintain appropriate entries in its books of accounts in relation to the Facility and such entries shall be final and binding upon the Borrower.</strong></li>\n" + 
				"    </ul>\n" + 
				"    <li class=\"li12\">MODE OF DISBURSAL</li>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p18\"><strong>The Facility shall be made by the Lender by RTGS/NEFT to the Borrower Account and charges for the same, if any, shall be borne by the Borrower. Such charges shall be deemed to form part of the Outstanding Amounts.</strong></p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li12\">INTEREST</li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li12\"><strong>The Borrower shall pay interest on the principal amount of the Facility outstanding from time to time at the Interest Rate mentioned in </strong>Schedule I<strong> (<em>Terms of the Facility</em>) to this Agreement. </strong></li>\n" + 
				"    <li class=\"li12\"><strong>Interest on the Facility will begin to accrue in favour of the Lender as and from the date of disbursal of amount of Facility. Interest shall accrue from day to day and shall be computed on the basis of 365 days a year (irrespective of leap year) and the actual number of days elapsed. However, in the event of the Borrower intends to Prepay the Facility, Interest would be calculated up to the date of actual prepayment, subject to payment of Prepayment charges as applicable.</strong></li>\n" + 
				"    <li class=\"li12\"><strong>Without prejudice to the Lender's rights, Interest and any other Outstanding Amounts shall be charged/debited to the Borrower Account.</strong></li>\n" + 
				"    <li class=\"li12\"><strong>Lender at its sole discretion, may change in the prevailing rate of interest on the Facility, either due to change in its policies, or issuance of RBI guidelines and notifications with respect to the same or for any other reason whatsoever and in such an event the term 'Interest Rate' shall for all purposes mean the revised interest rate, which shall always be construed as agreed to be paid by the Borrower and hereby secured.</strong></li>\n" + 
				"    </ul>\n" + 
				"    <li class=\"li12\">FEES &amp; REPAYMENT</li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li12\"><strong>The Borrower shall, on or before or after the disbursement of the Facility, bear, pay and reimburse to the Lender all cost, fee, charges, including stamp duty charges, applicable on the Financing Documents and any increased costs expenses incurred and/or to be incurred by the Lender, on a full indemnity basis, in connection with the Facility.</strong></li>\n" + 
				"    <li class=\"li12\"><strong>The Borrower shall, on or before the disbursement of the Facility, pay to the Lender processing/service fee calculated at the rate provided in </strong>Schedule I<strong> (<em>Terms of the Facility</em>) to this Agreement, on the amount of the Facility sanctioned by the Lender along-with applicable GST. The processing/service fee shall be non-refundable. The Lender shall be entitled to recover the non-refundable processing fees and GST by way of deduction from Drawdown(s). </strong></li>\n" + 
				"    <li class=\"li13\">All fees and charges payable by the Borrower to the Lender under this Clause shall be reimbursed by the Borrower to the Lender within 7 (seven) days from the date of notice of demand from the Lender and shall be debited to the Borrower Account.</li>\n" + 
				"    <li class=\"li13\">The Lender have appointed Resilient Innovations Private Limited (BharatPe) having registered office at 90/20, Malviya Nagar, New Delhi 110017 as its collection agent and for such other services as agreed between the Lender and BharatPe, from time to time. All Outstanding Balance shall be payable/paid as may be directed &amp; advised by Lender/BharatPe.</li>\n" + 
				"    <li class=\"li13\">The Borrower shall repay the Facility, if not demanded earlier by Lender pursuant to a Financing Document, as stipulated in and in accordance with and subject to the terms and conditions of the Repayment Schedule set out in <strong>Schedule II </strong>(<em>Repayment Schedule</em>).</li>\n" + 
				"    <li class=\"li13\">No notice, reminder or intimation in any manner shall be given by the Lender to the Borrower regarding its obligation and responsibility to ensure prompt and regular payment of the Outstanding Amounts to the Lender on Due Dates. It shall be entirely the Borrower's responsibility to ensure prompt and regular payment of the Outstanding Amount.</li>\n" + 
				"    <li class=\"li13\">The Borrower agrees that the repayment of the amount of Facility together with interest, Penal Interest, if any, and all such other sums due and payable by the Borrower to the Lender shall be payable to the Lender Account by way of a Payment Mechanism approved by the Lender, provided that the Lender may, at its sole discretion, require the Borrower to adopt or switch to any alternative mode of payment and the Borrower shall comply with such request, without demur or delay. The Borrower undertakes to remit all Outstanding Amounts to the Lender on the respective Due Date.</li>\n" + 
				"    <li class=\"li13\">Any instruction under the Payment Mechanism which is revoked/ dishonoured shall make the Borrower liable for payment of charges as per the prevailing rules of the Lender in force from time to time, in addition to any Penal Interest that may be levied by the Lender and without prejudice to the Lender's right to take appropriate legal action against the Borrower for such revocation / dishonour.</li>\n" + 
				"    <li class=\"li13\">The Lender expressly reserves its right to call upon the Borrower to pay the whole or part of the Outstanding Amounts at any time after the date of first Drawdown in the event of a default by the Borrower under any Financing Document.</li>\n" + 
				"    <li class=\"li13\">In the event of any change in Repayment Schedule (at the request of the Borrower or due to an Event of Default), the Borrower shall be liable to pay rescheduling charges at the rate specified in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) to this Agreement. Such payment of rescheduling charges shall be in addition to any other rights and remedies available with the Lender in the Event of Default or otherwise.</li>\n" + 
				"    </ul>\n" + 
				"    <li class=\"li13\">SECURITY</li>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p17\">The Borrower hereby agrees, undertakes and confirms that it shall deliver to the Lender such security, if applicable, as may be required pursuant to <strong>Schedule I </strong>(<em>Terms of the Facility</em>) to this Agreement, as security towards the payment of the Outstanding Amounts with the Lender named as the payee therein.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li12\">PENAL INTEREST</li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li12\"><strong>Upon occurrence of any of the events mentioned in Article 13 below, the Borrower shall be liable to pay Penal Interest which shall be in addition to the Interest payable by the Borrower under Article 5.1.</strong></li>\n" + 
				"    <li class=\"li12\"><strong>The Borrower expressly agrees that the rate of Penal Interest is a fair estimate of the loss likely to be suffered by the Lender by reason of such delay/default on the part of the Borrower.</strong></li>\n" + 
				"    <li class=\"li12\"><strong>Penal Interest shall accrue from day to day and shall be computed on the basis of 365 (three hundred and sixty) days a year (irrespective of leap year).</strong></li>\n" + 
				"    <li class=\"li12\"><strong>Penal Interest shall be computed for (i) in case the Penal Interest is payable due to default/delay in any payment, then the period commencing from the Due Date of payment of the amount in default/delay up to the payment of amount in default/delay along-with Penal Interest and (ii) in case of occurrence of any other Event of Default, for the period during which the Event of Default or breach, as the case may be, persists.</strong></li>\n" + 
				"    </ul>\n" + 
				"    <li class=\"li12\">PREPAYMENT / FORECLOSURE</li>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p17\">The Borrower shall be entitled to prepay/ foreclose the Outstanding Amounts, subject to payment of prepayment charges as set out in <strong>Schedule I</strong> (<em>Terms of the Facility</em>).</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li12\">TAXES</li>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p16\">The Borrower shall make all payments to be made by it hereunder without and free from any Tax deduction and/or other deduction and/or withholding and/or statutory levies/duties/charges (&ldquo;<strong>Withholding</strong>&rdquo;), unless a Withholding is required by Law.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li12\">PURPOSE OF THE FACILITY</li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li13\">The Borrower undertakes and confirms that the entire Facility amount shall be utilized/ deployed only for the Purpose and for no other purpose that shall include without limitation to invest in share market, real estate or in any subsidiary/ associates of the Borrower.</li>\n" + 
				"    <li class=\"li13\">Any default, fraud, legal incompetence during the currency of the limits, non-compliance of agreed terms and conditions, non-submission of required papers, any other irregularities by the Borrower will enable the Lender to recall the Facility.</li>\n" + 
				"    <li class=\"li13\">The Borrower further confirms and/or undertakes that the Facility shall not be utilized for the following:</li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\">Subscription to or purchase of shares/debentures;</li>\n" + 
				"    <li class=\"li11\">Extending unsecured loans to subsidiary company/ associates or for making inter corporate deposits;</li>\n" + 
				"    <li class=\"li11\">Any speculative purposes or any anti-social purpose or any unlawful purpose.</li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <li class=\"li12\">COVENANTS</li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li13\">The Borrower agrees to promptly notify, in writing, the Lender about any litigation, arbitration, investigative, regulatory or administrative proceeding/action having a Material Adverse Effect.</li>\n" + 
				"    <li class=\"li13\">All terms and conditions of this Agreement including the Repayment Schedule in relation to the Facility shall remain same even if any amount under the Facility is being taken over by/assigned to any new lender.</li>\n" + 
				"    <li class=\"li13\">The Borrower declares that all the amounts including the amount of own contribution paid/ payable in connection with the Facility, is/ shall be through legitimate source and does not/ shall not constitute an offence of money laundering under the Prevention of Money Laundering Act, 2002.</li>\n" + 
				"    <li class=\"li13\">The Borrower shall perform, on request of the Lender, such acts as may be necessary to carry out the intent of the Financing Documents.</li>\n" + 
				"    <li class=\"li13\">The Borrower shall deliver to the Lender in form and detail, such details, information, documents etc to the satisfaction of the Lender, as may reasonably be required, within such period as required by the Lender from time to time.</li>\n" + 
				"    <li class=\"li13\">In case the Borrower is a body corporate, it shall not induct any person on the board of directors or as partners who have been identified as a wilful defaulter by the RBI. The Borrower confirms that neither it nor any member of its organisation has been declared as wilful defaulter.</li>\n" + 
				"    <li class=\"li12\"><strong>The Borrower hereby agrees, undertakes and covenants that unless the Lender otherwise agrees in writing, so long as the Facility or any part thereof is outstanding and an Event of Default has occurred and continuing, until full and final payment of all money owing hereunder, the Borrower </strong>SHALL NOT<strong>:</strong></li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\">Grant any loans; grant any credit (except in the ordinary course of business) to or for the benefit of any Person other than itself.</li>\n" + 
				"    <li class=\"li11\">Allow its principal shareholders/ directors/ promoters/ partners to withdraw monies brought in by them or withdraw the profits earned in the business/capital invested in the business.</li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <li class=\"li12\">REPRESENTATIONS AND WARRANTIES</li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\">The Borrower hereby represents and warrants to the Lender on a continuing basis that:</li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li13\"><span class=\"s1\"><strong>Confirmation of Loan Application</strong></span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p17\">The Borrower acknowledges and confirms that all the factual information provided by the Borrower to the Lender in the Loan Application or otherwise in order to avail the Facility and any prior or subsequent information or explanation given to the Lender in this regard is true and accurate in all material respects as at the date it was provided and does not omit to state a material fact necessary in order to make the statements contained therein misleading in the light of the circumstances under which such statements were or are made.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li13\"><span class=\"s1\"><strong>Compliance with Laws</strong></span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p17\">The Borrower has complied with all the applicable Laws and is not a party to any litigation, arbitration or administrative or regulatory proceedings or investigations of a material character and that the Borrower is not aware, to the best of its knowledge and belief, of any facts likely to give rise to such litigation, arbitration or administrative or regulatory proceedings or investigations or to material claims against the Borrower.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li13\"><span class=\"s1\"><strong>Litigation</strong></span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p17\">Where applicable, the Borrower shall supply to the Lender, promptly upon becoming aware of them, details of any filing by any creditor (financial creditor or operational creditor) which are made or threatened against them, in accordance with the provisions of the Insolvency and Bankruptcy Code, 2016 or any analogous laws.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li13\"><span class=\"s1\"><strong>Compliance of Know Your Customer (KYC) Policy:</strong></span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p17\">The Borrower is fully aware of the KYC Policy of the Lender and RBI and confirms that the information/clarification/documents/signage provided by it on its identity, address, authorised signatory, board resolution, PAN and all other material facts are true and correct and the transaction, etc. are <em>bonafide </em>and as per Law. The Borrower further confirms that it has disclosed all facts/information as are required to be disclosed for the adherence and compliance of the provisions related to the KYC Policy. The Lender reserve the right to recall the Facility or close the account in case the required documents are not provided by the Borrower to the Lender.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li13\"><strong>The Lender/BharatPe shall, without notice to or without any consent of the Borrower, be absolutely entitled and have full right, power and authority to make disclosure of any information relating to Borrower including personal information, details in relation to documents, Loan, defaults, security, obligations of Borrower, to the Credit Information Bureau of India (CIBIL) and/or any other governmental/regulatory/statutory or private agency/entity, credit bureau, RBI, the Lender&rsquo;s other branches/ subsidiaries / affiliates / rating agencies, service providers, other Lenders / financial institutions, any third parties, any assignees/potential assignees or transferees, who may need the information and may process the information, publish in such manner and through such medium as may be deemed necessary by the publisher/ Lender/ RBI, including publishing the name as part of willful defaulter&rsquo;s list from time to time, as also use for KYC information verification, credit risk analysis, or for other related purposes. The Borrower waives the privilege of privacy and privity of contract. </strong></li>\n" + 
				"    <li class=\"li13\"><strong>The execution and delivery of this Agreement and documents to be executed in pursuance hereof, and the performance of the Borrower's obligations hereunder and thereunder does not and will not (i) contravene any applicable Law, statute or regulation or any judgment or decree to which any of the Borrowers and/or their Assets and/or business and/or their undertaking is subject, or (ii) conflict with or result in any breach of, any of the terms of or constitute default of any covenants, conditions and stipulations under any existing agreement or contract or binding to which any of the Borrowers are a party or subject or (iii) conflict or contravene any provision of the memorandum and the articles of association and/or any constituting/governing documents of Borrowers. </strong></li>\n" + 
				"    <li class=\"li13\"><strong>The Borrower has informed the Lender about all loans/finances/advances availed by the Borrower from other banks/financial institutions/third parties up to the date of this Agreement to the Lender.</strong></li>\n" + 
				"    <li class=\"li13\"><span class=\"s1\"><strong>No</strong> <strong>default</strong></span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p16\">The Borrower and/or its group companies, affiliates have no over dues/not defaulted in repayment of any amount due and payable to any other bank/financial institutions.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li13\"><span class=\"s1\"><strong>Material Adverse Effect</strong></span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p17\">There are no facts or circumstances, conditions or occurrences, which could collectively or otherwise be expected to result in the Borrower being unable to perform their respective obligations under the Financing Documents to which they are expressed to be a party, or which could affect the legality, validity, binding nature or enforceability of this Agreement or other Financing Documents or is otherwise expected to have an Material Adverse Effect.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li12\">EVENT OF DEFAULT AND CONSEQUENCES</li>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p17\">The Borrower expressly and irrevocably hereby agrees and declares that each of the following events or events similar thereto shall constitute an &ldquo;<strong>Events of Default</strong>&rdquo;: The following events shall constitute events of default (each an &ldquo;Event of Default&rdquo;), and upon the occurrence of any of them the entire Outstanding Balance shall become immediately due and payable by the Borrower and further enable the Lender inter alia to recall the entire Outstanding Balance and/or enforce any security and transfer/sell the same and/or take, initiate and pursue any actions/proceedings as deemed necessary by the Lender for recovery of the dues, or such other action as the Lender may deem fit: (a) Failure on Borrower&rsquo;s part to perform any of the obligations or terms or conditions or covenants applicable in relation to the Loan including under this document/other documents including non-payment in full of any part of the Outstanding Balance when due or when demanded by Lender/BharatPe; (b) any misrepresentations or misstatement by the Borrower; or (c) occurrence of any circumstance or event which adversely affects Borrower&rsquo;s ability/capacity to pay/repay the Outstanding Balance or any part thereof or perform any of the obligations; (d) the event of death, insolvency, cessation, failure in business of the Borrower, or change or termination of employment/profession/business for any reason whatsoever<span class=\"s3\">. </span></p>\n" + 
				"    <p class=\"p17\">On and any time after the occurrence of Event of Default, the Lender may, without prejudice to any other rights that it may have under this Agreement or applicable Law (including right to accelerate payment obligations of the Borrower under the Financing Documents) take one or more of the following actions: (a) recall or declare the Outstanding Amounts to be forthwith due and payable, whereupon such amounts shall become forthwith due and payable without presentment, demand, protest or any other notice of any kind, all of which are hereby expressly waived, anything contained herein to the contrary notwithstanding;<strong> (b) </strong>exercise any and all rights specified in the Financing Documents including, without limitation, to enforce any security created/provided;<strong> (c) </strong>to initiate, appropriate proceedings for recovery of its dues by invoking the jurisdiction of appropriate court at its sole discretion, in addition to taking further action or actions under any other statute in force; and/or (d) exercise such other remedies as permitted or available under applicable law in the sole discretion of the Lender; and/or<strong> (e) </strong>disclose the name of the Borrower, and its promoters/directors/partners to RBI, TransUnion CIBIL and/or any other authorised agency.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li12\">SUCCESSION</li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\">In case of the death of the Borrower, where the Borrower is an individual and the Lender agrees to continue extending the Facility, the legal representative of the Borrower, with such other requirements as the Lender may deem fit.</li>\n" + 
				"    </ul>\n" + 
				"    <li class=\"li12\">MISCELLANEOUS</li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\"><span class=\"s1\">Governing Law and Jurisdiction</span></li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\">This Agreement and the rights and obligations of the Parties hereunder shall be construed in accordance with and be governed by the laws of India.</li>\n" + 
				"    <li class=\"li11\">The Parties agree that the courts in New Delhi shall have exclusive jurisdiction to settle any disputes which may arise out of or in connection with the Financing Documents.</li>\n" + 
				"    <li class=\"li11\">The Borrower irrevocably waive any objection, now or in future, to the venue of any Proceedings being the courts at New Delhi or any claim that any such Proceedings have been brought in an inconvenient forum.</li>\n" + 
				"    <li class=\"li11\">Nothing contained herein shall limit any right of the Lender to take Proceedings in any other court of competent jurisdiction, nor shall the taking of proceedings in one or more jurisdictions preclude the taking of proceedings in any other jurisdiction whether concurrently or not and the Borrower irrevocably waive any objection it may have now or in the future to the laying of the venue of any Proceedings on the grounds that such Proceedings have been brought in an inconvenient forum.</li>\n" + 
				"    </ul>\n" + 
				"    <li class=\"li11\"><span class=\"s1\">Arbitration</span></li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\">Without prejudice to the other legal remedies available to the Lender under applicable law (including under the SARFAESI Act, 2002 and Insolvency and Bankruptcy Code, 2016), any dispute arising out of or in connection with the Financing Documents shall be referred to and finally resolved by arbitration under the Arbitration and Conciliation Act, 1996 (as amended from time to time).</li>\n" + 
				"    <li class=\"li11\">The arbitration shall be referred to a sole arbitrator appointed by the Lender. The seat and venue of the arbitration shall be New Delhi. The language of the arbitration and the award of the arbitrator shall be in the English language. The award of the arbitrator shall be final and binding on the Parties and the expenses of the arbitration shall be borne in such manner as the arbitrator may determine.</li>\n" + 
				"    </ul>\n" + 
				"    <li class=\"li11\"><span class=\"s1\">Indemnity</span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p16\">Without prejudice to and in addition to other provisions contained in the Financing Documents, the Borrower hereby agrees to indemnify the Lender/BharatPe and its directors, officers, representatives and agents against any losses, liabilities, claims, damages or the like (including, without limitation, reasonable attorneys' fees and expenses) which may be sustained or incurred by any of them as a result of, or in connection with, or arising out of:</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\">the Borrower failing to comply with the provisions of any Financing Documents and applicable Laws; and / or</li>\n" + 
				"    <li class=\"li11\">the occurrence of any Event of Default; and / or</li>\n" + 
				"    <li class=\"li11\">levy by any Government Authority of any charge, Taxes or penalty in connection with regularising or perfecting any of the Financing Documents as may be required under applicable Law at any time during the currency of the Facility, or getting any of the Financing Documents admitted into evidence, or relying on any Financing Documents for proving any claim; and/or</li>\n" + 
				"    <li class=\"li11\">the exercise of any of the rights by the Lender under this Agreement and any of the Financing Documents; and/or</li>\n" + 
				"    <li class=\"li11\">any of the representations and warranties of the Borrower under the Financing Documents are found to be false or untrue or incorrect on a future date.</li>\n" + 
				"    </ul>\n" + 
				"    <li class=\"li11\"><span class=\"s1\">Confidentiality</span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p16\">Any information supplied by a Party to another Party pursuant hereto which is by its nature can reasonably be construed to be proprietary or confidential or is marked &ldquo;confidential&rdquo; (&ldquo;<strong>Confidential Information</strong>&rdquo;) shall be kept confidential by the recipient unless or until compelled to disclose the same (i) by judicial or administrative process, or (ii) by law, or unless the same (iii) is in or is a part of public domain, or (iv) is required to be furnished to the bankers or investors or potential investors in the either Party, or (v) is required to be furnished to any Government Authority having jurisdiction over the recipient, or (vi) can be shown by the receiving Party to the reasonable satisfaction of the disclosing Party to have been known to the receiving Party prior to it being disclosed by the disclosing Party to the receiving Party, or (vii) subsequently comes lawfully into the possession of the receiving Party from a third party, and in such cases the confidentiality obligations shall cease to the extent required under the foregoing circumstances.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\"><span class=\"s1\">Amendments and Waivers</span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p16\">This Agreement (including the schedules, annexure and appendices hereto) may not be amended, supplemented or modified and no other Financing Document may be amended, supplemented or modified and no term or condition thereof may be waived without the written consent of the Parties to such Financing Document.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\"><span class=\"s1\">Severability</span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p16\">Any provision of this Agreement or any other Financing Document which is prohibited or unenforceable shall be ineffective to the extent of prohibition or unenforceability but shall not invalidate the remaining provisions of this Agreement or any Financing Document.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\"><span class=\"s1\">Survival</span></li>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\">This Agreement shall be in force until all the Outstanding Amounts under this Agreement have been fully and irrevocably paid in accordance with the terms and provisions hereof.</li>\n" + 
				"    <li class=\"li11\">The obligations of the Borrower under the Financing Documents will not be affected by:</li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\">any unenforceability, illegality or invalidity of any obligation of any Person under a Financing Document; or</li>\n" + 
				"    <li class=\"li11\">the breach, frustration or non-fulfilment of any provisions of, or claim arising out of or in connection with a Financing Document.</li>\n" + 
				"    </ul>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\"><span class=\"s1\">Right of Set-off</span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p16\">In addition to any rights now or hereafter granted under Applicable Law or otherwise, and not by way of limitation of any such rights, upon the occurrence and continuation of an Event of Default, the Lender is hereby authorised by the Borrower to, from time to time, without presentment, demand, protest or other notice of any kind to the Borrower, or to any other Person, set off and/or appropriate and/or apply any and all deposits (general or special) at any time held or owing by the Lender (including, without limitation, by any branches and agencies other than the lending office of Lender) to or for the credit or the account of the Borrower against and on account of the obligations and liabilities of the Borrower to the Lender under this Agreement or under any of the other Financing Documents.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\"><span class=\"s1\">Notices</span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p16\">All notices and other communications provided at various places in this Agreement shall be in writing and (a) sent by hand delivery, or (b) prepaid registered post with acknowledgment due, or (c) by e-mail followed by prepaid registered post with acknowledgment due, at the address and/or email first above written. All such notices and communications shall be deemed to have been delivered effective: (i) if sent by email, when sent (provided the email enters the sent folder of the sender), (ii) if sent by prepaid registered post, 3 (three) Business Days after its dispatch.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\"><span class=\"s1\">Effectiveness</span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p17\">This Agreement shall become binding on the Parties hereto on and from the date hereof and shall be in force and effect till the Final Settlement Date.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\"><span class=\"s1\">Entire Agreement</span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p17\">This Agreement and other Financing Documents shall represent the entire understanding of the Parties on the subject matter hereof and shall override all the previous understanding and agreement between the Parties hereto.</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li11\"><span class=\"s1\">No Discrimination</span></li>\n" + 
				"    </ul>\n" + 
				"    </ul>\n" + 
				"    <p class=\"p17\">The Borrower shall, at all times during the term of this Agreement, ensure that no fraudulent preference is given to other lender of the Borrower, both present and future, so as to defeat Lender&rsquo;s rights, either present and future under this Agreement or to fraudulently service the dues owed to other lenders in preference to the dues owed to the Lender or to wilfully act in or consent to any third party acting in a manner as would cause a Material Adverse Effect.</p>\n" + 
				"    <p class=\"p19\">&nbsp;</p>\n" + 
				"    <p class=\"p19\">&nbsp;</p>\n" + 
				"    <p class=\"p20\">&nbsp;</p>\n" + 
				"    <p class=\"p20\">&nbsp;</p>\n" + 
				"    <p class=\"p8\" style=\"text-align: center;\"><strong>SCHEDULE I</strong></p>\n" + 
				"    <p class=\"p21\">&nbsp;</p>\n" + 
				"    <p class=\"p8\" style=\"text-align: center;\"><strong>TERMS OF THE FACILITY</strong></p>\n" + 
				"    <p class=\"p9\">&nbsp;</p>\n" + 
				"    <table class=\"t1 new-table1\" cellspacing=\"0\" cellpadding=\"0\">\n" + 
				"    <tbody>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p22\"><strong>S. NO.</strong></p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p23\"><strong>PARTICULARS</strong></p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"    <p class=\"p24\"><strong>DETAILS</strong></p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">1&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p25\">Date of Agreement</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">"+new Date()+"&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">2&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p25\">Place of Agreement</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">"+(creditApplicationAddress.getCity()==null?"":creditApplicationAddress.getCity())+"&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">3&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p25\">Loan Agreement No.</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">4&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p25\">Name of Borrower</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">"+(merchant.getBeneficiaryName()==null?(merchant.getMerchantName()==null?"":merchant.getMerchantName()):merchant.getBeneficiaryName())+"&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">5&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p25\">Address of Borrower</p>\n" + 
				"    <p class=\"p26\">&nbsp;</p>\n" + 
				"    <p class=\"p25\">Email Address of Borrower</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">"+(creditApplicationAddress.getShopNumber()==null?"":(creditApplicationAddress.getShopNumber()+","))+(creditApplicationAddress.getStreetAddress()==null?"":(creditApplicationAddress.getStreetAddress()+","))+(creditApplicationAddress.getArea()==null?"":(creditApplicationAddress.getArea()+","))+(creditApplicationAddress.getCity()==null?"":(creditApplicationAddress.getCity()+","))+(creditApplicationAddress.getState()==null?"":(creditApplicationAddress.getState()+","))+(creditApplicationAddress.getPincode()==null?"":(creditApplicationAddress.getPincode()))+"&nbsp;</p>\n" + 
				"    <p class=\"p20\">"+(creditApplication.getEmail()==null?"":creditApplication.getEmail())+"&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">6&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p25\">Borrower's constitution</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"    <p class=\"p27\">&nbsp;</p>\n" + 
				"    <p class=\"p27\">&nbsp;</p>\n" + 
				"    <p class=\"p28\">"+(creditApplication.getMerchantStoreId()==null?"Individual":"Proprietor")+"</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">7&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p25\">Purpose of the Facility/ Proposed utilization of the Facility</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">"+(creditApplication.getMerchantStoreId()==null?"For General":"Business Use")+"&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">8&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p25\">Amount of Loan</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"    <p class=\"p29\">"+requestDto.getAmount()+"&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">9&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p25\">Availability Period</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"    <p class=\"p6\">The period of days/months commencing from the date of execution of this Agreement or by such extended time as may be allowed by the Lender, available for draw down by the Borrower.</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">10&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p25\">Business of the Borrower</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">"+(merchant.getBusinessCategory()==null?"":merchant.getBusinessCategory())+"&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">11&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p25\">Penal Interest Rate</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">NIL&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">12&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p25\">Interest Rate</p>\n" + 
				"    <ul class=\"ul1\">\n" + 
				"    <li class=\"li6\">Interest chargeable (In case of Fixed/Monthly Loans)</li>\n"+
				"    </ul>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">2% per month&nbsp;</p>\n" + 
				"    </td>\n" +  
				"    </tr>\n" + 
				"    <tr>\n" +
				"    <td class=\"td1\" valign=\"middle\">13&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p25\">Non-refundable Processing Fees /</p>\n" + 
				"    <p class=\"p25\">service charge</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">Nil&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    </tbody>\n" + 
				"    </table>\n" + 
				"    <p class=\"p32\">&nbsp;</p>\n" + 
				"    <p class=\"p35\" style=\"text-align: center;\"><strong>TABLE OF CHARGES</strong></p>\n" + 
				"    <p class=\"p21\">&nbsp;</p>\n" + 
				"    <table class=\"t1 new-table2\" cellspacing=\"0\" cellpadding=\"0\">\n" + 
				"    <tbody>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p36\"><strong>Type of Charges</strong></p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p37\">&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p38\"><strong>Type of Charges</strong></p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p37\">&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p39\"><strong>Type of Charges</strong></p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p37\">&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p40\">Late payment Charges</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">NIL&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p41\">Part Prepayment Charges</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">NIL&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p42\">Title Search Report Charges (Legal Charges)</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">NIL&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p40\">Stamping Charges</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">NIL&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p41\">Processing Fee</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">NIL&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p43\">Other Charges</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">NIL&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    </tbody>\n" + 
				"    </table>\n" + 
				"    <p class=\"p32\">&nbsp;</p>\n" + 
				"    <p class=\"p32\">&nbsp;</p>\n" + 
				"    <p class=\"p8\" style=\"text-align: center;\"><strong>SCHEDULE II</strong></p>\n" + 
				"    <p class=\"p21\">&nbsp;</p>\n" + 
				"    <p class=\"p8\" style=\"text-align: center;\"><strong>REPAYMENT SCHEDULE</strong></p>\n" + 
				"    <p class=\"p9\">&nbsp;</p>\n" + 
				"    <table class=\"t1 new-table1\" cellspacing=\"0\" cellpadding=\"0\">\n" + 
				"    <tbody>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p44\"><strong>S. No</strong></p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p45\"><strong>Particulars</strong></p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p46\"><strong>Details</strong></p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">1&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p47\">Number of EDI</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">"+LoanUtil.getEdiDays(requestDto.getTenure())+"&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">2&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p47\">Date of Commencement of EDI</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">"+((new Date().getDay()!=6?new Date():DateTimeUtil.addDays(new Date(), 2)))+"&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    <tr>\n" + 
				"    <td class=\"td1\" valign=\"middle\">3&nbsp;</td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p47\">Mode of repayment</p>\n" + 
				"    </td>\n" + 
				"    <td class=\"td1\" valign=\"middle\">\n" + 
				"    <p class=\"p20\">QR Settlement&nbsp;</p>\n" + 
				"    </td>\n" + 
				"    </tr>\n" + 
				"    </tbody>\n" + 
				"    </table>\n" + 
				"    <p class=\"p32\">&nbsp;</p>\n" + 
				"    <p class=\"p32\">&nbsp;</p>\n" + 
				"    <p class=\"p20\">&nbsp;</p>";
		return html;
	}
	
	public String getTncForFlexible(Merchant merchant,CreditApplication creditApplication,TncRequestDto requestDto) {
		
		CreditApplicationAddress creditApplicationAddress=creditApplicationAddressDao.findByMerchantIdAndApplicationId(merchant.getId(), creditApplication.getId());
		if(creditApplicationAddress==null) {
			return null;
		}
		String html="  <p class=\"p1\" style=\"text-align: center;\"><span class=\"s1\"><strong>Loan Details</strong></span></p>\n" + 
				"<p class=\"p2\">Loan ID: <span class=\"data-insert\">"+(creditApplication.getExternalLoanId()==null?"":creditApplication.getExternalLoanId())+"</span></p>\n" + 
				"<p class=\"p0\">Date:  <span class=\"data-insert\">"+new Date()+"</span></p>\n" + 
				"<p class=\"p2\">Loan Amount (INR):<span class=\"Apple-converted-space\">&nbsp; </span> "+(requestDto.getAmount())+"<span class=\"data-insert\"></span></p>\n" + 
				"<p class=\"p2\">Tenure (Months):<span class=\"Apple-converted-space\">&nbsp; &nbsp; </span><span class=\"data-insert\">"+requestDto.getTenure()+"</span></p>\n" + 
				"<p class=\"p2\">Flat Rate of Interest (% per month)<span class=\"data-insert\">2</span></p>\n" + 
				"<p class=\"p2\">Flat Rate of Interest<span class=\"Apple-converted-space\">&nbsp; </span>(% per annum)<span class=\"data-insert\">24</span></p>\n" + 
				"<p class=\"p2\">BharatPe Registered Mobile Number: <span class=\"data-insert\">"+(creditApplication.getMobile()==null?"":creditApplication.getMobile())+"</span></p>\n" + 
				"<p class=\"p2\">Location: <span class=\"data-insert\"></span>"+(creditApplicationAddress.getCity()==null?"":creditApplicationAddress.getCity())+"</p>\n" + 
				"<p class=\"p2\">Shop/Business Address: <span class=\"data-insert\"></span>"+(creditApplicationAddress.getShopNumber()==null?"":(creditApplicationAddress.getShopNumber()+","))+(creditApplicationAddress.getStreetAddress()==null?"":(creditApplicationAddress.getStreetAddress()+","))+(creditApplicationAddress.getArea()==null?"":(creditApplicationAddress.getArea()+","))+(creditApplicationAddress.getCity()==null?"":(creditApplicationAddress.getCity()+","))+(creditApplicationAddress.getState()==null?"":(creditApplicationAddress.getState()+","))+(creditApplicationAddress.getPincode()==null?"":(creditApplicationAddress.getPincode()))+"</p>\n" + 
				"<p class=\"p2\">Landmark: <span class=\"data-insert\"></span>"+(creditApplicationAddress.getLandmark()==null?"":creditApplicationAddress.getLandmark())+"<span>&nbsp;</span> PIN: <span class=\"data-insert\">"+(creditApplicationAddress.getPincode()==null?"":creditApplicationAddress.getPincode())+"</span></p>\n" + 
				"<p class=\"p2\">City: <span class=\"data-insert\"></span>"+(creditApplicationAddress.getCity()==null?"":creditApplicationAddress.getCity())+"<span>&nbsp;</span> State: <span class=\"data-insert\">"+(creditApplicationAddress.getState()==null?"":creditApplicationAddress.getState())+"</span></p>\n" + 
				"<p class=\"p3\">Email: <span class=\"data-insert\">"+(creditApplication.getEmail()==null?"":creditApplication.getEmail())+"</span></p>\n" + 
				"<p class=\"p4\">&nbsp;</p>\n" + 
				"<p class=\"p3\">Shop/ Business Phone Number: <span class=\"data-insert\">"+(creditApplication.getMobile()==null?"":creditApplication.getMobile())+"</span></p>\n" + 
				"<p class=\"p4\">&nbsp;</p>\n" + 
				"<p class=\"p4\">&nbsp;</p>\n" + 
				"<p class=\"p5\"><strong>Declaration / Undertaking/Representation by Borrower</strong></p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li6\">I/We hereby apply for a finance facility as proposition made by <strong>Resilient Innovation Private Limited (&ldquo;BharatPe&rdquo;)</strong> as in terms of Loan Agreement as below and declare that all the particulars, information and details provided and other documents submitted by me/us are true, correct, complete and up-to-date in all respects and that I/We have not withheld any material information.</li>\n" + 
				"<li class=\"li6\">I/We hereby authorize <span class=\"s2\">Lender</span>/BharatPe/Liquiloans to exchange or share information and details relating to my application to its group companies or any third party, as may be required or deemed fit, for the purpose of processing this loan application and/or related offerings or other products / services that I/We may apply for from time to time.</li>\n" + 
				"<li class=\"li6\">By submitting this application, I/We hereby expressly authorize <span class=\"s2\">Lender</span>/BharatPe to send me communications regarding loans, insurance and other products from <span class=\"s2\">Lender</span>/BharatPe/Liquiloans, its group<span class=\"Apple-converted-space\">&nbsp; </span>companies and / or third parties through telephone calls / SMSs / emails / post etc. including but not limited to promotional, transactional communications. I/We confirm that I shall not challenge receipt of such communications by me as unsolicited communication, defined under TRAI Regulations on Unsolicited Commercial Communications.</li>\n" + 
				"<li class=\"li6\">I authorize BharatPe / <span class=\"s2\">Lender</span>/Liquiloans to evaluate my transaction history on the BharatPe platform in order to check my eligibility for the loan and understand and acknowledge that <span class=\"s2\">Lender</span>/BharatP/Liquiloans e has the absolute discretion, without assigning any reasons to reject my application and that <span class=\"s2\">Lender</span>/BharatPe/Liquiloans is not answerable / liable to me, in any manner whatsoever,<span class=\"Apple-converted-space\">&nbsp; </span>for<span class=\"Apple-converted-space\">&nbsp; </span>rejecting<span class=\"Apple-converted-space\">&nbsp; </span>my application.</li>\n" + 
				"<li class=\"li6\">I / We agrees and accept that <span class=\"s2\">Lender</span>/BharatPe/Liquiloans may in its sole discretion, by its self or through authorised persons, advocate, agencies, bureau, etc. verify any information given, check credit references, employment details and obtain credit reports to determine creditworthiness from time to time.</li>\n" + 
				"</ul>\n" + 
				"<p class=\"p7\">&nbsp;</p>\n" + 
				"<p class=\"p8\" style=\"text-align: center;\"><strong>LOAN AGREEMENT</strong></p>\n" + 
				"<p class=\"p9\">&nbsp;</p>\n" + 
				"<p class=\"p10\">&nbsp;</p>\n" + 
				"<p class=\"p11\">This <strong>Loan Agreement</strong> (&ldquo;<strong>Agreement</strong>&rdquo;) is made and executed at the place mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) and on the date mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) by and between:</p>\n" + 
				"<p class=\"p11\">The Lenders as arranged by NDX P2P Private Limited, a non-banking finance company, having its registered office at 012, Lachiram Plaza, C Wing, GAKV Marg, Goregaon East, Mumbai 400063 (hereinafter referred to as the &ldquo;<strong>Lender</strong>&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include its successor(s) and permitted assign(s)) of the One Part;</p>\n" + 
				"<p class=\"p11\"><strong>AND</strong></p>\n" + 
				"<p class=\"p11\"><strong><em>[Details from the Schedule I]</em></strong>, hereto as the borrower and co-borrower (if any) (wherever the context so requires) (hereinafter referred to as the &ldquo;<strong>Borrower</strong>&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include his/her/their heir(s), successor(s), legal representative(s), executor(s), administrator(s) and permitted assign(s)) of the Other Part.</p>\n" + 
				"<p class=\"p11\">The Lender and the Borrower are hereinafter collectively referred to as the &ldquo;<strong>Parties</strong>&rdquo; and each individually as the &ldquo;<strong>Party</strong>&rdquo;.</p>\n" + 
				"<p class=\"p11\"><strong>WHEREAS</strong>:</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\">The Lender is a non-banking financing company, registered with the Reserve Bank of India, having registration no. [N-13.02280], and is <em>inter alia</em> engaged in the business of advancing loans and other financial facilities.</li>\n" + 
				"<li class=\"li11\">The Borrower has approached the Lender and has requested for grant of loan facility for the purpose of <strong><em>as mentioned in Schedule I </em></strong>and in reliance on the acceptance of the terms, conditions, assurances, representations and warranties of the Borrower, the Lender has agreed to grant loan facility to the Borrower, subject to the terms and conditions contained in this Agreement.</li>\n" + 
				"<li class=\"li11\">The Parties hereto are now desirous of <em>inter alia</em> entering into this Agreement to set out the terms and conditions in relation to the Facility.</li>\n" + 
				"</ul>\n" + 
				"<p class=\"p11\"><strong>Now, therefore, in view of the foregoing and in consideration of the mutual covenants and agreements herein set forth, the parties hereby agree as follows:</strong></p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li12\">DEFINITIONS AND INTERPRETATION</li>\n" + 
				"</ul>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li13\"><strong>Definitions</strong></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p14\">&ldquo;<strong>Borrower Account</strong>&rdquo; means the following bank account of the Borrower <strong><em>as mentioned in Schedule I</em></strong>, unless otherwise notified by the Borrower in writing.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Due Date</strong>&rdquo; means the date(s) on which any amounts from the Borrower to the Lender including the principal amounts of the Facility, interest and/or any other Outstanding Amounts, fall due as per <strong>Schedule I</strong> (<em>Terms of the Facility</em>) of this Agreement or any other Facility Document, or as demanded by the Lender in accordance with a Facility Document.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Events of Default</strong>&rdquo; shall have the meaning ascribed to it under the terms herein.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Facility</strong>&rdquo; means the facility amount mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>).</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Final Settlement Date</strong>&rdquo; means the date on which all the Outstanding Amounts have been fully paid and the Facility has been irrevocably discharged to the satisfaction of the Lender.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Financing Documents</strong>&rdquo; means this Agreement and such other documents as may be executed or required to be executed between the Lender and/or the Borrower in order to perfect or validate this Agreement.</p>\n" + 
				"<p class=\"p14\">&ldquo;<strong>Government Authority</strong>&rdquo; means any governmental department, commission, board, bureau, agency, regulatory authority, instrumentality, court or other judicial, quasi-judicial or administrative body, whether central, state, provincial or local, having jurisdiction over the subject matter or matters in question. For avoidance of doubt, it is hereby clarified that the term &ldquo;Government Authority&rdquo; does not include any bank/financial institution acting solely in its capacity as a lender to the Borrower.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Interest Rate</strong>&rdquo; means the rate of interest mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>).</p>\n" + 
				"<p class=\"p14\">&ldquo;<strong>Laws</strong>&rdquo; means any statute, law, regulation, ordinance, rule, judgment, order, decree, bye-laws, rule of law, directives, guidelines policy, requirement, or any governmental restriction or any similar form of decision of, or determination by, or any interpretation or administration having the force of law of any of the foregoing, by any Government Authority having jurisdiction over the matter in subject, whether in effect as of the date of this Agreement or hereafter.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Loan Application</strong>&rdquo; means the application made by the Borrower in the form specified by the Lender for availing the Facility and where the context so requires, all other information, particulars submitted by the Borrower to the Lender with a view to avail the Facility.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Material Adverse Effect</strong>&rdquo; means adverse effect on: (a) the ability of the Borrower to observe and perform in a timely manner their respective obligations under any of the Financing Documents to which it is or would be a party or; (b) the legality, validity, binding nature or enforceability of any of the Financing Documents; or (d) the Business or financial condition of the Borrower which is reasonably likely to impair its ability to service the Facility as and when becoming due; or (e) the rights and remedies of the Lender under the Financing Documents.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Outstanding Amounts</strong>&rdquo; mean principal amount of the Facility outstanding from time to time, and all interests, Penal Interest, prepayment charges, costs, commissions, fees &amp; charges, expenses and other amounts due under or in respect of this Agreement.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Payment Mechanism</strong>&rdquo; means ECS, ACH, NEFT, RTGS or payment by way of cheque, as the case may be.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Person</strong>&rdquo; shall, unless specifically provided otherwise, mean any individual, corporation, partnership, association of persons, company, joint stock company, trust or Government Authority, as the context may admit.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Prepayment</strong>&rdquo; means the premature repayment of the Facility as per the terms and conditions approved by the Lender in this regard and prevailing at the time of such premature repayment by the Borrower.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Purpose</strong>&rdquo; means the purpose for which the Facility has been agreed to be utilised by the Borrower, as mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) to this Agreement.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>RBI</strong>&rdquo; means the Reserve Bank of India.</p>\n" + 
				"<p class=\"p15\">&ldquo;<strong>Tax</strong>&rdquo; means any tax, levy, impost, duty or other charge or withholding of a similar nature (including any penalty or interest payable in connection with the failure to pay or delay in paying any of the same).</p>\n" + 
				"<p class=\"p16\">&ldquo;<strong>Term</strong>&rdquo; or &ldquo;<strong>Tenure</strong>&rdquo; means the period as specified in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) of this Agreement, within which the Facility has to be repaid by the Borrower to the Lender along with interest, cost, expenses, fees &amp; charges and other amount as specified in this Agreement.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li5\"><strong>Principles of Interpretation</strong>: In this Agreement, unless the context otherwise requires:</li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p14\"><strong>T</strong>he headings are for convenience or reference only and shall not be used in and shall not affect the construction or interpretation of this Agreement.</p>\n" + 
				"<p class=\"p14\"><strong>T</strong>he words &ldquo;include&rdquo; and &ldquo;including&rdquo; are to be construed without limitation.</p>\n" + 
				"<p class=\"p14\"><strong>W</strong>ords importing a particular gender shall include all genders.</p>\n" + 
				"<p class=\"p14\"><strong>R</strong>eferences to any law shall include references to such law as it may, after the date of this Agreement, from time to time be amended, supplemented or re-enacted.</p>\n" + 
				"<p class=\"p14\"><strong>T</strong>he Schedule(s) annexed to this Agreement form an integral part of this Agreement and will be of full force and effect as though they were expressly set out in the body of the Agreement;</p>\n" + 
				"<p class=\"p14\"><strong>R</strong>eference to any agreement, including this Agreement, deed, document, instrument, rule, regulation, notification, statute or the like shall mean a reference to the same as may have been duly amended, modified or replaced. For the avoidance of doubt, a document shall be construed as amended, modified or replaced only if such amendment, modification or replacement is executed in compliance with the provisions of such document(s);</p>\n" + 
				"<p class=\"p17\"><strong>I</strong>n the event of any disagreement or dispute between the Lender and the Borrower regarding the materiality or reasonableness of any matter, the opinion of Lender as to the materiality shall be final and binding on the Borrower.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li12\">FACILITY</li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li12\"><strong>The Lender at the request of the Borrower agrees to grant to the Borrower and the Borrower agrees to borrow from the Lender, the Facility, on the basis and subject to the covenants and terms and conditions set forth herein. </strong></li>\n" + 
				"<li class=\"li12\"><strong>If in future, the Borrower approaches the Lender for grant of an additional facility or increase in the amount of Facility, the Lender shall have the sole discretion for granting the same and the Lender can either proceed with<span class=\"Apple-converted-space\">&nbsp; </span>the execution of fresh loan agreement with the Borrower or execute a supplemental loan agreement.</strong></li>\n" + 
				"<li class=\"li12\"><strong>Disbursement shall be made directly and only to Borrower. </strong></li>\n" + 
				"<li class=\"li12\"><strong>The Lender shall have the right to adjust and/or set off any Outstanding Amounts or other dues against any subsequent amount of the Facility due to be disbursed by the Lender to the Borrower.</strong></li>\n" + 
				"<li class=\"li12\"><strong>Notwithstanding anything stated herein, the continuation of the Facility shall be at sole and absolute discretion of the Lender and the Lender may at any time in its sole discretion and without assigning any reason call upon the Borrower to pay the Outstanding Balance and upon such demand by the Lender, the Borrower shall, within 48 hours of being so called upon, pay the whole of the Outstanding Balance to the Lender without any delay or demur. </strong></li>\n" + 
				"<li class=\"li12\"><strong>The Lender may, at its discretion, maintain appropriate entries in its books of accounts in relation to the Facility and such entries shall be final and binding upon the Borrower.</strong></li>\n" + 
				"</ul>\n" + 
				"<li class=\"li12\">MODE OF DISBURSAL</li>\n" + 
				"</ul>\n" + 
				"<p class=\"p18\"><strong>The Facility shall be made by the Lender by RTGS/NEFT to the Borrower Account and charges for the same, if any, shall be borne by the Borrower. Such charges shall be deemed to form part of the Outstanding Amounts.</strong></p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li12\">INTEREST</li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li12\"><strong>The Borrower shall pay interest on the principal amount of the Facility outstanding from time to time at the Interest Rate mentioned in </strong>Schedule I<strong> (<em>Terms of the Facility</em>) to this Agreement. </strong></li>\n" + 
				"<li class=\"li12\"><strong>Interest on the Facility will begin to accrue in favour of the Lender as and from the date of disbursal of amount of Facility. Interest shall accrue from day to day and shall be computed on the basis of 365 days a year (irrespective of leap year) and the actual number of days elapsed. However, in the event of the Borrower intends to Prepay the Facility, Interest would be calculated up to the date of actual prepayment, subject to payment of Prepayment charges as applicable.</strong></li>\n" + 
				"<li class=\"li12\"><strong>Without prejudice to the Lender's rights, Interest and any other Outstanding Amounts shall be charged/debited to the Borrower Account.</strong></li>\n" + 
				"<li class=\"li12\"><strong>Lender at its sole discretion, may change in the prevailing rate of interest on the Facility, either due to change in its policies, or issuance of RBI guidelines and notifications with respect to the same or for any other reason whatsoever and in such an event the term 'Interest Rate' shall for all purposes mean the revised interest rate, which shall always be construed as agreed to be paid by the Borrower and hereby secured.</strong></li>\n" + 
				"</ul>\n" + 
				"<li class=\"li12\">FEES &amp; REPAYMENT</li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li12\"><strong>The Borrower shall, on or before or after the disbursement of the Facility, bear, pay and reimburse to the Lender all cost, fee, charges, including stamp duty charges, applicable on the Financing Documents and any increased costs expenses incurred and/or to be incurred by the Lender, on a full indemnity basis, in connection with the Facility.</strong></li>\n" + 
				"<li class=\"li12\"><strong>The Borrower shall, on or before the disbursement of the Facility, pay to the Lender processing/service fee calculated at the rate provided in </strong>Schedule I<strong> (<em>Terms of the Facility</em>) to this Agreement, on the amount of the Facility sanctioned by the Lender along-with applicable GST. The processing/service fee shall be non-refundable. The Lender shall be entitled to recover the non-refundable processing fees and GST by way of deduction from Drawdown(s). </strong></li>\n" + 
				"<li class=\"li13\">All fees and charges payable by the Borrower to the Lender under this Clause shall be reimbursed by the Borrower to the Lender within 7 (seven) days from the date of notice of demand from the Lender and shall be debited to the Borrower Account.</li>\n" + 
				"<li class=\"li13\">The Lender have appointed Resilient Innovations Private Limited (BharatPe) having registered office at 90/20, Malviya Nagar, New Delhi 110017 as its collection agent and for such other services as agreed between the Lender and BharatPe, from time to time. All Outstanding Balance shall be payable/paid as may be directed &amp; advised by Lender/BharatPe.</li>\n" + 
				"<li class=\"li13\">The Borrower shall repay the Facility, if not demanded earlier by Lender pursuant to a Financing Document, as stipulated in and in accordance with and subject to the terms and conditions of the Repayment Schedule set out in <strong>Schedule II </strong>(<em>Repayment Schedule</em>).</li>\n" + 
				"<li class=\"li13\">No notice, reminder or intimation in any manner shall be given by the Lender to the Borrower regarding its obligation and responsibility to ensure prompt and regular payment of the Outstanding Amounts to the Lender on Due Dates. It shall be entirely the Borrower's responsibility to ensure prompt and regular payment of the Outstanding Amount.</li>\n" + 
				"<li class=\"li13\">The Borrower agrees that the repayment of the amount of Facility together with interest, Penal Interest, if any, and all such other sums due and payable by the Borrower to the Lender shall be payable to the Lender Account by way of a Payment Mechanism approved by the Lender, provided that the Lender may, at its sole discretion, require the Borrower to adopt or switch to any alternative mode of payment and the Borrower shall comply with such request, without demur or delay. The Borrower undertakes to remit all Outstanding Amounts to the Lender on the respective Due Date.</li>\n" + 
				"<li class=\"li13\">Any instruction under the Payment Mechanism which is revoked/ dishonoured shall make the Borrower liable for payment of charges as per the prevailing rules of the Lender in force from time to time, in addition to any Penal Interest that may be levied by the Lender and without prejudice to the Lender's right to take appropriate legal action against the Borrower for such revocation / dishonour.</li>\n" + 
				"<li class=\"li13\">The Lender expressly reserves its right to call upon the Borrower to pay the whole or part of the Outstanding Amounts at any time after the date of first Drawdown in the event of a default by the Borrower under any Financing Document.</li>\n" + 
				"<li class=\"li13\">In the event of any change in Repayment Schedule (at the request of the Borrower or due to an Event of Default), the Borrower shall be liable to pay rescheduling charges at the rate specified in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) to this Agreement. Such payment of rescheduling charges shall be in addition to any other rights and remedies available with the Lender in the Event of Default or otherwise.</li>\n" + 
				"</ul>\n" + 
				"<li class=\"li13\">SECURITY</li>\n" + 
				"</ul>\n" + 
				"<p class=\"p17\">The Borrower hereby agrees, undertakes and confirms that it shall deliver to the Lender such security, if applicable, as may be required pursuant to <strong>Schedule I </strong>(<em>Terms of the Facility</em>) to this Agreement, as security towards the payment of the Outstanding Amounts with the Lender named as the payee therein.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li12\">PENAL INTEREST</li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li12\"><strong>Upon occurrence of any of the events mentioned in Article 13 below, the Borrower shall be liable to pay Penal Interest which shall be in addition to the Interest payable by the Borrower under Article 5.1.</strong></li>\n" + 
				"<li class=\"li12\"><strong>The Borrower expressly agrees that the rate of Penal Interest is a fair estimate of the loss likely to be suffered by the Lender by reason of such delay/default on the part of the Borrower.</strong></li>\n" + 
				"<li class=\"li12\"><strong>Penal Interest shall accrue from day to day and shall be computed on the basis of 365 (three hundred and sixty) days a year (irrespective of leap year).</strong></li>\n" + 
				"<li class=\"li12\"><strong>Penal Interest shall be computed for (i) in case the Penal Interest is payable due to default/delay in any payment, then the period commencing from the Due Date of payment of the amount in default/delay up to the payment of amount in default/delay along-with Penal Interest and (ii) in case of occurrence of any other Event of Default, for the period during which the Event of Default or breach, as the case may be, persists.</strong></li>\n" + 
				"</ul>\n" + 
				"<li class=\"li12\">PREPAYMENT / FORECLOSURE</li>\n" + 
				"</ul>\n" + 
				"<p class=\"p17\">The Borrower shall be entitled to prepay/ foreclose the Outstanding Amounts, subject to payment of prepayment charges as set out in <strong>Schedule I</strong> (<em>Terms of the Facility</em>).</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li12\">TAXES</li>\n" + 
				"</ul>\n" + 
				"<p class=\"p16\">The Borrower shall make all payments to be made by it hereunder without and free from any Tax deduction and/or other deduction and/or withholding and/or statutory levies/duties/charges (&ldquo;<strong>Withholding</strong>&rdquo;), unless a Withholding is required by Law.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li12\">PURPOSE OF THE FACILITY</li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li13\">The Borrower undertakes and confirms that the entire Facility amount shall be utilized/ deployed only for the Purpose and for no other purpose that shall include without limitation to invest in share market, real estate or in any subsidiary/ associates of the Borrower.</li>\n" + 
				"<li class=\"li13\">Any default, fraud, legal incompetence during the currency of the limits, non-compliance of agreed terms and conditions, non-submission of required papers, any other irregularities by the Borrower will enable the Lender to recall the Facility.</li>\n" + 
				"<li class=\"li13\">The Borrower further confirms and/or undertakes that the Facility shall not be utilized for the following:</li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\">Subscription to or purchase of shares/debentures;</li>\n" + 
				"<li class=\"li11\">Extending unsecured loans to subsidiary company/ associates or for making inter corporate deposits;</li>\n" + 
				"<li class=\"li11\">Any speculative purposes or any anti-social purpose or any unlawful purpose.</li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<li class=\"li12\">COVENANTS</li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li13\">The Borrower agrees to promptly notify, in writing, the Lender about any litigation, arbitration, investigative, regulatory or administrative proceeding/action having a Material Adverse Effect.</li>\n" + 
				"<li class=\"li13\">All terms and conditions of this Agreement including the Repayment Schedule in relation to the Facility shall remain same even if any amount under the Facility is being taken over by/assigned to any new lender.</li>\n" + 
				"<li class=\"li13\">The Borrower declares that all the amounts including the amount of own contribution paid/ payable in connection with the Facility, is/ shall be through legitimate source and does not/ shall not constitute an offence of money laundering under the Prevention of Money Laundering Act, 2002.</li>\n" + 
				"<li class=\"li13\">The Borrower shall perform, on request of the Lender, such acts as may be necessary to carry out the intent of the Financing Documents.</li>\n" + 
				"<li class=\"li13\">The Borrower shall deliver to the Lender in form and detail, such details, information, documents etc to the satisfaction of the Lender, as may reasonably be required, within such period as required by the Lender from time to time.</li>\n" + 
				"<li class=\"li13\">In case the Borrower is a body corporate, it shall not induct any person on the board of directors or as partners who have been identified as a wilful defaulter by the RBI. The Borrower confirms that neither it nor any member of its organisation has been declared as wilful defaulter.</li>\n" + 
				"<li class=\"li12\"><strong>The Borrower hereby agrees, undertakes and covenants that unless the Lender otherwise agrees in writing, so long as the Facility or any part thereof is outstanding and an Event of Default has occurred and continuing, until full and final payment of all money owing hereunder, the Borrower </strong>SHALL NOT<strong>:</strong></li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\">Grant any loans; grant any credit (except in the ordinary course of business) to or for the benefit of any Person other than itself.</li>\n" + 
				"<li class=\"li11\">Allow its principal shareholders/ directors/ promoters/ partners to withdraw monies brought in by them or withdraw the profits earned in the business/capital invested in the business.</li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<li class=\"li12\">REPRESENTATIONS AND WARRANTIES</li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\">The Borrower hereby represents and warrants to the Lender on a continuing basis that:</li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li13\"><span class=\"s1\"><strong>Confirmation of Loan Application</strong></span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p17\">The Borrower acknowledges and confirms that all the factual information provided by the Borrower to the Lender in the Loan Application or otherwise in order to avail the Facility and any prior or subsequent information or explanation given to the Lender in this regard is true and accurate in all material respects as at the date it was provided and does not omit to state a material fact necessary in order to make the statements contained therein misleading in the light of the circumstances under which such statements were or are made.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li13\"><span class=\"s1\"><strong>Compliance with Laws</strong></span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p17\">The Borrower has complied with all the applicable Laws and is not a party to any litigation, arbitration or administrative or regulatory proceedings or investigations of a material character and that the Borrower is not aware, to the best of its knowledge and belief, of any facts likely to give rise to such litigation, arbitration or administrative or regulatory proceedings or investigations or to material claims against the Borrower.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li13\"><span class=\"s1\"><strong>Litigation</strong></span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p17\">Where applicable, the Borrower shall supply to the Lender, promptly upon becoming aware of them, details of any filing by any creditor (financial creditor or operational creditor) which are made or threatened against them, in accordance with the provisions of the Insolvency and Bankruptcy Code, 2016 or any analogous laws.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li13\"><span class=\"s1\"><strong>Compliance of Know Your Customer (KYC) Policy:</strong></span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p17\">The Borrower is fully aware of the KYC Policy of the Lender and RBI and confirms that the information/clarification/documents/signage provided by it on its identity, address, authorised signatory, board resolution, PAN and all other material facts are true and correct and the transaction, etc. are <em>bonafide </em>and as per Law. The Borrower further confirms that it has disclosed all facts/information as are required to be disclosed for the adherence and compliance of the provisions related to the KYC Policy. The Lender reserve the right to recall the Facility or close the account in case the required documents are not provided by the Borrower to the Lender.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li13\"><strong>The Lender/BharatPe shall, without notice to or without any consent of the Borrower, be absolutely entitled and have full right, power and authority to make disclosure of any information relating to Borrower including personal information, details in relation to documents, Loan, defaults, security, obligations of Borrower, to the Credit Information Bureau of India (CIBIL) and/or any other governmental/regulatory/statutory or private agency/entity, credit bureau, RBI, the Lender&rsquo;s other branches/ subsidiaries / affiliates / rating agencies, service providers, other Lenders / financial institutions, any third parties, any assignees/potential assignees or transferees, who may need the information and may process the information, publish in such manner and through such medium as may be deemed necessary by the publisher/ Lender/ RBI, including publishing the name as part of willful defaulter&rsquo;s list from time to time, as also use for KYC information verification, credit risk analysis, or for other related purposes. The Borrower waives the privilege of privacy and privity of contract. </strong></li>\n" + 
				"<li class=\"li13\"><strong>The execution and delivery of this Agreement and documents to be executed in pursuance hereof, and the performance of the Borrower's obligations hereunder and thereunder does not and will not (i) contravene any applicable Law, statute or regulation or any judgment or decree to which any of the Borrowers and/or their Assets and/or business and/or their undertaking is subject, or (ii) conflict with or result in any breach of, any of the terms of or constitute default of any covenants, conditions and stipulations under any existing agreement or contract or binding to which any of the Borrowers are a party or subject or (iii) conflict or contravene any provision of the memorandum and the articles of association and/or any constituting/governing documents of Borrowers. </strong></li>\n" + 
				"<li class=\"li13\"><strong>The Borrower has informed the Lender about all loans/finances/advances availed by the Borrower from other banks/financial institutions/third parties up to the date of this Agreement to the Lender.</strong></li>\n" + 
				"<li class=\"li13\"><span class=\"s1\"><strong>No</strong> <strong>default</strong></span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p16\">The Borrower and/or its group companies, affiliates have no over dues/not defaulted in repayment of any amount due and payable to any other bank/financial institutions.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li13\"><span class=\"s1\"><strong>Material Adverse Effect</strong></span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p17\">There are no facts or circumstances, conditions or occurrences, which could collectively or otherwise be expected to result in the Borrower being unable to perform their respective obligations under the Financing Documents to which they are expressed to be a party, or which could affect the legality, validity, binding nature or enforceability of this Agreement or other Financing Documents or is otherwise expected to have an Material Adverse Effect.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li12\">EVENT OF DEFAULT AND CONSEQUENCES</li>\n" + 
				"</ul>\n" + 
				"<p class=\"p17\">The Borrower expressly and irrevocably hereby agrees and declares that each of the following events or events similar thereto shall constitute an &ldquo;<strong>Events of Default</strong>&rdquo;: The following events shall constitute events of default (each an &ldquo;Event of Default&rdquo;), and upon the occurrence of any of them the entire Outstanding Balance shall become immediately due and payable by the Borrower and further enable the Lender inter alia to recall the entire Outstanding Balance and/or enforce any security and transfer/sell the same and/or take, initiate and pursue any actions/proceedings as deemed necessary by the Lender for recovery of the dues, or such other action as the Lender may deem fit: (a) Failure on Borrower&rsquo;s part to perform any of the obligations or terms or conditions or covenants applicable in relation to the Loan including under this document/other documents including non-payment in full of any part of the Outstanding Balance when due or when demanded by Lender/BharatPe; (b) any misrepresentations or misstatement by the Borrower; or (c) occurrence of any circumstance or event which adversely affects Borrower&rsquo;s ability/capacity to pay/repay the Outstanding Balance or any part thereof or perform any of the obligations; (d) the event of death, insolvency, cessation, failure in business of the Borrower, or change or termination of employment/profession/business for any reason whatsoever<span class=\"s3\">. </span></p>\n" + 
				"<p class=\"p17\">On and any time after the occurrence of Event of Default, the Lender may, without prejudice to any other rights that it may have under this Agreement or applicable Law (including right to accelerate payment obligations of the Borrower under the Financing Documents) take one or more of the following actions: (a) recall or declare the Outstanding Amounts to be forthwith due and payable, whereupon such amounts shall become forthwith due and payable without presentment, demand, protest or any other notice of any kind, all of which are hereby expressly waived, anything contained herein to the contrary notwithstanding;<strong> (b) </strong>exercise any and all rights specified in the Financing Documents including, without limitation, to enforce any security created/provided;<strong> (c) </strong>to initiate, appropriate proceedings for recovery of its dues by invoking the jurisdiction of appropriate court at its sole discretion, in addition to taking further action or actions under any other statute in force; and/or (d) exercise such other remedies as permitted or available under applicable law in the sole discretion of the Lender; and/or<strong> (e) </strong>disclose the name of the Borrower, and its promoters/directors/partners to RBI, TransUnion CIBIL and/or any other authorised agency.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li12\">SUCCESSION</li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\">In case of the death of the Borrower, where the Borrower is an individual and the Lender agrees to continue extending the Facility, the legal representative of the Borrower, with such other requirements as the Lender may deem fit.</li>\n" + 
				"</ul>\n" + 
				"<li class=\"li12\">MISCELLANEOUS</li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\"><span class=\"s1\">Governing Law and Jurisdiction</span></li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\">This Agreement and the rights and obligations of the Parties hereunder shall be construed in accordance with and be governed by the laws of India.</li>\n" + 
				"<li class=\"li11\">The Parties agree that the courts in New Delhi shall have exclusive jurisdiction to settle any disputes which may arise out of or in connection with the Financing Documents.</li>\n" + 
				"<li class=\"li11\">The Borrower irrevocably waive any objection, now or in future, to the venue of any Proceedings being the courts at New Delhi or any claim that any such Proceedings have been brought in an inconvenient forum.</li>\n" + 
				"<li class=\"li11\">Nothing contained herein shall limit any right of the Lender to take Proceedings in any other court of competent jurisdiction, nor shall the taking of proceedings in one or more jurisdictions preclude the taking of proceedings in any other jurisdiction whether concurrently or not and the Borrower irrevocably waive any objection it may have now or in the future to the laying of the venue of any Proceedings on the grounds that such Proceedings have been brought in an inconvenient forum.</li>\n" + 
				"</ul>\n" + 
				"<li class=\"li11\"><span class=\"s1\">Arbitration</span></li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\">Without prejudice to the other legal remedies available to the Lender under applicable law (including under the SARFAESI Act, 2002 and Insolvency and Bankruptcy Code, 2016), any dispute arising out of or in connection with the Financing Documents shall be referred to and finally resolved by arbitration under the Arbitration and Conciliation Act, 1996 (as amended from time to time).</li>\n" + 
				"<li class=\"li11\">The arbitration shall be referred to a sole arbitrator appointed by the Lender. The seat and venue of the arbitration shall be New Delhi. The language of the arbitration and the award of the arbitrator shall be in the English language. The award of the arbitrator shall be final and binding on the Parties and the expenses of the arbitration shall be borne in such manner as the arbitrator may determine.</li>\n" + 
				"</ul>\n" + 
				"<li class=\"li11\"><span class=\"s1\">Indemnity</span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p16\">Without prejudice to and in addition to other provisions contained in the Financing Documents, the Borrower hereby agrees to indemnify the Lender/BharatPe and its directors, officers, representatives and agents against any losses, liabilities, claims, damages or the like (including, without limitation, reasonable attorneys' fees and expenses) which may be sustained or incurred by any of them as a result of, or in connection with, or arising out of:</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\">the Borrower failing to comply with the provisions of any Financing Documents and applicable Laws; and / or</li>\n" + 
				"<li class=\"li11\">the occurrence of any Event of Default; and / or</li>\n" + 
				"<li class=\"li11\">levy by any Government Authority of any charge, Taxes or penalty in connection with regularising or perfecting any of the Financing Documents as may be required under applicable Law at any time during the currency of the Facility, or getting any of the Financing Documents admitted into evidence, or relying on any Financing Documents for proving any claim; and/or</li>\n" + 
				"<li class=\"li11\">the exercise of any of the rights by the Lender under this Agreement and any of the Financing Documents; and/or</li>\n" + 
				"<li class=\"li11\">any of the representations and warranties of the Borrower under the Financing Documents are found to be false or untrue or incorrect on a future date.</li>\n" + 
				"</ul>\n" + 
				"<li class=\"li11\"><span class=\"s1\">Confidentiality</span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p16\">Any information supplied by a Party to another Party pursuant hereto which is by its nature can reasonably be construed to be proprietary or confidential or is marked &ldquo;confidential&rdquo; (&ldquo;<strong>Confidential Information</strong>&rdquo;) shall be kept confidential by the recipient unless or until compelled to disclose the same (i) by judicial or administrative process, or (ii) by law, or unless the same (iii) is in or is a part of public domain, or (iv) is required to be furnished to the bankers or investors or potential investors in the either Party, or (v) is required to be furnished to any Government Authority having jurisdiction over the recipient, or (vi) can be shown by the receiving Party to the reasonable satisfaction of the disclosing Party to have been known to the receiving Party prior to it being disclosed by the disclosing Party to the receiving Party, or (vii) subsequently comes lawfully into the possession of the receiving Party from a third party, and in such cases the confidentiality obligations shall cease to the extent required under the foregoing circumstances.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\"><span class=\"s1\">Amendments and Waivers</span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p16\">This Agreement (including the schedules, annexure and appendices hereto) may not be amended, supplemented or modified and no other Financing Document may be amended, supplemented or modified and no term or condition thereof may be waived without the written consent of the Parties to such Financing Document.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\"><span class=\"s1\">Severability</span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p16\">Any provision of this Agreement or any other Financing Document which is prohibited or unenforceable shall be ineffective to the extent of prohibition or unenforceability but shall not invalidate the remaining provisions of this Agreement or any Financing Document.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\"><span class=\"s1\">Survival</span></li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\">This Agreement shall be in force until all the Outstanding Amounts under this Agreement have been fully and irrevocably paid in accordance with the terms and provisions hereof.</li>\n" + 
				"<li class=\"li11\">The obligations of the Borrower under the Financing Documents will not be affected by:</li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\">any unenforceability, illegality or invalidity of any obligation of any Person under a Financing Document; or</li>\n" + 
				"<li class=\"li11\">the breach, frustration or non-fulfilment of any provisions of, or claim arising out of or in connection with a Financing Document.</li>\n" + 
				"</ul>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\"><span class=\"s1\">Right of Set-off</span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p16\">In addition to any rights now or hereafter granted under Applicable Law or otherwise, and not by way of limitation of any such rights, upon the occurrence and continuation of an Event of Default, the Lender is hereby authorised by the Borrower to, from time to time, without presentment, demand, protest or other notice of any kind to the Borrower, or to any other Person, set off and/or appropriate and/or apply any and all deposits (general or special) at any time held or owing by the Lender (including, without limitation, by any branches and agencies other than the lending office of Lender) to or for the credit or the account of the Borrower against and on account of the obligations and liabilities of the Borrower to the Lender under this Agreement or under any of the other Financing Documents.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\"><span class=\"s1\">Notices</span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p16\">All notices and other communications provided at various places in this Agreement shall be in writing and (a) sent by hand delivery, or (b) prepaid registered post with acknowledgment due, or (c) by e-mail followed by prepaid registered post with acknowledgment due, at the address and/or email first above written. All such notices and communications shall be deemed to have been delivered effective: (i) if sent by email, when sent (provided the email enters the sent folder of the sender), (ii) if sent by prepaid registered post, 3 (three) Business Days after its dispatch.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\"><span class=\"s1\">Effectiveness</span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p17\">This Agreement shall become binding on the Parties hereto on and from the date hereof and shall be in force and effect till the Final Settlement Date.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\"><span class=\"s1\">Entire Agreement</span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p17\">This Agreement and other Financing Documents shall represent the entire understanding of the Parties on the subject matter hereof and shall override all the previous understanding and agreement between the Parties hereto.</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li11\"><span class=\"s1\">No Discrimination</span></li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p17\">The Borrower shall, at all times during the term of this Agreement, ensure that no fraudulent preference is given to other lender of the Borrower, both present and future, so as to defeat Lender&rsquo;s rights, either present and future under this Agreement or to fraudulently service the dues owed to other lenders in preference to the dues owed to the Lender or to wilfully act in or consent to any third party acting in a manner as would cause a Material Adverse Effect.</p>\n" + 
				"<p class=\"p19\">&nbsp;</p>\n" + 
				"<p class=\"p19\">&nbsp;</p>\n" + 
				"<p class=\"p20\">&nbsp;</p>\n" + 
				"<p class=\"p20\">&nbsp;</p>\n" + 
				"<p class=\"p8\" style=\"text-align: center;\"><strong>SCHEDULE I</strong></p>\n" + 
				"<p class=\"p21\">&nbsp;</p>\n" + 
				"<p class=\"p8\" style=\"text-align: center;\"><strong>TERMS OF THE FACILITY</strong></p>\n" + 
				"<p class=\"p9\">&nbsp;</p>\n" + 
				"<table class=\"t1 new-table1\" cellspacing=\"0\" cellpadding=\"0\">\n" + 
				"    <style>\n" + 
				"        .new-table1 td{\n" + 
				"            border: 1px solid #000;\n" + 
				"            width: 33%;\n" + 
				"            text-align: center;\n" + 
				"        }\n" + 
				"    </style>\n" + 
				"<tbody>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p22\"><strong>S. NO.</strong></p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p23\"><strong>PARTICULARS</strong></p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"<p class=\"p24\"><strong>DETAILS</strong></p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">1&nbsp;</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p25\">Date of Agreement</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"<p class=\"p20\">"+new Date()+"&nbsp;</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">2&nbsp;</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p25\">Place of Agreement</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"<p class=\"p20\">"+(creditApplicationAddress.getCity()==null?"":creditApplicationAddress.getCity())+"&nbsp;</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">3&nbsp;</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p25\">Loan Agreement No.</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"<p class=\"p20\">&nbsp;</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">4&nbsp;</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p25\">Name of Borrower</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"<p class=\"p20\">"+(merchant.getBeneficiaryName()==null?(merchant.getMerchantName()==null?"":merchant.getMerchantName()):merchant.getBeneficiaryName())+"&nbsp;</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">5&nbsp;</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p25\">Address of Borrower</p>\n" + 
				"<p class=\"p26\">&nbsp;</p>\n" + 
				"<p class=\"p25\">Email Address of Borrower</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"<p class=\"p20\">"+creditApplicationAddress.getShopNumber()+","+creditApplicationAddress.getStreetAddress()+","+creditApplicationAddress.getArea()+"&nbsp;</p>\n" + 
				"<p class=\"p20\">"+(creditApplication.getEmail()==null?"":creditApplication.getEmail())+"&nbsp;</p>\n" +
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">6&nbsp;</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p25\">Borrower's constitution</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"<p class=\"p27\">&nbsp;</p>\n" + 
				"<p class=\"p27\">&nbsp;</p>\n" + 
				"<p class=\"p28\">"+(creditApplication.getMerchantStoreId()==null?"Individual":"Propreitor")+"</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">7&nbsp;</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p25\">Purpose of the Facility/ Proposed utilization of the Facility</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"<p class=\"p20\">"+(creditApplication.getMerchantStoreId()==null?"For General":"Business Use")+"&nbsp;</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">8&nbsp;</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p25\">Amount of Loan</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"<p class=\"p29\">"+requestDto.getAmount()+"&nbsp;</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">9&nbsp;</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p25\">Availability Period</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"<p class=\"p6\">The period of 12 months commencing from the date of execution of this Agreement or by such extended time as may be allowed by the Lender, available for draw down by the Borrower.</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">10&nbsp;</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p25\">Business of the Borrower</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"<p class=\"p20\">"+(merchant.getBusinessCategory()==null?"":merchant.getBusinessCategory())+"&nbsp;</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">11&nbsp;</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p25\">Penal Interest Rate</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"<p class=\"p20\">0.15% per day if used as Flexible / monthly repayment option.\n" + 
				"This can change in future post as per BharatPe Policy&nbsp;</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">12&nbsp;</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p25\">Interest Rate</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li6\">Interest chargeable (In case of Floating Rate Loans)</li>\n" + 
				"<li class=\"li6\">Interest chargeable (In case of Fixed Rate Loans)</li>\n" + 
				"</ul>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li6\">2% per month</li>\n" + 
				"<li class=\"li6\">0.1% per day</li>\n" + 
				"</ul>\n" +
				"</td>\n" +
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">13&nbsp;</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p25\">Non-refundable Processing Fees /</p>\n" + 
				"<p class=\"p25\">service charge</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" + 
				"<p class=\"p20\">Nil&nbsp;</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"</tbody>\n" + 
				"</table>\n" + 
				"<p class=\"p32\">&nbsp;</p>\n" + 
				"<p class=\"p35\" style=\"text-align: center;\"><strong>TABLE OF CHARGES</strong></p>\n" + 
				"<p class=\"p21\">&nbsp;</p>\n" + 
				"<table class=\"t1 new-table2\" cellspacing=\"0\" cellpadding=\"0\">\n" + 
				"    <style>\n" + 
				"        .new-table2 td{\n" + 
				"            border: 1px solid #000;\n" + 
				"            width: 16.6%;\n" + 
				"            text-align: center;\n" + 
				"        }\n" + 
				"    </style>\n" + 
				"<tbody>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p36\"><strong>Type of Charges</strong></p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p37\">&nbsp;</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p38\"><strong>Type of Charges</strong></p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p37\">&nbsp;</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p39\"><strong>Type of Charges</strong></p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p37\">&nbsp;</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p40\">Late payment Charges</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p20\">NIL\n&nbsp;</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p41\">Part Prepayment Charges</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p20\">NIL&nbsp;</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p42\">Title Search Report Charges (Legal Charges)</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p20\">NIL&nbsp;</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p40\">Stamping Charges</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p20\">NIL&nbsp;</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p41\">Processing Fee</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p20\">NIL&nbsp;</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p43\">Other Charges</p>\n" + 
				"</td>\n" + 
				"<td class=\"td1\" valign=\"middle\">\n" + 
				"<p class=\"p20\">NIL&nbsp;</p>\n" + 
				"</td>\n" + 
				"</tr>\n" + 
				"</tbody>\n" + 
				"</table>\n" + 
				"<p class=\"p32\">&nbsp;</p>\n" + 
				"<p class=\"p32\">&nbsp;</p>\n" + 
				"<p class=\"p8\" style=\"text-align: center;\"><strong>SCHEDULE II</strong></p>\n" + 
				"<p class=\"p21\">&nbsp;</p>\n" + 
				"<p class=\"p8\" style=\"text-align: center;\"><strong>REPAYMENT SCHEDULE</strong></p>\n" + 
				"<p class=\"p44\">&nbsp;</p>\n" + 
				"<p class=\"p45\">The Borrower would be charged a simple interest of 0.1% per day on the outstanding principal balance from the Date of Agreement. After completion of 20 (twenty) days from the Date of Agreement, a computerized statement will be generated and shared with the Borrower mentioning the minimum amount due (MAD), total payment due and bill due date.</p>\n" + 
				"<p class=\"p9\">&nbsp;</p>\n" + 
				"<p class=\"p45\">The Borrower has to pay MAD on or before due date as mentioned in the statement. In case the MAD is not paid by the bill due date, then a compound interest of 0.15% per day would be charged on the MAD while the rest of the outstanding balance will continue to attract interest at 0.1% per day.</p>\n" + 
				"<p class=\"p9\">&nbsp;</p>\n" + 
				"<p class=\"p44\">&nbsp;</p>\n" + 
				"<p class=\"p44\">&nbsp;</p>";
		return html;
	}
	
	public Integer getEdiAmount(Integer tenure,Double amount) {
		int ediCount = LoanUtil.getEdiDays(tenure);
		int edi = (int) Math.ceil(((amount + (amount * 0.02 * tenure))) / ediCount);
		return edi;
	}
	
}
