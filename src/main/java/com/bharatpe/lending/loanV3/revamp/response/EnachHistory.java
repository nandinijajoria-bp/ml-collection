package com.bharatpe.lending.loanV3.revamp.response;


import lombok.Data;

import java.util.Date;

@Data
public class EnachHistory {
    private String status;
    private String nachId;
    private String nachDate;
    private String message;
}

