package com.bharatpe.lending.dto;

import com.bharatpe.lending.constant.CreditConstants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentDetailDto {
    String name;
    String type;
    String fundSource;
    Double balance;
    Double amountLimit;
    String description;
    String offers;
    boolean authRequired;
    boolean isDefault;
    List<String> psps;
    boolean enable;
    boolean initiate_sb;
    String sb_link;
    String upiType;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFundSource() {
        return fundSource;
    }

    public void setFundSource(String fundSource) {
        this.fundSource = fundSource;
    }

    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOffers() {
        return offers;
    }

    public void setOffers(String offers) {
        this.offers = offers;
    }

    public boolean isAuthRequired() {
        return authRequired;
    }

    public void setAuthRequired(boolean authRequired) {
        this.authRequired = authRequired;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public List<String> getPsps() {
        return psps;
    }

    public void setPsps(List<String> psps) {
        this.psps = psps;
    }

    public Double getAmountLimit() {
        return amountLimit;
    }

    public void setAmountLimit(Double amountLimit) {
        this.amountLimit = amountLimit;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public boolean isInitiate_sb() {
        return initiate_sb;
    }

    public void setInitiate_sb(boolean initiate_sb) {
        this.initiate_sb = initiate_sb;
    }

    public String getSb_link() {
        return sb_link;
    }

    public void setSb_link(String sb_link) {
        this.sb_link = sb_link;
    }

	public String getUpiType() {
		return upiType;
	}

	public void setUpiType(String upiType) {
		this.upiType = upiType;
	}
    
}
