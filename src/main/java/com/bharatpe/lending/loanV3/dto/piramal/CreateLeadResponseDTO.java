package com.bharatpe.lending.loanV3.dto.piramal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateLeadResponseDTO {
    String leadId;
    List<ApplicantsDetail> applicantDetail;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApplicantsDetail {
        Applicant applicant;
        String customerId;

        @lombok.Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Applicant {
            String applicantType;
            String firstName;
            String middleName;
            String lastName;
            String nameAsPerKyc;
            String countryOfBirth;
            String gender;
            String salutation;
            String emailId;
            Long mobileNo;
            String maritalStatus;
            String residentialStatus;
            PanCardDetail panCardDetail;

            @lombok.Data
            public static class PanCardDetail {
                String name;
                String panCardNo;
                Date dateOfBirth;
            }

            Boolean incomeContributor;
            Boolean propertyOwner;
        }

        String consentStatus;
    }

    String productId;
    String createdBy;

}