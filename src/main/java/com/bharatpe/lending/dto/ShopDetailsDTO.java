package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ShopDetailsDTO {

	@JsonProperty("business_name")
	private String businessName = "";
	
	@JsonProperty("shop_number")
	private String shopNumber = "";
	
	@JsonProperty("street_address")
	private String streetAddress = "";
	
	@JsonProperty("area")
	private String area = "";
	
	@JsonProperty("landmark")
	private String landmark = "";
	
	@JsonProperty("pincode")
	private String pincode = "";
	
	@JsonProperty("city")
	private String city = "";
	
	@JsonProperty("state")
	private String state = "";

	@JsonProperty("alternative_contact")
	private String alternateContact = "";

	@JsonProperty("gstNumber")
	private String gstNumber = "";

	@JsonProperty("entityType")
	private String entityType = "";

	@JsonProperty("salary")
	private String salary = "";

	@JsonProperty("hasGST")
	private Boolean hasGST;

	@JsonProperty("experience")
	private String experience = "";

	@JsonProperty("businessCategory")
	private String businessCategory = "";

	public String getBusinessName() {
		return businessName;
	}

	public void setBusinessName(String businessName) {
		this.businessName = businessName;
	}

	public String getShopNumber() {
		return shopNumber;
	}

	public void setShopNumber(String shopNumber) {
		this.shopNumber = shopNumber;
	}

	public String getStreetAddress() {
		return streetAddress;
	}

	public void setStreetAddress(String streetAddress) {
		this.streetAddress = streetAddress;
	}

	public String getArea() {
		return area;
	}

	public void setArea(String area) {
		this.area = area;
	}

	public String getLandmark() {
		return landmark;
	}

	public void setLandmark(String landmark) {
		this.landmark = landmark;
	}

	public String getPincode() {
		return pincode;
	}

	public void setPincode(String pincode) {
		this.pincode = pincode;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getAlternateContact() {
		return alternateContact;
	}

	public String getGstNumber() {
		return gstNumber;
	}

	public void setGstNumber(String gstNumber) {
		this.gstNumber = gstNumber;
	}

	public String getEntityType() {
		return entityType;
	}

	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	public String getSalary() {
		return salary;
	}

	public void setSalary(String salary) {
		this.salary = salary;
	}

	public Boolean getHasGST() {
		return hasGST;
	}

	public void setHasGST(Boolean hasGST) {
		this.hasGST = hasGST;
	}

	public String getExperience() {
		return experience;
	}

	public void setExperience(String experience) {
		this.experience = experience;
	}

	public String getBusinessCategory() {
		return businessCategory;
	}

	public void setBusinessCategory(String businessCategory) {
		this.businessCategory = businessCategory;
	}

	public void setAlternateContact(String alternateContact) {
		this.alternateContact = alternateContact;
	}

	@Override
	public String toString() {
		return "ShopDetailsDTO [businessName=" + businessName + ", shopNumber=" + shopNumber + ", streetAddress="
				+ streetAddress + ", area=" + area + ", landmark=" + landmark + ", pincode=" + pincode + ", city="
				+ city + ", state=" + state + "]";
	}
	
}
