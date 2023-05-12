package com.bharatpe.lending.dto;

import com.bharatpe.lending.enums.Lender;

import java.util.List;

public class PgCreateTransactionRequestDTO {
    private String orderId;
    private Double orderAmount;
    private String redirectURI;
    private String redirectURIDeeplink;
    private String paymentPageHeaderText;
    private String narration;
    private List<String> allowedModes;
    private Lender lender;
    private String checkout;

    private boolean isPgWebMode;

    public String getCheckout() {
        return checkout;
    }

    public void setCheckout(String checkout) {
        this.checkout = checkout;
    }

    public String getOrderId() {
        return orderId;
    }

    public Lender getLender() {
        return lender;
    }

    public void setLender(Lender lender) {
        this.lender = lender;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Double getOrderAmount() {
        return orderAmount;
    }

    public void setOrderAmount(Double orderAmount) {
        this.orderAmount = orderAmount;
    }

    public String getRedirectURI() {
        return redirectURI;
    }

    public void setRedirectURI(String redirectURI) {
        this.redirectURI = redirectURI;
    }

    public String getRedirectURIDeeplink() {
        return redirectURIDeeplink;
    }

    public void setRedirectURIDeeplink(String redirectURIDeeplink) {
        this.redirectURIDeeplink = redirectURIDeeplink;
    }

    public String getPaymentPageHeaderText() {
        return paymentPageHeaderText;
    }

    public void setPaymentPageHeaderText(String paymentPageHeaderText) {
        this.paymentPageHeaderText = paymentPageHeaderText;
    }

    public String getNarration() {
        return narration;
    }

    public void setNarration(String narration) {
        this.narration = narration;
    }

    public List<String> getAllowedModes() {
        return allowedModes;
    }

    public void setAllowedModes(List<String> allowedModes) {
        this.allowedModes = allowedModes;
    }

    public boolean isPgWebMode() {
        return isPgWebMode;
    }

    public void setPgWebMode(boolean pgWebMode) {
        isPgWebMode = pgWebMode;
    }
}
