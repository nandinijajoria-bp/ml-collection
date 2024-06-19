package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.loanV3.services.associationsV2.AbflPennyDropServiceV2;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class AbflPennyDropServiceTest {
    @Mock
    LendingApplicationDetailsDao lendingApplicationDetailsDao;
    @Mock
    AbflPennyDropServiceV2 abflPennyDropServiceV2;
    @InjectMocks
    AbflPennyDropService abflPennyDropService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testInvoke(){

        HashMap<String, Object> map = new HashMap<>();
        map.put("application_id", "5756732");

        Optional result = abflPennyDropService.invoke(Long.valueOf(1), map);
        verify(abflPennyDropServiceV2, times(1)).invokePennyDrop(any());
    }

    @Test
    public void testInvoke_exception() throws Exception {

        HashMap<String, Object> map = new HashMap<>();
        map.put("application_id", "5756732");

        when(abflPennyDropService.invoke(Long.valueOf(1), map)).thenThrow(new NullPointerException("exception occured"));

        Optional result = abflPennyDropService.invoke(Long.valueOf(1), map);

        Assert.assertEquals(result, Optional.empty());
    }
}

