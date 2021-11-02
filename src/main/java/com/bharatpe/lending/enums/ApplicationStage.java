package com.bharatpe.lending.enums;

import lombok.Getter;

public enum ApplicationStage {

    ELIGIBLITY_NOT_CHECKED("ELIELIGIBLITY_NOT_CHECKEDGI"),
    INELIGIBLE("in_eligible"),
    NOT_STARTED("not_started"),
    DRAFT("draft"),
    SUBMITTED("submitted"),
    RELEVANT("relevant"),
    REJECTED("rejected"),
    ACTIVE_LOAN("active_loan"),
    CLOSED_LOAN("closed_loan"),
    PENDING_CPV_FOS_ASSIGNMENT("PENDING_CPV_FOS_ASSIGNMENT"),
    PENDING_CPV_SUBMISSION("PENDING_CPV_SUBMISSION"),
    PENDING_CPV_QC_ASSIGNMENT("PENDING_CPV_QC_ASSIGNMENT"),
    PENDING_CPV_QC("PENDING_CPV_QC"),
    CPV_REJECTED("CPV_REJECTED"),
    PENDING_CPV_REUPLOAD("PENDING_CPV_REUPLOAD");

    @Getter
    private final String stage;

    ApplicationStage(String stage) {
        this.stage = stage;
    }
}
