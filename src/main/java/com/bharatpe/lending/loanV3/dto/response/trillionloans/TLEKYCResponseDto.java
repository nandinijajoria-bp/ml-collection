package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLEKYCResponseDto {
    public String url;
    public String customerName;
    public String kid;
    public String createdAt;
    public Integer expireInDays;
}