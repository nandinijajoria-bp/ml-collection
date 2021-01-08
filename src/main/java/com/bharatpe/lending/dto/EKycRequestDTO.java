package com.bharatpe.lending.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

public class EKycRequestDTO {

	@JsonProperty("m_id")
	private String mId;
	@JsonProperty("status")
	private Boolean status;
	@JsonProperty("status_message")
	private String statusMessage;
	@JsonProperty("gender")
	private String gender;
	@JsonProperty( "dob")
	private String dob;
	@JsonProperty( "name")
	private String name;
	@JsonProperty("address")
	private String address;
	@JsonProperty( "city")
	private String city;
	@JsonProperty("pincode")
	private String pincode;
	@JsonProperty("country")
	private String country;
	@JsonProperty("response")
	private String response;
	@JsonProperty("state")
	private String state;
	@JsonProperty("xml_response")
	private String xmlResponse;


	public String getmId() {
		return mId;
	}

	public void setmId(String mId) {
		this.mId = mId;
	}

	public Boolean getStatus() {
		return status;
	}

	public void setStatus(Boolean status) {
		this.status = status;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	public void setStatusMessage(String statusMessage) {
		this.statusMessage = statusMessage;
	}

	public String getGender() {
		return gender;
	}

	public void setGender(String gender) {
		this.gender = gender;
	}

	public String getDob() {
		return dob;
	}

	public void setDob(String dob) {
		this.dob = dob;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getPincode() {
		return pincode;
	}

	public void setPincode(String pincode) {
		this.pincode = pincode;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getResponse() {
		return response;
	}

	public void setResponse(String response) {
		this.response = response;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getXmlResponse() {
		return xmlResponse;
	}

	public void setXmlResponse(String xmlResponse) {
		this.xmlResponse = xmlResponse;
	}

	@Override
	public String toString() {
		return "EKycRequestDTO{" +
				"mId='" + mId + '\'' +
				", status=" + status +
				", statusMessage='" + statusMessage + '\'' +
				", gender='" + gender + '\'' +
				", dob='" + dob + '\'' +
				", name='" + name + '\'' +
				", address='" + address + '\'' +
				", city='" + city + '\'' +
				", pincode='" + pincode + '\'' +
				", country='" + country + '\'' +
				", response='" + response + '\'' +
				", state='" + state + '\'' +
				", xmlResponse='" + xmlResponse + '\'' +
				'}';
	}
}
