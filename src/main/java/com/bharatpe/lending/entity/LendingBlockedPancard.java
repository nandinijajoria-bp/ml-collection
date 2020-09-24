package com.bharatpe.lending.entity;

import javax.persistence.*;

@Entity
@Table(name = "lending_blocked_pancard")
public class LendingBlockedPancard {

    @Id
    private String pancard;

    public String getPancard() {
        return pancard;
    }

    public void setPancard(String pancard) {
        this.pancard = pancard;
    }
}
