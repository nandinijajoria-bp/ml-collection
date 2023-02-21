package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AbflDataUploadService implements ILenderAssociationService<Optional> {
    @Autowired
    AbflDataUploadServiceUtil abflDataUploadServiceUtil;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {
        abflDataUploadServiceUtil.pushDataToNbfc(applicationId);
        return Optional.empty();
    }
}
