package com.bharatpe.lending.loanV3.interfaces;


import java.util.Map;

public interface ILenderAssociationService<T> {
    T invoke(Long applicationId, Map<String,Object> args);
}
