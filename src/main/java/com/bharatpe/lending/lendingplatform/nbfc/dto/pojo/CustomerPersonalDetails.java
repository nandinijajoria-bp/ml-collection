package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class CustomerPersonalDetails {
    private AadhaarDetails aadhaarDetails;
    private PanDetails panDetails;
    private PanFetchDetails panFetchDetails;
    private String merchantType;
    private long mobile;
    private long customerId;
    private String mid;
    private String email;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @Data
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor
    public static class AadhaarDetails {
        private String aadhaar;
        private NameDobDetails nameDobDetails;
        private String fatherName;
        private String gender;
        private String sonOfDaughterOf;
        private String wifeOf;
        private String careOf;
    }

    @Data
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor
    public static class PanDetails {
        private String pan;
        private NameDobDetails nameDobDetails;
        private String gender;
    }

    @Data
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor
    public static class NameDobDetails {
        private String salutation;
        private String firstName;
        private String middleName;
        private String lastName;
        private String fullName;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy", timezone = "Asia/Kolkata")
        private Date dob;
    }

    @Data
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor
    public static class PanFetchDetails {
        private String pan;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy", timezone = "Asia/Kolkata")
        private Date dob;
        private String gender;
    }

}