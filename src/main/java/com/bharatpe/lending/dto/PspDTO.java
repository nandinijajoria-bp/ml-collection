package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.ToStringBuilder;

public class PspDTO {
   
	@JsonProperty("app_name")
    public String appName;

    @JsonProperty("package_name")
    public String packageName;
    
    @JsonProperty("merchant_id")
    public Long merchantId;
    
    @JsonProperty("merchant_store_id")
    public Long merchantStoreId;

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
    
    public Long getMerchantId() {
		return merchantId;
	}

	public void setMerchantId(Long merchantId) {
		this.merchantId = merchantId;
	}

	public Long getMerchantStoreId() {
		return merchantStoreId;
	}

	public void setMerchantStoreId(Long merchantStoreId) {
		this.merchantStoreId = merchantStoreId;
	}

	@Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
