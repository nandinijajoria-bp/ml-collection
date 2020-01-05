package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MetaDTO {

	@JsonProperty("app_version")
	private String appVersion;

	private String client;

	@JsonProperty("lat")
	private String latitude;

	@JsonProperty("lon")
	private String longitude;

	private String ip;

	@JsonProperty("device_id")
	private String deviceId;

	@JsonProperty("device_info")
	private DeviceInfoDTO deviceInfo;
	
	public String getAppVersion() {
		return appVersion;
	}

	public void setAppVersion(String appVersion) {
		this.appVersion = appVersion;
	}

	public String getClient() {
		return client;
	}

	public void setClient(String client) {
		this.client = client;
	}

	public String getLatitude() {
		return latitude;
	}

	public void setLattitude(String latitude) {
		this.latitude = latitude;
	}

	public String getLongitude() {
		return longitude;
	}

	public void setLongitude(String longitude) {
		this.longitude = longitude;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public DeviceInfoDTO getDeviceInfo() {
		return deviceInfo;
	}

	public void setDeviceInfo(DeviceInfoDTO deviceInfo) {
		this.deviceInfo = deviceInfo;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}
	
	@Override
	public String toString() {
		String response = "MetaRequest { app_version : " + appVersion +
						  " client : " + client +
						  " lat : " + latitude +
						  " lon : " + longitude +
						  " ip : " + ip +
						  " device_id : " + deviceId +
						  " Device_Info { " + deviceInfo + " }";
		return response;
	}
}
