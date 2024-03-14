package com.bharatpe.lending.dto;

public class PanVerifyKYCResponseDto {
    private boolean status;
    private Data data;
    public class Data {
        private String id;
        private String version;
        private String identifier;
        private String active;
        private String panHolderFirstName;
        private String panHolderMiddleName;
        private String panHolderLastName;
        private String panNumber;
        private String panHolderName;
        private String dob;
        private boolean panValid;
        private boolean dobMatch;
        private boolean nameMatch;
        private String aadhaarSeedingStatus;
        private String provider;
        private String createdAt;
        private String updatedAt;

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

        public boolean getPanValid() {
            return panValid;
        }

        public void setPanValid(boolean panValid) {
            this.panValid = panValid;
        }

        public boolean getDobMatch() {
            return dobMatch;
        }

        public void setDobMatch(boolean dobMatch) {
            this.dobMatch = dobMatch;
        }

        public boolean getNameMatch() {
            return nameMatch;
        }

        public void setNameMatch(boolean nameMatch) {
            this.nameMatch = nameMatch;
        }
    }

    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }
}
