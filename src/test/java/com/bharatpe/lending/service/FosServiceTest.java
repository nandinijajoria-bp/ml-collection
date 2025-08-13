package com.bharatpe.lending.service;

import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.common.entities.LendingApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FosServiceTest {

    @InjectMocks
    private FosService fosService;

    @Mock
    private LendingApplicationDao lendingApplicationDao;
    @Mock
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;
    @Mock
    private EnachHandler enachHandler;
    @Mock
    private DateTimeUtil dateTimeUtil;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        // Set default flags if needed
        fosService.merchantNachEligibleFlag = true;
        fosService.merchantAutoPayEligibleFlag = true;
    }

    // 1. All params valid, NACH eligible, NACH status APPROVED, eNACH in window
    @Test
    void testNachTaskSuccessNewFlow_AllApproved() {
        Map<String, Object> request = getValidRequest();
        LendingApplication app = getLendingApplication("APPROVED", "APPROVED");
        LendingApplicationDetails details = getLendingApplicationDetails(true, true);
        MerchantNachDetailsResponseDTO enach = getEnach(new Date(10000));
        mockCommon(app, details, enach, 10000L, 1000);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertTrue(response.isSuccess());
        assertEquals("nach done for the application", response.getMessage());
        assertEquals(app.getId(), ((Map)response.getData()).get("application_id"));
    }

    // 2. All params valid, NACH eligible, NACH status not APPROVED, eNACH in window
    @Test
    void testNachTaskSuccessNewFlow_NachPending() {
        Map<String, Object> request = getValidRequest();
        LendingApplication app = getLendingApplication("PENDING", "APPROVED");
        LendingApplicationDetails details = getLendingApplicationDetails(true, true);
        MerchantNachDetailsResponseDTO enach = getEnach(new Date(10000));
        mockCommon(app, details, enach, 10000L, 1000);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertFalse(response.isSuccess());
        assertEquals("nach is pending for the application", response.getMessage());
    }

    // 3. All params valid, AutoPay eligible, AutoPay status APPROVED, eNACH in window
    @Test
    void testNachTaskSuccessNewFlow_AutoPayApproved() {
        Map<String, Object> request = getValidRequest();
        LendingApplication app = getLendingApplication("APPROVED", "APPROVED");
        app.setUpiAutopayStatus("APPROVED");
        LendingApplicationDetails details = getLendingApplicationDetails(false, true);
        MerchantNachDetailsResponseDTO enach = getEnach(new Date(10000));
        mockCommon(app, details, enach, 10000L, 1000);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertTrue(response.isSuccess());
        assertEquals("nach done for the application", response.getMessage());
    }

    // 4. All params valid, neither NACH nor AutoPay eligible
    @Test
    void testNachTaskSuccessNewFlow_NeitherEligible() {
        Map<String, Object> request = getValidRequest();
        LendingApplication app = getLendingApplication("APPROVED", "APPROVED");
        LendingApplicationDetails details = getLendingApplicationDetails(false, false);
        MerchantNachDetailsResponseDTO enach = getEnach(new Date(10000));
        mockCommon(app, details, enach, 10000L, 1000);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertTrue(response.isSuccess());
        assertEquals("nach done for the application", response.getMessage());
    }

    // 5. All params valid, both eligible, only NACH status APPROVED
    @Test
    void testNachTaskSuccessNewFlow_BothEligibleOnlyNachApproved() {
        Map<String, Object> request = getValidRequest();
        LendingApplication app = getLendingApplication("APPROVED", "APPROVED");
        app.setUpiAutopayStatus("PENDING");
        LendingApplicationDetails details = getLendingApplicationDetails(true, true);
        MerchantNachDetailsResponseDTO enach = getEnach(new Date(10000));
        mockCommon(app, details, enach, 10000L, 1000);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertFalse(response.isSuccess());
        assertEquals("nach is pending for the application", response.getMessage());
    }

    // 6. All params valid, both eligible, only AutoPay status APPROVED
    @Test
    void testNachTaskSuccessNewFlow_BothEligibleOnlyAutoPayApproved() {
        Map<String, Object> request = getValidRequest();
        LendingApplication app = getLendingApplication("PENDING", "APPROVED");
        app.setUpiAutopayStatus("APPROVED");
        LendingApplicationDetails details = getLendingApplicationDetails(true, true);
        MerchantNachDetailsResponseDTO enach = getEnach(new Date(10000));
        mockCommon(app, details, enach, 10000L, 1000);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertFalse(response.isSuccess());
        assertEquals("nach is pending for the application", response.getMessage());
    }

    // 7. All params valid, both eligible, both statuses APPROVED
    @Test
    void testNachTaskSuccessNewFlow_BothEligibleBothApproved() {
        Map<String, Object> request = getValidRequest();
        LendingApplication app = getLendingApplication("APPROVED", "APPROVED");
        app.setUpiAutopayStatus("APPROVED");
        LendingApplicationDetails details = getLendingApplicationDetails(true, true);
        MerchantNachDetailsResponseDTO enach = getEnach(new Date(10000));
        mockCommon(app, details, enach, 10000L, 1000);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertTrue(response.isSuccess());
        assertEquals("nach done for the application", response.getMessage());
    }

    // 8. eNACH not found
    @Test
    void testNachTaskSuccessNewFlow_EnachNotFound() {
        Map<String, Object> request = getValidRequest();
        LendingApplication app = getLendingApplication("APPROVED", "APPROVED");
        LendingApplicationDetails details = getLendingApplicationDetails(true, true);
        mockCommon(app, details, null, 10000L, 1000);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertFalse(response.isSuccess());
        assertEquals("no application found against this task", response.getMessage());
    }

    // 9. eNACH found, but updatedAt out of window
    @Test
    void testNachTaskSuccessNewFlow_EnachOutOfWindow() {
        Map<String, Object> request = getValidRequest();
        LendingApplication app = getLendingApplication("APPROVED", "APPROVED");
        LendingApplicationDetails details = getLendingApplicationDetails(true, true);
        MerchantNachDetailsResponseDTO enach = getEnach(new Date(1));
        mockCommon(app, details, enach, 10000L, 1000);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertFalse(response.isSuccess());
        assertEquals("no application found against this task", response.getMessage());
    }

    // 10. Lending application not found
    @Test
    void testNachTaskSuccessNewFlow_ApplicationNotFound() {
        Map<String, Object> request = getValidRequest();
        when(lendingApplicationDao.findBymerchantId(anyLong())).thenReturn(null);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertFalse(response.isSuccess());
        assertEquals("Application not found.", response.getMessage());
    }

    // 11. Lending application details not found
    @Test
    void testNachTaskSuccessNewFlow_ApplicationDetailsNotFound() {
        Map<String, Object> request = getValidRequest();
        LendingApplication app = getLendingApplication("APPROVED", "APPROVED");
        when(lendingApplicationDao.findBymerchantId(anyLong())).thenReturn(app);
        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(anyLong())).thenReturn(null);
        MerchantNachDetailsResponseDTO enach = getEnach(new Date(10000));
        mockCommon(app, null, enach, 10000L, 1000);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertTrue(response.isSuccess());
    }

    // 12. Invalid merchantId (0)
    @Test
    void testNachTaskSuccessNewFlow_InvalidMerchantId() {
        Map<String, Object> request = getValidRequest();
        request.put("merchant_id", 0L);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertFalse(response.isSuccess());
        assertEquals("Required parameteers Missing", response.getMessage());
    }

    // 13. Invalid visitTimestamp (0)
    @Test
    void testNachTaskSuccessNewFlow_InvalidVisitTimestamp() {
        Map<String, Object> request = getValidRequest();
        request.put("visit_timestamp", 0L);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertFalse(response.isSuccess());
        assertEquals("Required parameteers Missing", response.getMessage());
    }

    // 14. Invalid timeWindow (0)
    @Test
    void testNachTaskSuccessNewFlow_InvalidTimeWindow() {
        Map<String, Object> request = getValidRequest();
        request.put("time_window", 0);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertFalse(response.isSuccess());
        assertEquals("Required parameteers Missing", response.getMessage());
    }

    // 15. Null refCode
    @Test
    void testNachTaskSuccessNewFlow_NullRefCode() {
        Map<String, Object> request = getValidRequest();
        request.put("ref_code", null);

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertFalse(response.isSuccess());
        assertEquals("Required parameteers Missing", response.getMessage());
    }

    // 16. Exception thrown in try block
    @Test
    void testNachTaskSuccessNewFlow_Exception() {
        Map<String, Object> request = getValidRequest();
        LendingApplication app = getLendingApplication("APPROVED", "APPROVED");
        when(lendingApplicationDao.findBymerchantId(anyLong())).thenReturn(app);
        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(anyLong())).thenThrow(new RuntimeException("DB error"));

        ResponseDTO response = fosService.nachTaskSuccessNewFlow(request);

        assertFalse(response.isSuccess());
        assertEquals("Error occurred while checking nach status", response.getMessage());
    }

    // 17-25. Add more cases for edge conditions, e.g.:
    // - NACH eligible true, AutoPay eligible false, NACH status false
    // - NACH eligible false, AutoPay eligible true, AutoPay status false
    // - Both eligible, both statuses false
    // - NACH eligible true, AutoPay eligible false, NACH status true
    // - NACH eligible false, AutoPay eligible true, AutoPay status true
    // - Both eligible, NACH status true, AutoPay status false
    // - Both eligible, NACH status false, AutoPay status true
    // - Both eligible, both statuses true
    // - Both eligible, both statuses false

    // Helper methods
    private Map<String, Object> getValidRequest() {
        Map<String, Object> req = new HashMap<>();
        req.put("merchant_id", 1L);
        req.put("visit_timestamp", 10000L);
        req.put("time_window", 1000);
        req.put("ref_code", "RCODE");
        return req;
    }

    private LendingApplication getLendingApplication(String nachStatus, String upiAutopayStatus) {
        LendingApplication app = new LendingApplication();
        app.setId(1L);
        app.setMerchantId(1L);
        app.setAgreementAt(new Date(10000));
        app.setNachStatus(nachStatus);
        app.setUpiAutopayStatus(upiAutopayStatus);
        return app;
    }

    private LendingApplicationDetails getLendingApplicationDetails(boolean nachEligible, boolean autoPayUpiEligible) {
        LendingApplicationDetails details = new LendingApplicationDetails();
        details.setApplicationId(1L);
        details.setMandateFlagsToggledOn(any());
        details.setNachEligible(nachEligible);
        details.setAutoPayUpiEligible(autoPayUpiEligible);
        return details;
    }

    private MerchantNachDetailsResponseDTO getEnach(Date updatedAt) {
        MerchantNachDetailsResponseDTO enach = new MerchantNachDetailsResponseDTO();
        enach.setUpdatedAt(updatedAt);
        return enach;
    }

    private void mockCommon(LendingApplication app, LendingApplicationDetails details, MerchantNachDetailsResponseDTO enach, long visitTimestamp, int timeWindow) {
        when(lendingApplicationDao.findBymerchantId(anyLong())).thenReturn(app);
        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(anyLong())).thenReturn(details);
        when(enachHandler.findSuccessEnach(anyLong(), anyLong())).thenReturn(enach);
        when(dateTimeUtil.getDatePlusMinutes(any(Date.class), anyInt())).thenReturn(new Date(visitTimestamp - timeWindow * 1000L));
        when(dateTimeUtil.getEndTimeFromDateTime(any(Date.class))).thenReturn(new Date(visitTimestamp + timeWindow * 1000L));
    }
}
