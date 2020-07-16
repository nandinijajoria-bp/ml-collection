package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class MerchantDetailsDTO implements Serializable {

    private Long applicationId;
    
    @JsonProperty(value = "module")
    private String module;
    
    public String getModule() {
		return module;
	}

	public void setModule(String module) {
		this.module = module;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
    private SelectedLoanDTO selectedLoan;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "personal_info")
    private PersonalDetails personalDetails;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ShopDetails shopDetails;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BusinessDetails businessDetails;

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public SelectedLoanDTO getSelectedLoan() {
        return selectedLoan;
    }

    public void setSelectedLoan(SelectedLoanDTO selectedLoan) {
        this.selectedLoan = selectedLoan;
    }

    public PersonalDetails getPersonalDetails() {
        return personalDetails;
    }

    public void setPersonalDetails(PersonalDetails PersonalDetails) {
        this.personalDetails = PersonalDetails;
    }

    public ShopDetails getShopDetails() {
        return shopDetails;
    }

    public void setShopDetails(ShopDetails shopDetails) {
        this.shopDetails = shopDetails;
    }

    public BusinessDetails getBusinessDetails() {
        return businessDetails;
    }

    public void setBusinessDetails(BusinessDetails businessDetails) {
        this.businessDetails = businessDetails;
    }

    @Override
	public String toString() {
		return "MerchantDetailsDTO [applicationId=" + applicationId + ", module=" + module + ", selectedLoan="
				+ selectedLoan + ", personalDetails=" + personalDetails + ", shopDetails=" + shopDetails
				+ ", businessDetails=" + businessDetails + "]";
	}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class PersonalDetails {

        private String alternateMobile;

        private String email;

        private String maritalStatus;

        private String residentialAddress;

        private String pincode;

        private String state;

        private String city;

        private String landmark;

        public String getAlternateMobile() {
            return alternateMobile;
        }

        public void setAlternateMobile(String alternateMobile) {
            this.alternateMobile = alternateMobile;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getMaritalStatus() {
            return maritalStatus;
        }

        public void setMaritalStatus(String maritalStatus) {
            this.maritalStatus = maritalStatus;
        }

        public String getResidentialAddress() {
            return residentialAddress;
        }

        public void setResidentialAddress(String residentialAddress) {
            this.residentialAddress = residentialAddress;
        }

        public String getPincode() {
            return pincode;
        }

        public void setPincode(String pincode) {
            this.pincode = pincode;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getLandmark() {
            return landmark;
        }

        public void setLandmark(String landmark) {
            this.landmark = landmark;
        }

        @Override
        public String toString() {
            return "PersonalDetails{" +
                    "alternateMobile='" + alternateMobile + '\'' +
                    ", email='" + email + '\'' +
                    ", maritalStatus='" + maritalStatus + '\'' +
                    ", residentialAddress='" + residentialAddress + '\'' +
                    ", pincode='" + pincode + '\'' +
                    ", state='" + state + '\'' +
                    ", city='" + city + '\'' +
                    ", landmark='" + landmark + '\'' +
                    '}';
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class ShopDetails {

        private String businessType;

        private String shopOwnership;

        private String shopName;

        private List<ImageDTO> shopImage;

        private List<ImageDTO> bharatpeQrImage;

        private List<ImageDTO> stockImage;

        public String getBusinessType() {
            return businessType;
        }

        public void setBusinessType(String businessType) {
            this.businessType = businessType;
        }

        public String getShopOwnership() {
            return shopOwnership;
        }

        public void setShopOwnership(String shopOwnership) {
            this.shopOwnership = shopOwnership;
        }

        public String getShopName() {
            return shopName;
        }

        public void setShopName(String shopName) {
            this.shopName = shopName;
        }

        public List<ImageDTO> getShopImage() {
            return shopImage;
        }

        public void setShopImage(List<ImageDTO> shopImage) {
            this.shopImage = shopImage;
        }

        public List<ImageDTO> getBharatpeQrImage() {
            return bharatpeQrImage;
        }

        public void setBharatpeQrImage(List<ImageDTO> bharatpeQrImage) {
            this.bharatpeQrImage = bharatpeQrImage;
        }

        public List<ImageDTO> getStockImage() {
            return stockImage;
        }

        public void setStockImage(List<ImageDTO> stockImage) {
            this.stockImage = stockImage;
        }

        @Override
        public String toString() {
            return "ShopDetails{" +
                    "businessType='" + businessType + '\'' +
                    ", shopOwnership='" + shopOwnership + '\'' +
                    ", shopName='" + shopName + '\'' +
                    ", shopImage=" + shopImage +
                    ", bharatpeQrImage=" + bharatpeQrImage +
                    ", stockImage=" + stockImage +
                    '}';
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class BusinessDetails {

        private String businessStartDate;

        private String dailySales;

        private List<ImageDTO> relationshipImage;

        private List<ImageDTO> ownershipImage;

        private List<ImageDTO> repaymentDocImage;

        public String getBusinessStartDate() {
            return businessStartDate;
        }

        public void setBusinessStartDate(String businessStartDate) {
            this.businessStartDate = businessStartDate;
        }

        public String getDailySales() {
            return dailySales;
        }

        public void setDailySales(String dailySales) {
            this.dailySales = dailySales;
        }

        public List<ImageDTO> getRelationshipImage() {
            return relationshipImage;
        }

        public void setRelationshipImage(List<ImageDTO> relationshipImage) {
            this.relationshipImage = relationshipImage;
        }

        public List<ImageDTO> getOwnershipImage() {
            return ownershipImage;
        }

        public void setOwnershipImage(List<ImageDTO> ownershipImage) {
            this.ownershipImage = ownershipImage;
        }

        public List<ImageDTO> getRepaymentDocImage() {
            return repaymentDocImage;
        }

        public void setRepaymentDocImage(List<ImageDTO> repaymentDocImage) {
            this.repaymentDocImage = repaymentDocImage;
        }

        @Override
        public String toString() {
            return "BusinessDetails{" +
                    "businessStartDate='" + businessStartDate + '\'' +
                    ", dailySales='" + dailySales + '\'' +
                    ", relationshipImage=" + relationshipImage +
                    ", ownershipImage=" + ownershipImage +
                    ", repaymentDocImage=" + repaymentDocImage +
                    '}';
        }
    }

}
