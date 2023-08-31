package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class LendingStateDTO<T> {
    T data;

    // this would be next stage in case prev stage is passed
    // or
    // curr stage when curr stage data is fetched
    LendingViewStates lendingViewStates;

    //LendingViewState corresponding to DTO used in data
    LendingViewStates scopeState;
}
