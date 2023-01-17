package com.bharatpe.lending.service;

public interface IRequestAudit<T,G> {
    G refineAuditData(T payload);
    String getEntityName();
}
