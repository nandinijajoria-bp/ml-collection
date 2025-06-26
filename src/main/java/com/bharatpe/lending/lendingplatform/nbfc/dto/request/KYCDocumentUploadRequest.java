package com.bharatpe.lending.lendingplatform.nbfc.dto.request;

import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAdditionalData;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerPersonalDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.KYCDocuments;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAddressDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CSCustomerAdditionalData;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycDocType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KYCDocumentUploadRequest {
    @NotNull
    private ApplicationDetails applicationDetails;
    @NotNull
    private CustomerAdditionalData customerAdditionalData;
    @NotNull
    private Map<KycDocType, KYCDocuments> kycDocuments;

    private CustomerPersonalDetails customerPersonalDetails;
    private CustomerAddressDetails customerAddressDetails;
    private LinkedHashMap<String, Object> identifier;
    private CSCustomerAdditionalData csCustomerAdditionalData;
}
