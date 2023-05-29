package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "lms_stage_history")
public class LmsStageHistory extends BaseEntity {

    @Column(name = "lending_application_id")
    private Long lendingApplicationId;

    @Column(name = "lms_stage")
    private String lmsStage;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "request_id")
    private String requestId;
}