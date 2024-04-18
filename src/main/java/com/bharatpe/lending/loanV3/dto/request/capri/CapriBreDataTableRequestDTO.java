package com.bharatpe.lending.loanV3.dto.request.capri;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CapriBreDataTableRequestDTO {
    String industry;
    @JsonProperty("loan_segment")
    String loanSegment;

    @JsonProperty("risk_group")
    String riskGroup;

    @JsonProperty("totalsixtydaystpv")
    String tpv;
    String nfi;
    @JsonProperty("pincode_color")
    String pincodeColor;
    String tenure;
    String edi;
    String locale;
    String pincode;
}
