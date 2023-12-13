package com.bharatpe.lending.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
@Builder
public class MileStoneDataForSupport {
    private int AchieveMilestone;
    private int TargetMileStone;

    private int AchieveMileStoneActiveDays;
    private int TargetActiveDays;

    private int AchieveMileStoneUniquePayer;
    private int TargetUniquePayer;

    private Date milestone_start_time;
    private Date milestone_end_time;
    private List<Object> unq_payer_daily;
    private List<Object> active_days_daily;
    private int tpv;
}
