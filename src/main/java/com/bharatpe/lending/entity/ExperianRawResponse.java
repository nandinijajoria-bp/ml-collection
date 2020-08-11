package com.bharatpe.lending.entity;

import javax.persistence.Entity;
import javax.persistence.Table;

import com.bharatpe.lending.common.entity.BaseEntity;

@Entity
@Table(name = "merchant_nach_raw_response")
public class ExperianRawResponse extends BaseEntity{
	
	public Double bpScore;
	public String pancard;
	public String mobile;
	public String request;
	public String response;
	public String urlName;
	public Long merchantId;
	
	public Double getBpScore() {
		return bpScore;
	}
	public void setBpScore(Double bpScore) {
		this.bpScore = bpScore;
	}
	public String getPancard() {
		return pancard;
	}
	public void setPancard(String pancard) {
		this.pancard = pancard;
	}
	public String getMobile() {
		return mobile;
	}
	public void setMobile(String mobile) {
		this.mobile = mobile;
	}
	public String getRequest() {
		return request;
	}
	public void setRequest(String request) {
		this.request = request;
	}
	public String getResponse() {
		return response;
	}
	public void setResponse(String response) {
		this.response = response;
	}
	public String getUrlName() {
		return urlName;
	}
	public void setUrlName(String urlName) {
		this.urlName = urlName;
	}
	public Long getMerchantId() {
		return merchantId;
	}
	public void setMerchantId(Long merchantId) {
		this.merchantId = merchantId;
	}
	@Override
	public String toString() {
		return "ExperianRawResponse [bpScore=" + bpScore + ", pancard=" + pancard + ", mobile=" + mobile + ", request="
				+ request + ", response=" + response + ", urlName=" + urlName + ", merchantId=" + merchantId + "]";
	}

}
