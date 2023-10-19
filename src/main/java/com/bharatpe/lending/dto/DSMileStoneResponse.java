package com.bharatpe.lending.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

@Slf4j
@Data
public class DSMileStoneResponse {

    public int cashback;

    public int target_duration_days;

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
