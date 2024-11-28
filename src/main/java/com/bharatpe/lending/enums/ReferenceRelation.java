package com.bharatpe.lending.enums;

public enum ReferenceRelation {
    FATHER("FATHER"),
    MOTHER("MOTHER"),
    BROTHER("BROTHER"),
    SISTER("SISTER"),
    WIFE("WIFE"),
    HUSBAND("HUSBAND"),
    FRIEND("FRIEND"),
    OTHER("OTHER");

    String val;

    ReferenceRelation(String val) {
        this.val = val;
    }

    public String getVal() {
        return val;
    }
}
