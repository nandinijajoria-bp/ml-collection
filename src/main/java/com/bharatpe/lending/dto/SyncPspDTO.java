package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.List;

public class SyncPspDTO {
    @JsonProperty("psp_list")
    public List<PspDTO> pspList;

    public List<PspDTO> getPspList() {
        return pspList;
    }

    public void setPspList(List<PspDTO> pspList) {
        this.pspList = pspList;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
