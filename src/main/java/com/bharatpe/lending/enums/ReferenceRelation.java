package com.bharatpe.lending.enums;

public enum ReferenceRelation {
    FATHER("FATHER"),
    MOTHER("MOTHER"),
    BROTHER("BROTHER"),
    SISTER("SISTER"),
    WIFE("WIFE"),
    HUSBAND("HUSBAND"),
    FRIEND("FRIEND"),
    OTHER("OTHER"),
    PARENT("Parent"),
    BROTHER_SISTER("Brother/Sister"),
    HUSBAND_WIFE("Husband/Wife"),
    FRIEND_OTHER("Friend/Other");


    String val;

    ReferenceRelation(String val) {
        this.val = val;
    }

    public String getVal() {
        return val;
    }
}
