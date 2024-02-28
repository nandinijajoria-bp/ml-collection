package com.bharatpe.lending.dto;


import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class MileStoneDashboardDetailsForCRM {
    private List<MileStoneDataForSupport> mapList;
    private Date mileStoneCreatedAt;
    private int achievementUniquePayer;
    private int achievementActiveDays;
    private int targetUniquePayer;
    private int targetActiveDays;
}
