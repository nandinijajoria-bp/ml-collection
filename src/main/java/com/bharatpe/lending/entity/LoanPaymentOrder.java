package com.bharatpe.lending.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.bharatpe.common.entities.BaseEntity;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantStore;

@Entity
@Table(name = "loan_payment_order")
public class LoanPaymentOrder extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @ManyToOne
    @JoinColumn(name = "merchant_store_id")
    private MerchantStore merchantStore;
    
    @Column(name = "owner")
    private String owner;
    
    @Column(name = "owner_id")
    private Long ownerId;
    
    @Column(name = "order_id")
    private String orderId;
    
    @Column(name = "amount")
    private Double amount;
    
    @Column(name = "vpa")
    private String vpa;
    
    @Column(name = "bank_ref_no")
    private String bankRefNo;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "short_link")
    private String shortLink;
    
    @Column(name = "upi_intent")
    private String upiIntent;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "mid")
    private String mid;

	public Merchant getMerchant() {
		return merchant;
	}

	public void setMerchant(Merchant merchant) {
		this.merchant = merchant;
	}

	public MerchantStore getMerchantStore() {
		return merchantStore;
	}

	public void setMerchantStore(MerchantStore merchantStore) {
		this.merchantStore = merchantStore;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(Long ownerId) {
		this.ownerId = ownerId;
	}

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	public Double getAmount() {
		return amount;
	}

	public void setAmount(Double amount) {
		this.amount = amount;
	}

	public String getVpa() {
		return vpa;
	}

	public void setVpa(String vpa) {
		this.vpa = vpa;
	}

	public String getShortLink() {
		return shortLink;
	}

	public void setShortLink(String shortLink) {
		this.shortLink = shortLink;
	}

	public String getUpiIntent() {
		return upiIntent;
	}

	public void setUpiIntent(String upiIntent) {
		this.upiIntent = upiIntent;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
	
	public String getBankRefNo() {
		return bankRefNo;
	}

	public void setBankRefNo(String bankRefNo) {
		this.bankRefNo = bankRefNo;
	}
	
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
	
	public String getMid() {
		return mid;
	}

	public void setMid(String mid) {
		this.mid = mid;
	}

	@Override
	public String toString() {
		return "LoanPaymentOrder [merchant=" + merchant + ", merchantStore=" + merchantStore + ", owner=" + owner
				+ ", ownerId=" + ownerId + ", orderId=" + orderId + ", amount=" + amount + ", vpa=" + vpa
				+ ", bankRefNo=" + bankRefNo + ", description=" + description + ", shortLink=" + shortLink
				+ ", upiIntent=" + upiIntent + ", status=" + status + ", mid=" + mid + "]";
	}
	
}