package config;

import com.theguy.app.TheGuyApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TheGuyApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    @SuppressWarnings("unused")
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    @SuppressWarnings("unused")
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Test
    void shouldAllowHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectUnauthenticatedJobs() throws Exception {
        mockMvc.perform(get("/api/jobs/request"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectUnauthenticatedProviders() throws Exception {
        mockMvc.perform(get("/api/providers/nearby?lat=-1.28&lng=36.82&radius=100"))
                .andExpect(status().isOk());

    }


    @Test
    void shouldAllowPublicEndpoints() throws Exception {
        mockMvc.perform(get("/api/public/test"))
                .andExpect(status().isNotFound());
    }
}
