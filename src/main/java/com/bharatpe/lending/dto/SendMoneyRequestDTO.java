package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.ToStringBuilder;

public class SendMoneyRequestDTO<T> {
	
	@JsonProperty(value = "common_params", required = false)
	private MetaDTO meta;

	@JsonProperty(value = "params", required = false)
	private T payload;

	public MetaDTO getMeta() {
		return meta;
	}

	public void setMeta(MetaDTO meta) {
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
		return ToStringBuilder.reflectionToString(this);
	}
}
