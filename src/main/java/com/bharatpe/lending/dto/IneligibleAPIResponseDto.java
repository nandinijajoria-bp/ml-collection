package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Date;
import java.util.List;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class IneligibleAPIResponseDto {

	private Boolean success = true;
	private String message = "";
	private Date registrationDate;
	private Integer paymentCount = 0;
	private Integer paymentAmount = 0;
	private Boolean newMerchant = false;
	private Boolean amountSuccess = false;
	private Boolean countSuccess;
	private Boolean enach = false;
	private List<Banner> banners;

	public IneligibleAPIResponseDto() {
		super();
	}

	public IneligibleAPIResponseDto(Boolean success, String message) {
		super();
		this.success = success;
		this.message = message;
	}

	@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
	public class Banner {
		private String img;
		private String deepLink;

		public Banner() {
		}

		public Banner(String img, String deepLink) {
			this.img = img;
			this.deepLink = deepLink;
		}

		public String getImg() {
			return img;
		}

		public void setImg(String img) {
			this.img = img;
		}

		public String getDeepLink() {
			return deepLink;
		}

		public void setDeepLink(String deepLink) {
			this.deepLink = deepLink;
		}
	}

	public Boolean getSuccess() {
		return success;
	}

	public void setSuccess(Boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Date getRegistrationDate() {
		return registrationDate;
	}

	public void setRegistrationDate(Date registrationDate) {
		this.registrationDate = registrationDate;
	}

	public Integer getPaymentCount() {
		return paymentCount;
	}

	public void setPaymentCount(Integer paymentCount) {
		this.paymentCount = paymentCount;
	}

	public Integer getPaymentAmount() {
		return paymentAmount;
	}

	public void setPaymentAmount(Integer paymentAmount) {
		this.paymentAmount = paymentAmount;
	}

	public Boolean getNewMerchant() {
		return newMerchant;
	}

	public void setNewMerchant(Boolean newMerchant) {
		this.newMerchant = newMerchant;
	}

	public Boolean getAmountSuccess() {
		return amountSuccess;
	}

	public void setAmountSuccess(Boolean amountSuccess) {
		this.amountSuccess = amountSuccess;
	}

	public Boolean getCountSuccess() {
		return countSuccess;
	}

	public void setCountSuccess(Boolean countSuccess) {
		this.countSuccess = countSuccess;
	}

	public Boolean getEnach() {
		return enach;
	}

	public void setEnach(Boolean enach) {
		this.enach = enach;
	}

	public List<Banner> getBanners() {
		return banners;
	}

	public void setBanners(List<Banner> banners) {
		this.banners = banners;
	}

	public void addBanner(Banner banner) {
		this.banners.add(banner);
	}
}
