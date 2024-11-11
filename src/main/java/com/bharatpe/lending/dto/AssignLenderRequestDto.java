package com.bharatpe.lending.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignLenderRequestDto {
    private Long applicationId;
    private String lender;

    @Override
    public String toString() {
        return "AssignLenderRequestDto{" +
                "applicationId=" + applicationId +
                ", lender='" + lender + '\'' +
                '}';
    }
}