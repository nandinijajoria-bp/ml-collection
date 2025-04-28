package com.bharatpe.lending.lendingplatform.nbfc.dto.request;

import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAdditionalData;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.KYCDocuments;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycDocType;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

@Data
@Builder
public class KYCDocumentUploadRequest {
    @NotNull
    private ApplicationDetails applicationDetails;
    @NotNull
    private CustomerAdditionalData customerAdditionalData;
    @NotNull
    private Map<KycDocType, KYCDocuments> kycDocuments;
}
