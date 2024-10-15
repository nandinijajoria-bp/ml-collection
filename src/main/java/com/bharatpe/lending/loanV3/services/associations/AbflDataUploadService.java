package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AbflDataUploadService implements ILenderAssociationService<Optional> {
    @Autowired
    @Lazy
    AbflDataUploadServiceUtil abflDataUploadServiceUtil;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {
        String[] documents = {"KFS_SANCTION_AGREEMENT", "SHOP-FRONT", "SHOP-STOCK"};
        Boolean digiSignRetry = false;
        boolean systemMangedState = true;
        try {
            if (args != null) {
                documents = args.containsKey("documents") ? ((String) args.get("documents")).split(";") : documents;
                systemMangedState = args.containsKey("systemManagedState") ? Boolean.parseBoolean((String) args.get("systemManagedState")) : systemMangedState;
                digiSignRetry = args.containsKey("digi_sign_retry") ? Boolean.parseBoolean((String) args.get("digi_sign_retry")) : false;
            }
        } catch (Exception e) {
            log.info("exception occurred while parsing args for {} {} {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        abflDataUploadServiceUtil.pushDataToNbfc(applicationId, Arrays.asList(documents), systemMangedState, digiSignRetry);
        return Optional.empty();
    }
}