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
	private String pincode;
	
	@JsonProperty("city")
	private String city = "";
	
	@JsonProperty("state")
	private String state = "";

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

	@Override
	public String toString() {
		return "ShopDetailsDTO [businessName=" + businessName + ", shopNumber=" + shopNumber + ", streetAddress="
				+ streetAddress + ", area=" + area + ", landmark=" + landmark + ", pincode=" + pincode + ", city="
				+ city + ", state=" + state + ", getBusinessName()=" + getBusinessName() + ", getShopNumber()="
				+ getShopNumber() + ", getStreetAddress()=" + getStreetAddress() + ", getArea()=" + getArea()
				+ ", getLandmark()=" + getLandmark() + ", getPincode()=" + getPincode() + ", getCity()=" + getCity()
				+ ", getState()=" + getState() + ", getClass()=" + getClass() + ", hashCode()=" + hashCode()
				+ ", toString()=" + super.toString() + "]";
	}
	
}
