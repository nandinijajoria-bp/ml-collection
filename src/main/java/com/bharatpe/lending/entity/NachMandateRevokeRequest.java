package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "nach_mandate_revoke_request")
public class NachMandateRevokeRequest extends BaseEntity {

    @Column(name = "merchant_id")
    private Long merchantId;
    @Column(name = "mobile")
    private String mobile;
    @Column(name = "name")
    private String name;
    @Column(name = "Status")
    private String status;

    public NachMandateRevokeRequest() {
    }

    public NachMandateRevokeRequest(Long merchantId, String mobile, String name, String status) {
        this.merchantId = merchantId;
        this.mobile = mobile;
        this.name = name;
        this.status = status;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
