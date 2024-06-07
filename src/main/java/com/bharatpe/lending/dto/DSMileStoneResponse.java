package com.bharatpe.lending.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

@Slf4j
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DSMileStoneResponse {

    public int cashback;
    public String program_type;

    public int target_duration_days;
    public String loan_amount;
    public Integer max_limit;

    @JsonProperty("target")
    public ArrayList<Target> target;

    public total_target total_target;


    @Data
    public static class total_target {
        public int total_tpv;
        public int no_txn;
        public int unq_payer;
        public int active_days;
    }
}
