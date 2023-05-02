package com.bharatpe.lending.service;

import com.bharatpe.lending.common.enums.EdiModel;

public interface IEdiModelAssignment {
    EdiModel assignModel(Long merchantId);
}
