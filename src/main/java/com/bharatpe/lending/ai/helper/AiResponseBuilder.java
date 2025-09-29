package com.bharatpe.lending.ai.helper;

import com.bharatpe.lending.ai.dto.AiSupportLoanData;
import com.bharatpe.lending.ai.dto.AiSupportLoanResponse;
import com.bharatpe.lending.dto.SupportLoanResponseDTO;
import com.bharatpe.lending.dto.SupportResponseDTO;

public class AiResponseBuilder {

    public static AiSupportLoanResponse getSupportResponse(SupportResponseDTO supportResponse) {
        if (supportResponse == null || supportResponse.getData() == null) {
            return new AiSupportLoanResponse(false, "No data available");
        }
        SupportLoanResponseDTO data = (SupportLoanResponseDTO) supportResponse.getData();
        AiSupportLoanData aiData = new AiSupportLoanData(data);
        return new AiSupportLoanResponse(supportResponse.isSuccess(), supportResponse.getMessage(), aiData);
    }
}
