package com.bharatpe.lending.dto;

public class RequestDTO<T> {
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
