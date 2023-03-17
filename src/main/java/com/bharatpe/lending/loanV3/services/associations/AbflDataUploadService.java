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
        String[] documents = {"KFS", "SANCTION_AGREEMENT", "SHOP-FRONT", "SHOP-STOCK"};
        boolean systemMangedState = true;
        if (args != null && args.containsKey("documents")) {
            documents = ((String) args.get("documents")).split(";");
        }
        if (args != null && args.containsKey("documents")) {
            systemMangedState = (boolean) args.get("systemManagedState");
        }
        abflDataUploadServiceUtil.pushDataToNbfc(applicationId, Arrays.asList(documents), systemMangedState);
        return Optional.empty();
    }
}
