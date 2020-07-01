package com.bharatpe.lending.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.bharatpe.common.entities.BaseEntity;

@Entity
@Table(name = "loan_agreement")
public class LoanAgreement extends BaseEntity {
	
	 @Column(name = "merchant_id")
	private Long merchantId;
	
	 @Column(name = "application_id")
	private Long applicationId;
	
	 @Column(name = "agreement_name")
	private String agreementName;
	
	 @Column(name = "shorturl")
	private String shortUrl;
	
	private String keyword;

	public Long getMerchantId() {
		return merchantId;
	}

	public void setMerchantId(Long merchantId) {
		this.merchantId = merchantId;
	}

	public Long getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(Long applicationId) {
		this.applicationId = applicationId;
	}

	public String getAgreementName() {
		return agreementName;
	}

	public void setAgreementName(String agreementName) {
		this.agreementName = agreementName;
	}

	public String getShortUrl() {
		return shortUrl;
	}

	public void setShortUrl(String shortUrl) {
		this.shortUrl = shortUrl;
	}

	public String getKeyword() {
		return keyword;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}

	@Override
	public String toString() {
		return "LoanAgreement [merchantId=" + merchantId + ", applicationId=" + applicationId + ", agreementName="
				+ agreementName + ", shortUrl=" + shortUrl + ", keyword=" + keyword + "]";
	}
	
	
}
 
 
