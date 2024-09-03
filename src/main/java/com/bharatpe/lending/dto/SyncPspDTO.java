package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.List;



@Data
@ToString
@Builder
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class SyncPspDTO {

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
