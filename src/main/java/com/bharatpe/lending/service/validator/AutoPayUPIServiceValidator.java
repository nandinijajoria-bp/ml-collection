package com.bharatpe.lending.service.validator;

import com.bharatpe.lending.exceptions.InvalidRequestException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Component
@Slf4j
@Data
public class AutoPayUPIServiceValidator {


    public void validatePageData(Optional<Integer> pageNum,
                                 @RequestParam(name = "page_size") Optional<Integer> pageSize)
    {
        if (ObjectUtils.isEmpty(pageNum) || ObjectUtils.isEmpty(pageSize)
                || !pageNum.isPresent() || !pageNum.isPresent()) {
            throw new InvalidRequestException("pageNum and page size not found");
        }
    }
}
