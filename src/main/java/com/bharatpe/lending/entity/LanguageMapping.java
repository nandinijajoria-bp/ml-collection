package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

@Data
@Entity
@Table(name = "language_mapping")
public class LanguageMapping extends BaseEntity {

    @Column(name = "language_label")
    private String languageLabel;

    @Column(name = "language_value")
    private String languageValue;

    @Column(name = "vernac_code")
    private String vernacCode;


}
