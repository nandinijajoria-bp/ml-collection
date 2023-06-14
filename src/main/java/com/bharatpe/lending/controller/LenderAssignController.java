package com.bharatpe.lending.controller;

import com.bharatpe.lending.entity.LenderAssignmentRules;
import com.bharatpe.lending.entity.LendingLenderQuota;
import com.bharatpe.lending.service.impl.LenderAssignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assign")
@Slf4j
public class LenderAssignController {

    @Autowired
    LenderAssignService lenderAssignService;

    @GetMapping(value = "/rules")
    public List<LenderAssignmentRules> FetchLenderRules(){
        return lenderAssignService.getAllActiveRules();
    }

    @GetMapping(value = "/limit")
    public List<LendingLenderQuota> fetchLenderLimits(){
        return lenderAssignService.getAllLenderLimits();
    }

    @PostMapping(value = "/update-rule")
    public LenderAssignmentRules updateLenderRules(@RequestBody LenderAssignmentRules rule){
        return lenderAssignService.updateRules(rule);
    }

    @PostMapping(value = "/update-limit")
    public LendingLenderQuota updateLenderLimits(@RequestBody LendingLenderQuota limit){
        return lenderAssignService.updateLenderLimits(limit);
    }

    @PostMapping(value="/assign-lender")
    public String assignLender(@RequestParam Long applicationId, @RequestParam String ediModel){
        return lenderAssignService.assignLenderAndEdiModel(applicationId, ediModel);
    }
}
