package com.bharatpe.lending.lendingplatform.nbfc.registry;

import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WorkflowRegistryFactory {
    @Autowired
    @Lazy
    private TrillionWorkflowRegistry trillionWorkflowRegistry;

    public WorkflowRegistry getWorkflowRegistry(Lender lender) {
        switch (lender) {
            case TRILLIONLOANS:
                return trillionWorkflowRegistry;
            default: {
                log.error("Invalid lender: {}. Returning an empty workflow registry.", lender);
                return null;
            }
        }
    }
}

