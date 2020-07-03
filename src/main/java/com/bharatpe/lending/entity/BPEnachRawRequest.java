package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;


import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "merchant_nach_raw_response")
public class BPEnachRawRequest extends BaseEntity {

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "merchant_store_id")
    private Long merchantStoreId;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "api_name")
    private String apiName;

    private String request;

    private String response;

    private String status;

    public BPEnachRawRequest(Long merchantId, String apiName) {
        this.merchantId = merchantId;
        this.apiName = apiName;
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

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getRequest() {
        return request;
    }

    public void setRequest(String request) {
        this.request = request;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
