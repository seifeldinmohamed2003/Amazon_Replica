package com.team27.amazon.user;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires running Postgres, RabbitMQ, Redis, and MongoDB — skipped in CI")
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
