package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class SimInfo {

    private String installId;
    private String deviceId;
    private List<Sim> sims;

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Sim{
        private String slot;
        private String simId;
        private String carrierName;
        private String phone;

        public String getSlot() {
            return slot;
        }

        public void setSlot(String slot) {
            this.slot = slot;
        }

        public String getSimId() {
            return simId;
        }

        public void setSimId(String simId) {
            this.simId = simId;
        }

        public String getCarrierName() {
            return carrierName;
        }

        public void setCarrierName(String carrierName) {
            this.carrierName = carrierName;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        @Override
        public String toString() {
            return "Sim{" +
                    "slot='" + slot + '\'' +
                    ", simId='" + simId + '\'' +
                    ", carrierName='" + carrierName + '\'' +
                    ", phone='" + phone + '\'' +
                    '}';
        }
    }

    public String getInstallId() {
        return installId;
    }

    public void setInstallId(String installId) {
        this.installId = installId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public List<Sim> getSims() {
        return sims;
    }

    public void setSims(List<Sim> sims) {
        this.sims = sims;
    }

    @Override
    public String toString() {
        return "SimInfo{" +
                "installId='" + installId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", sims=" + sims +
                '}';
    }
}
