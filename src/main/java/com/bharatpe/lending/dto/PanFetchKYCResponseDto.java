package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PanFetchKYCResponseDto {
    private Boolean status;
    private Data data;
    public static class Data {
        private String id;
        private String message;
        private Boolean maxCountReached;
        private String createdAt;
        private String updatedAt;
        private String version;
        private String panNumber;
        private String identifier;
        private String name;
        private String active;
        private String firstName;
        private String lastName;
        private String middleName;
        private String dateOfBirth;
        private String gender;
        private String maskedAadhaar;
        private Boolean aadhaarLinked;
        private String provider;
        private String consumerId;
        private String merchantId;
        private Boolean isPanNsdlVerified;
        private String verifiedName;
        private String verifiedDob;
        private NonNsdladdress nonNsdladdress;

        public class NonNsdladdress {
            private String fullAddress;
            private String addressLineOne;
            private String addressLineTwo;
            private String street;
            private String city;
            private String state;
            private String pincode;
            private String country;

            public String getFullAddress() {
                return fullAddress;
            }

            public void setFullAddress(String fullAddress) {
                this.fullAddress = fullAddress;
            }

            public String getAddressLineOne() {
                return addressLineOne;
            }

            public void setAddressLineOne(String addressLineOne) {
                this.addressLineOne = addressLineOne;
            }

            public String getAddressLineTwo() {
                return addressLineTwo;
            }

            public void setAddressLineTwo(String addressLineTwo) {
                this.addressLineTwo = addressLineTwo;
            }

            public String getStreet() {
                return street;
            }

            public void setStreet(String street) {
                this.street = street;
            }

            public String getCity() {
                return city;
            }

            public void setCity(String city) {
                this.city = city;
            }

            public String getState() {
                return state;
            }

            public void setState(String state) {
                this.state = state;
            }

            public String getPincode() {
                return pincode;
            }

            public void setPincode(String pincode) {
                this.pincode = pincode;
            }

            public String getCountry() {
                return country;
            }

            public void setCountry(String country) {
                this.country = country;
            }

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

        public String getPanNumber() {
            return panNumber;
        }

        public void setPanNumber(String panNumber) {
            this.panNumber = panNumber;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getActive() {
            return active;
        }

        public void setActive(String active) {
            this.active = active;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getMiddleName() {
            return middleName;
        }

        public void setMiddleName(String middleName) {
            this.middleName = middleName;
        }

        public String getDateOfBirth() {
            return dateOfBirth;
        }

        public void setDateOfBirth(String dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public String getMaskedAadhaar() {
            return maskedAadhaar;
        }

        public void setMaskedAadhaar(String maskedAadhaar) {
            this.maskedAadhaar = maskedAadhaar;
        }

        public Boolean isAadhaarLinked() {
            return aadhaarLinked;
        }

        public void setAadhaarLinked(Boolean aadhaarLinked) {
            this.aadhaarLinked = aadhaarLinked;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
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

        public Boolean getIsPanNsdlVerified() {
            return isPanNsdlVerified;
        }

        public void setIsPanNsdlVerified(Boolean panNsdlVerified) {
            isPanNsdlVerified = panNsdlVerified;
        }

        public String getVerifiedName() {
            return verifiedName;
        }

        public void setVerifiedName(String verifiedName) {
            this.verifiedName = verifiedName;
        }

        public String getVerifiedDob() {
            return verifiedDob;
        }

        public void setVerifiedDob(String verifiedDob) {
            this.verifiedDob = verifiedDob;
        }

        public NonNsdladdress getNonNsdladdress() {
            return nonNsdladdress;
        }

        public void setNonNsdladdress(NonNsdladdress nonNsdladdress) {
            this.nonNsdladdress = nonNsdladdress;
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
}
