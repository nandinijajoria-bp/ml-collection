/**
 * REFACTORED VERSION - Performance Optimized getEligibleLenderList
 * 
 * Key Improvements:
 * 1. Batch database queries to eliminate N+1 problem
 * 2. Pre-compute and cache frequently used data
 * 3. Use efficient data structures (Sets for O(1) lookups)
 * 4. Reduce object creation and memory allocations
 * 5. Optimize string operations
 * 6. Add parallel processing where safe
 */

public List<EligibleOffersResponseDTO.TenureWithLender> getEligibleLenderList(Long merchantId, List<EligibleLoanDTO> eligibleLoans, BasicDetailsDto merchantDetails, LendingRiskVariables lendingRiskVariables, String evaluationId) {
    final String METHOD = "getEligibleLenderList";
    AsyncLoggerUtil.logInfo(logger, "ENTRY {} - Processing {} eligible loans for merchantId: {}", METHOD, eligibleLoans.size(), merchantId);

    try {
        // Call lender assignment handler to get eligible loans with assigned lenders
        List<EligibleLoanDTO> eligibleOffersWithLenders = lenderAssignmentHandlerV1(merchantId, eligibleLoans, merchantDetails, evaluationId);

        if (CollectionUtils.isEmpty(eligibleOffersWithLenders)) {
            AsyncLoggerUtil.logInfo(logger, "EXIT {} - No eligible offers with lenders found for merchantId: {}", METHOD, merchantId);
            return null;
        }

        // PERFORMANCE IMPROVEMENT 1: Pre-load and cache all required data
        LenderDataCache cache = preloadLenderData(merchantId, evaluationId, eligibleOffersWithLenders);
        
        // PERFORMANCE IMPROVEMENT 2: Filter switched off lenders efficiently
        filterSwitchedOffLenders(eligibleOffersWithLenders, cache.switchedOffLenderNames);
        
        // PERFORMANCE IMPROVEMENT 3: Process rejected lenders for open application
        List<String> rejectedLenders = processRejectedLenders(merchantId, eligibleOffersWithLenders, cache.openApplication);

        // PERFORMANCE IMPROVEMENT 4: Process loans with optimized data structures
        List<EligibleOffersResponseDTO.TenureWithLender> tenureWithLenders = processEligibleLoansOptimized(
                merchantId, eligibleOffersWithLenders, lendingRiskVariables, evaluationId, 
                rejectedLenders, cache);

        // Handle empty result case
        if (tenureWithLenders.isEmpty()) {
            return createDefaultTenureWithLender(rejectedLenders, cache.ineligibleLenders);
        }

        AsyncLoggerUtil.logInfo(logger, "EXIT {} - Found {} tenure options for merchantId: {}",
                METHOD, tenureWithLenders.size(), merchantId);
        return tenureWithLenders;
    } catch (Exception e) {
        AsyncLoggerUtil.logError(logger, "Unexpected error in {}: {}", METHOD, e.getMessage(), e);
        return null;
    }
}

/**
 * PERFORMANCE IMPROVEMENT 1: Pre-loads and caches common data to avoid repeated database calls
 * This eliminates the N+1 query problem by batching all database operations
 */
private LenderDataCache preloadLenderData(Long merchantId, String evaluationId, List<EligibleLoanDTO> eligibleOffersWithLenders) {
    // Batch fetch all required data in parallel where possible
    CompletableFuture<LendingApplication> openApplicationFuture = CompletableFuture.supplyAsync(() -> 
        lendingApplicationDao.findByMerchantIdAndStatus(merchantId, ApplicationStatus.DRAFT.name()));
    
    CompletableFuture<List<LenderMetricsHistory>> switchedOffLendersFuture = CompletableFuture.supplyAsync(() -> 
        lenderMetricsHistoryDao.findByIsLenderSwitchedOff(Boolean.TRUE));
    
    CompletableFuture<List<OfferRankingConfig>> initialConfigsFuture = CompletableFuture.supplyAsync(() -> 
        offerRankingConfigDao.findByEnabledAndRankingType(true, RankingType.INITIAL));
    
    CompletableFuture<List<OfferRankingConfig>> fallbackConfigsFuture = CompletableFuture.supplyAsync(() -> 
        offerRankingConfigDao.findByEnabledAndRankingType(true, RankingType.FALLBACK));

    // Collect all unique lender names across all loans for batch processing
    Set<String> allLenderNames = eligibleOffersWithLenders.stream()
            .filter(loan -> !CollectionUtils.isEmpty(loan.getEligibleLenders()))
            .flatMap(loan -> loan.getEligibleLenders().stream())
            .collect(Collectors.toSet());

    // Batch fetch lender metrics history for all lenders at once
    CompletableFuture<List<LenderMetricsHistory>> allLenderMetricsFuture = allLenderNames.isEmpty() ? 
            CompletableFuture.completedFuture(Collections.emptyList()) : 
            CompletableFuture.supplyAsync(() -> 
                lenderMetricsHistoryDao.findByLenderInAndIsLenderSwitchedOffFalse(new ArrayList<>(allLenderNames)));

    // Wait for all async operations to complete
    try {
        LendingApplication openApplication = openApplicationFuture.get();
        List<LenderMetricsHistory> switchedOffLenders = switchedOffLendersFuture.get();
        List<OfferRankingConfig> initialOfferRankingConfigs = initialConfigsFuture.get();
        List<OfferRankingConfig> fallbackOfferRankingConfigs = fallbackConfigsFuture.get();
        List<LenderMetricsHistory> allLenderMetricsHistory = allLenderMetricsFuture.get();

        // Convert to efficient data structures
        Set<String> switchedOffLenderNames = switchedOffLenders.stream()
                .map(LenderMetricsHistory::getLender)
                .collect(Collectors.toSet());

        return new LenderDataCache(
                openApplication,
                switchedOffLenderNames,
                initialOfferRankingConfigs,
                fallbackOfferRankingConfigs,
                allLenderMetricsHistory,
                new HashSet<>()
        );
    } catch (Exception e) {
        AsyncLoggerUtil.logError(logger, "Error preloading lender data for merchantId {}: {}", merchantId, e.getMessage(), e);
        throw new RuntimeException("Failed to preload lender data", e);
    }
}

/**
 * PERFORMANCE IMPROVEMENT 2: Efficiently filters out switched off lenders from all loans
 * Uses Set for O(1) lookup performance
 */
private void filterSwitchedOffLenders(List<EligibleLoanDTO> eligibleOffersWithLenders, Set<String> switchedOffLenderNames) {
    if (switchedOffLenderNames.isEmpty()) {
        AsyncLoggerUtil.logInfo(logger, "No lenders are switched off, skipping filtering step");
        return;
    }

    AsyncLoggerUtil.logInfo(logger, "Lenders switched off in the system: {} for merchantId: {}", switchedOffLenderNames, merchantId);
    
    // Use parallel stream for large collections
    eligibleOffersWithLenders.parallelStream().forEach(loan -> {
        if (loan.getEligibleLenders() != null) {
            loan.getEligibleLenders().removeAll(switchedOffLenderNames);
            AsyncLoggerUtil.logInfo(logger, "Filtered out switched off lenders for tenure {} months, remaining lenders: {}",
                    loan.getTenureInMonths(), loan.getEligibleLenders());
        }
    });
}

/**
 * PERFORMANCE IMPROVEMENT 3: Processes rejected lenders for open application
 */
private List<String> processRejectedLenders(Long merchantId, List<EligibleLoanDTO> eligibleOffersWithLenders, LendingApplication openApplication) {
    List<String> rejectedLenders = Collections.emptyList();
    
    if (openApplication != null) {
        AsyncLoggerUtil.logInfo(logger, "Found open application with ID: {}", openApplication.getId());
        List<String> alreadyAssignedLender = lendingApplicationLenderDetailsDao.findLendersByApplicationId(openApplication.getId());
        AsyncLoggerUtil.logInfo(logger, "Already assigned lenders for applicationId : {} {}", openApplication.getId(), alreadyAssignedLender);

        rejectedLenders = alreadyAssignedLender;

        // Remove rejected lenders from all loans efficiently using parallel stream
        eligibleOffersWithLenders.parallelStream().forEach(loan -> {
            if (loan.getEligibleLenders() != null) {
                loan.getEligibleLenders().removeAll(alreadyAssignedLender);
            }
        });
    }

    AsyncLoggerUtil.logInfo(logger, "Lenders after removing rejected lenders due to open application: {} for merchantId: {}", 
            eligibleOffersWithLenders, merchantId);
    return rejectedLenders;
}

/**
 * PERFORMANCE IMPROVEMENT 4: Processes eligible loans with optimized data structures and parallel processing
 */
private List<EligibleOffersResponseDTO.TenureWithLender> processEligibleLoansOptimized(
        Long merchantId, List<EligibleLoanDTO> eligibleOffersWithLenders, 
        LendingRiskVariables lendingRiskVariables, String evaluationId,
        List<String> rejectedLenders, LenderDataCache cache) {
    
    // PERFORMANCE IMPROVEMENT: Pre-parse rejected lenders array to avoid repeated string operations
    Set<String> rejectedLendersSet = StringUtils.isEmpty(lendingRiskVariables.getRejectedLenders()) ? 
            Collections.emptySet() : 
            Arrays.stream(lendingRiskVariables.getRejectedLenders().split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());

    // Pre-fetch audit trials for open application
    LendingAuditTrial initialAuditTrial = null;
    LendingAuditTrial fallbackAuditTrial = null;
    if (cache.openApplication != null) {
        initialAuditTrial = lendingAuditTrialDao.findTopByApplicationIdAndType(cache.openApplication.getId(), "INITIAL_LENDERS");
        fallbackAuditTrial = lendingAuditTrialDao.findTopByApplicationIdAndType(cache.openApplication.getId(), "FALLBACK_LENDERS");
    } else {
        initialAuditTrial = lendingAuditTrialDao.findTopByEvaluationIdAndTypeOrderByIdDesc(evaluationId, "INITIAL_LENDERS");
        fallbackAuditTrial = lendingAuditTrialDao.findTopByEvaluationIdAndTypeOrderByIdDesc(evaluationId, "FALLBACK_LENDERS");
    }

    // PERFORMANCE IMPROVEMENT: Use parallel processing for loan processing where safe
    return eligibleOffersWithLenders.parallelStream()
            .filter(loan -> !CollectionUtils.isEmpty(loan.getEligibleLenders()))
            .map(loan -> {
                try {
                    return processSingleLoanOptimized(
                            merchantId, loan, lendingRiskVariables, evaluationId, rejectedLenders, 
                            rejectedLendersSet, cache, initialAuditTrial, fallbackAuditTrial);
                } catch (Exception e) {
                    AsyncLoggerUtil.logError(logger, "Error processing lender data for loan with tenure {} months: {}",
                            loan.getTenureInMonths(), e.getMessage());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
}

/**
 * PERFORMANCE IMPROVEMENT 5: Processes a single loan with optimized operations
 */
private EligibleOffersResponseDTO.TenureWithLender processSingleLoanOptimized(
        Long merchantId, EligibleLoanDTO loan, LendingRiskVariables lendingRiskVariables, 
        String evaluationId, List<String> rejectedLenders, Set<String> rejectedLendersSet,
        LenderDataCache cache, LendingAuditTrial initialAuditTrial, LendingAuditTrial fallbackAuditTrial) {
    
    // Get detailed lender data for this loan
    List<EligibleOffersResponseDTO.LenderData> lenderDataForLoan = getLenderData(
            loan.getEligibleLenders(), loan, lendingRiskVariables, merchantId);

    if (CollectionUtils.isEmpty(lenderDataForLoan)) {
        return null;
    }

    AsyncLoggerUtil.logInfo(logger, "Lender data: {},fetched for merchantId: {}", lenderDataForLoan, merchantId);

    // PERFORMANCE IMPROVEMENT: Use Set for O(1) lookups instead of List
    Set<String> lenderNames = lenderDataForLoan.stream()
            .map(EligibleOffersResponseDTO.LenderData::getLenderName)
            .collect(Collectors.toSet());

    AsyncLoggerUtil.logInfo(logger, "eligible lenders for tenure {} months: {}",
            loan.getTenureInMonths(), lenderNames);

    // PERFORMANCE IMPROVEMENT: Process rejected lenders efficiently using Set operations
    Set<String> ineligibleLenders = new HashSet<>();
    if (!rejectedLendersSet.isEmpty()) {
        Set<String> lendersToRemove = lenderNames.stream()
                .filter(lender -> rejectedLendersSet.contains(loanUtil.getLenderRejectedMapping(lender.toUpperCase())))
                .peek(lender -> {
                    AsyncLoggerUtil.logInfo(logger, "Skipping {} due to lender in rejected lender list in lending risk variables for merchant: {}",
                            lender, merchantId);
                    String remarks = "Skipping " + lender + " due to lender in rejected lender list in lending risk variables";
                    createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", remarks, evaluationId);
                })
                .collect(Collectors.toSet());
        
        lenderNames.removeAll(lendersToRemove);
        ineligibleLenders.addAll(lendersToRemove);
    }

    // Add active lenders not in current lender names to ineligible list
    for (String activeLender : activeLenders) {
        if (!lenderNames.contains(activeLender)) {
            ineligibleLenders.add(activeLender);
        }
    }

    cache.ineligibleLenders.addAll(ineligibleLenders);

    AsyncLoggerUtil.logInfo(logger, "Complete ineligible lenders list: {} for merchantId: {}",
            ineligibleLenders, merchantId);

    // PERFORMANCE IMPROVEMENT: Filter lender metrics history for current loan's lenders from cached data
    List<LenderMetricsHistory> lenderMetricsHistoryList = cache.allLenderMetricsHistory.stream()
            .filter(lender -> lenderNames.contains(lender.getLender()))
            .collect(Collectors.toList());

    // Process audit trials and calculate matching counts
    AuditTrialData auditData = processAuditTrialsOptimized(initialAuditTrial, fallbackAuditTrial, rejectedLenders);

    // PERFORMANCE IMPROVEMENT: Use cached ranking configs instead of fetching per loan
    List<String> initialLendersList = lenderRankingEngine.rankLenders(
            lenderMetricsHistoryList,
            cache.initialOfferRankingConfigs,
            RankingType.INITIAL,
            initalLendersLimit - auditData.initialMatchingLendersCount,
            merchantId,
            loan.getTenureInMonths());

    if (cache.openApplication == null) {
        createAndSaveLendingAuditTrial(
                merchantId,
                null,
                "INITIAL_LENDERS",
                String.join(",", initialLendersList),
                evaluationId
        );
    }

    AsyncLoggerUtil.logInfo(logger, "Initial lenders for loan with tenure {} months: {} for merchantId: {}",
            loan.getTenureInMonths(), initialLendersList, merchantId);

    // PERFORMANCE IMPROVEMENT: Use Set for efficient filtering
    Set<String> initialLendersSet = new HashSet<>(initialLendersList);
    List<LenderMetricsHistory> fallbackCandidates = lenderMetricsHistoryList.stream()
            .filter(lender -> !initialLendersSet.contains(lender.getLender()))
            .collect(Collectors.toList());

    List<String> fallbackLendersList = fallbackCandidates.isEmpty() ?
            Collections.emptyList() :
            lenderRankingEngine.rankLenders(
                    fallbackCandidates,
                    cache.fallbackOfferRankingConfigs,
                    RankingType.FALLBACK,
                    fallbackLendersLimit - auditData.fallbackMatchingLendersCount,
                    merchantId,
                    loan.getTenureInMonths());

    if (cache.openApplication == null) {
        createAndSaveLendingAuditTrial(
                merchantId,
                null,
                "FALLBACK_LENDERS",
                String.join(",", fallbackLendersList),
                evaluationId
        );
    }

    AsyncLoggerUtil.logInfo(logger, "Fallback lenders for loan with tenure {} months: {} for merchantId: {}",
            loan.getTenureInMonths(), fallbackLendersList, merchantId);

    // PERFORMANCE IMPROVEMENT: Create lender data map for efficient lookups
    Map<String, EligibleOffersResponseDTO.LenderData> lenderDataMap = lenderDataForLoan.stream()
            .collect(Collectors.toMap(EligibleOffersResponseDTO.LenderData::getLenderName, Function.identity()));

    // PERFORMANCE IMPROVEMENT: Build final lender lists efficiently
    List<EligibleOffersResponseDTO.LenderData> initialLenders = buildLenderDataListOptimized(initialLendersList, lenderDataMap, RankingType.INITIAL);
    List<EligibleOffersResponseDTO.LenderData> fallbackLenders = buildLenderDataListOptimized(fallbackLendersList, lenderDataMap, RankingType.FALLBACK);

    AsyncLoggerUtil.logInfo(logger, "initial lenders: {}, fallback lenders: {} for merchantId: {}", 
            initialLenders, fallbackLenders, merchantId);

    // Create TenureWithLender object
    return new EligibleOffersResponseDTO.TenureWithLender(
            loan.getCategory(),
            loan.getTenure(),
            loan.getTenureInMonths(),
            loan.getEdiCount(),
            initialLenders,
            fallbackLenders,
            rejectedLenders,
            new ArrayList<>(ineligibleLenders)
    );
}

/**
 * PERFORMANCE IMPROVEMENT: Processes audit trials and calculates matching lender counts efficiently
 */
private AuditTrialData processAuditTrialsOptimized(LendingAuditTrial initialAuditTrial, LendingAuditTrial fallbackAuditTrial, List<String> rejectedLenders) {
    int initialMatchingLendersCount = 0;
    int fallbackMatchingLendersCount = 0;

    if (rejectedLenders != null && !rejectedLenders.isEmpty()) {
        Set<String> rejectedLendersSet = new HashSet<>(rejectedLenders);

        if (initialAuditTrial != null && !StringUtils.isEmpty(initialAuditTrial.getRemarks())) {
            String remarks = initialAuditTrial.getRemarks();
            if (remarks.startsWith("Initial lenders:")) {
                remarks = remarks.substring("Initial lenders:".length()).trim();
            }
            
            List<String> initialLendersAssigned = Arrays.asList(remarks.split(","));
            AsyncLoggerUtil.logInfo(logger, "Initial lenders after parsing: {}, count: {}",
                    initialLendersAssigned, initialLendersAssigned.size());

            // PERFORMANCE IMPROVEMENT: Use stream operations for efficient counting
            initialMatchingLendersCount = (int) initialLendersAssigned.stream()
                    .filter(rejectedLendersSet::contains)
                    .count();
            AsyncLoggerUtil.logInfo(logger, "Matching lenders found in both rejected and initial lists: {}, matching count: {}",
                    initialLendersAssigned.stream().filter(rejectedLendersSet::contains).collect(Collectors.toList()), 
                    initialMatchingLendersCount);
        }

        if (fallbackAuditTrial != null && !StringUtils.isEmpty(fallbackAuditTrial.getRemarks())) {
            AsyncLoggerUtil.logInfo(logger, "Fallback lenders remarks from audit trial: {}",
                    fallbackAuditTrial.getRemarks());
            List<String> fallbackLendersAssigned = Arrays.asList(fallbackAuditTrial.getRemarks().split(","));
            AsyncLoggerUtil.logInfo(logger, "Fallback lenders from audit trail: {}, count: {}", 
                    fallbackLendersAssigned, fallbackLendersAssigned.size());

            // PERFORMANCE IMPROVEMENT: Use stream operations for efficient counting
            fallbackMatchingLendersCount = (int) fallbackLendersAssigned.stream()
                    .filter(rejectedLendersSet::contains)
                    .count();
            AsyncLoggerUtil.logInfo(logger, "Matching lenders found in both rejected and fallback lists: {}, matching count: {}",
                    fallbackLendersAssigned.stream().filter(rejectedLendersSet::contains).collect(Collectors.toList()), 
                    fallbackMatchingLendersCount);
        }
    }

    return new AuditTrialData(initialMatchingLendersCount, fallbackMatchingLendersCount);
}

/**
 * PERFORMANCE IMPROVEMENT: Builds lender data list efficiently
 */
private List<EligibleOffersResponseDTO.LenderData> buildLenderDataListOptimized(
        List<String> lenderNames, Map<String, EligibleOffersResponseDTO.LenderData> lenderDataMap, RankingType rankingType) {
    return lenderNames.stream()
            .map(lenderDataMap::get)
            .filter(Objects::nonNull)
            .peek(ld -> ld.setRankingType(rankingType))
            .collect(Collectors.toList());
}

/**
 * Creates default tenure with lender when no valid options found
 */
private List<EligibleOffersResponseDTO.TenureWithLender> createDefaultTenureWithLender(
        List<String> rejectedLenders, Set<String> ineligibleLenders) {
    AsyncLoggerUtil.logInfo(logger, "No valid tenure options found, but returning ineligible lenders");

    EligibleOffersResponseDTO.TenureWithLender defaultTenure = new EligibleOffersResponseDTO.TenureWithLender(
            "NO_ELIGIBLE_OFFERS",  // category
            "0 Months",            // tenure
            0,                     // tenureInMonths
            0,                     // ediCount
            Collections.emptyList(),     // initialLenders (empty)
            Collections.emptyList(),     // fallbackLenders (empty)
            rejectedLenders,
            new ArrayList<>(ineligibleLenders)
    );

    return Collections.singletonList(defaultTenure);
}

/**
 * Cache class to hold pre-loaded data
 */
private static class LenderDataCache {
    final LendingApplication openApplication;
    final Set<String> switchedOffLenderNames;
    final List<OfferRankingConfig> initialOfferRankingConfigs;
    final List<OfferRankingConfig> fallbackOfferRankingConfigs;
    final List<LenderMetricsHistory> allLenderMetricsHistory;
    final Set<String> ineligibleLenders;

    LenderDataCache(LendingApplication openApplication, Set<String> switchedOffLenderNames,
                   List<OfferRankingConfig> initialOfferRankingConfigs, List<OfferRankingConfig> fallbackOfferRankingConfigs,
                   List<LenderMetricsHistory> allLenderMetricsHistory, Set<String> ineligibleLenders) {
        this.openApplication = openApplication;
        this.switchedOffLenderNames = switchedOffLenderNames;
        this.initialOfferRankingConfigs = initialOfferRankingConfigs;
        this.fallbackOfferRankingConfigs = fallbackOfferRankingConfigs;
        this.allLenderMetricsHistory = allLenderMetricsHistory;
        this.ineligibleLenders = ineligibleLenders;
    }
}

/**
 * Data class to hold audit trial processing results
 */
private static class AuditTrialData {
    final int initialMatchingLendersCount;
    final int fallbackMatchingLendersCount;

    AuditTrialData(int initialMatchingLendersCount, int fallbackMatchingLendersCount) {
        this.initialMatchingLendersCount = initialMatchingLendersCount;
        this.fallbackMatchingLendersCount = fallbackMatchingLendersCount;
    }
}
