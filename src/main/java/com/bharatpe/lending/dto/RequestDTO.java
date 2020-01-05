package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RequestDTO<T> {
	
	@JsonProperty(value = "meta", required = false)
	private Meta meta;
	private T payload;

	public Meta getMeta() {
		return meta;
	}

	public void setMeta(Meta meta) {
		this.meta = meta;
	}


	public T getPayload() {
		return payload;
	}

	public void setPayload(T payload) {
		this.payload = payload;
	}

	@Override
	public String toString() {
		return "RequestDTO{" +
				"meta=" + meta +
				", payload=" + payload +
				'}';
	}
}
