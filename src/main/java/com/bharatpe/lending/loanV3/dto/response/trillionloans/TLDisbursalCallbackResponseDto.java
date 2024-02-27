package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLDisbursalCallbackResponseDto {

    private String loanApplicationId;

    private Double netDisbursement;

    private String lanID;

    private String status;

    private Double approvedAmount;

    private String receiptNumber;

    private String disbursementDate;

    public Date formatDisbursementDate(SimpleDateFormat simpleDateFormat) throws ParseException {
        return simpleDateFormat.parse(this.getDisbursementDate());
    }

}
