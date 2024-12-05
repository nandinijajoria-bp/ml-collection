package com.bharatpe.lending.entity;

import com.bharatpe.common.entities.BaseEntity;
import lombok.Data;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Table(name = "bl_doc_lender_details")
@Data
@ToString
public class BLDocLenderDetails extends BaseEntity {
    @Column(name = "lender")
    private String lender;
    @Column(name = "doc_type")
    private String docType;
    @Column(name = "doc_status")
    private String docStatus;
    @Column(name = "mandatory_for_bl")
    private Boolean mandatoryForBl;
    @Column(name = "priority")
    private Integer priority;
}
