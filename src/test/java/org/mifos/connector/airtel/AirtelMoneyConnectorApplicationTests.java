package org.mifos.connector.airtel;

import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for the AirtelMoneyConnectorApplication class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamelSpringBootTest
@EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)
@Import(InMemoryRedisTestConfig.class)
@TestPropertySource(properties = {
    "camel.server-port=0",
    "camel.springboot.main-run-controller=false",
    "management.health.redis.enabled=false"
})
public class AirtelMoneyConnectorApplicationTests {

    @Test
    void contextLoads() {
    }

}
