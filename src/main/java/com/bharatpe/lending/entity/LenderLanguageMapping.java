package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "lender_language_mapping")
public class LenderLanguageMapping extends BaseEntity {

    @Column(name = "lender")
    private String lender;

    @Column(name = "language_id")
    private int languageId;

    @Column(name = "doc_type")
    private String docType;

}
