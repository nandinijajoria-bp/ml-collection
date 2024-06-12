package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PanVerifyKYCResponseDto {
    private Boolean status;
    private Data data;
    public class Data {
        private String id;
        private String message;
        private Boolean maxCountReached;
        private String version;
        private String identifier;
        private String active;
        private String panHolderFirstName;
        private String panHolderMiddleName;
        private String panHolderLastName;
        private String panNumber;
        private String panHolderName;
        private String dob;
        private Boolean panValid;
        private Boolean dobMatch;
        private Boolean nameMatch;
        private String aadhaarSeedingStatus;
        private String provider;
        private String createdAt;
        private String updatedAt;
        private String consumerId;
        private String merchantId;

        public String getPanNumber() {
            return panNumber;
        }

        public void setPanNumber(String panNumber) {
            this.panNumber = panNumber;
        }

        public String getPanHolderName() {
            return panHolderName;
        }

        public void setPanHolderName(String panHolderName) {
            this.panHolderName = panHolderName;
        }

        public String getDob() {
            return dob;
        }

        public void setDob(String dob) {
            this.dob = dob;
        }

        public String getAadhaarSeedingStatus() {
            return aadhaarSeedingStatus;
        }

        public void setAadhaarSeedingStatus(String aadhaarSeedingStatus) {
            this.aadhaarSeedingStatus = aadhaarSeedingStatus;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public String getActive() {
            return active;
        }

        public void setActive(String active) {
            this.active = active;
        }

        public String getPanHolderFirstName() {
            return panHolderFirstName;
        }

        public void setPanHolderFirstName(String panHolderFirstName) {
            this.panHolderFirstName = panHolderFirstName;
        }

        public String getPanHolderMiddleName() {
            return panHolderMiddleName;
        }

        public void setPanHolderMiddleName(String panHolderMiddleName) {
            this.panHolderMiddleName = panHolderMiddleName;
        }

        public String getPanHolderLastName() {
            return panHolderLastName;
        }

        public void setPanHolderLastName(String panHolderLastName) {
            this.panHolderLastName = panHolderLastName;
        }

        public Boolean getPanValid() {
            return panValid;
        }

        public void setPanValid(Boolean panValid) {
            this.panValid = panValid;
        }

        public Boolean getDobMatch() {
            return dobMatch;
        }

        public void setDobMatch(Boolean dobMatch) {
            this.dobMatch = dobMatch;
        }

        public Boolean getNameMatch() {
            return nameMatch;
        }

        public void setNameMatch(Boolean nameMatch) {
            this.nameMatch = nameMatch;
        }

        public String getConsumerId() {
            return consumerId;
        }

        public void setConsumerId(String consumerId) {
            this.consumerId = consumerId;
        }

        public String getMerchantId() {
            return merchantId;
        }

        public void setMerchantId(String merchantId) {
            this.merchantId = merchantId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Boolean getMaxCountReached() {
            return maxCountReached;
        }

        public void setMaxCountReached(Boolean maxCountReached) {
            this.maxCountReached = maxCountReached;
        }

        @Override
        public String toString() {
            return "Data{" +
                    "id='" + id + '\'' +
                    ", message='" + message + '\'' +
                    ", maxCountReached=" + maxCountReached +
                    ", version='" + version + '\'' +
                    ", identifier='" + identifier + '\'' +
                    ", active='" + active + '\'' +
                    ", panHolderFirstName='" + panHolderFirstName + '\'' +
                    ", panHolderMiddleName='" + panHolderMiddleName + '\'' +
                    ", panHolderLastName='" + panHolderLastName + '\'' +
                    ", panNumber='" + panNumber + '\'' +
                    ", panHolderName='" + panHolderName + '\'' +
                    ", dob='" + dob + '\'' +
                    ", panValid=" + panValid +
                    ", dobMatch=" + dobMatch +
                    ", nameMatch=" + nameMatch +
                    ", aadhaarSeedingStatus='" + aadhaarSeedingStatus + '\'' +
                    ", provider='" + provider + '\'' +
                    ", createdAt='" + createdAt + '\'' +
                    ", updatedAt='" + updatedAt + '\'' +
                    ", consumerId='" + consumerId + '\'' +
                    ", merchantId='" + merchantId + '\'' +
                    '}';
        }
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "PanVerifyKYCResponseDto{" +
                "status=" + status +
                ", data=" + data +
                '}';
    }
}
