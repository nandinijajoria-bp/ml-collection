package starter;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dao.LendingApplicationDao;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;


import java.util.Optional;

import static org.mockito.Mockito.when;

@SpringBootTest
public class StarterTest {

    @MockBean
    LendingApplicationDao lendingApplicationDao;

    @Test
    public void contextLoads() {
    }

    @Test
    public void runTest() {
        when(lendingApplicationDao.findById(123l)).thenReturn(Optional.of(new LendingApplication()));
        assertThat(lendingApplicationDao.findById(123l).equals(new LendingApplication()));
    }


}
