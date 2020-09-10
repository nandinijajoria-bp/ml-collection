package com.bharatpe.lending.dto;

public class EkycManualRequestDTO {

	

private String fullAdress;
	
	private String  city;
	
	private String state;
	
	private Long pincode;
	
	private Boolean isAddressChanged;

	public String getFullAdress() {
		return fullAdress;
	}

	public void setFullAdress(String fullAdress) {
		this.fullAdress = fullAdress;
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

	public Long getPincode() {
		return pincode;
	}

	public void setPincode(Long pincode) {
		this.pincode = pincode;
	}

	public Boolean getIsAddressChanged() {
		return isAddressChanged;
	}

	public void setIsAddressChanged(Boolean isAddressChanged) {
		this.isAddressChanged = isAddressChanged;
	}

	@Override
	public String toString() {
		return "EKycRequestDTO [fullAdress=" + fullAdress + ", city=" + city + ", state=" + state + ", pincode="
				+ pincode + ", isAddressChanged=" + isAddressChanged + "]";
	}
	
}
