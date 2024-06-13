package com.bharatpe.lending.loanV3;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Data
public class NameAndDobDetailsDto {

    String firstName;
    String middleName;
    String LastName;
    String dob;
    String fullName;
}
