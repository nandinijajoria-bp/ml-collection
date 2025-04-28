package com.bharatpe.lending.lendingplatform.nbfc.dto.request;

import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAdditionalData;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.LoanDocument;
import com.bharatpe.lending.lendingplatform.nbfc.enums.DocType;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

@Data
@Builder
public class LoanDocumentUploadRequest {
    @NotNull
    private ApplicationDetails applicationDetails;
    @NotNull
    private Map<DocType, LoanDocument> loanDocuments;
    @NotNull
    private CustomerAdditionalData customerAdditionalData;
}
