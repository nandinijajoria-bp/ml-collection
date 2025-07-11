package com.bharatpe.lending.enums;

public enum ReferenceRelation {

    /**
     * WARNING: Do not change the order of enum constants as they are used for relation-based sorting.
     * The ordinal values are significant for the application logic.
     */
    PARENT("Parent"),
    FRIEND_OTHER("Friend/Other"),
    BROTHER_SISTER("Brother/Sister"),
    HUSBAND_WIFE("Husband/Wife"),
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
