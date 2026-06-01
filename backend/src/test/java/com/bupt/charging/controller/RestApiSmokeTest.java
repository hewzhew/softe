package com.bupt.charging.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:rest-api-smoke-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RestApiSmokeTest {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void configAndAccountEndpointsReturnApiResult() {
        Map<String, Object> config = Map.of(
                "fastPileCount", 2,
                "slowPileCount", 3,
                "waitingAreaSize", 10,
                "queueLength", 2,
                "fastPower", 30.0,
                "slowPower", 10.0
        );

        ResponseEntity<String> configResponse = restTemplate.postForEntity("/api/config", config, String.class);

        assertEquals(HttpStatus.OK, configResponse.getStatusCode());
        assertTrue(configResponse.getBody().contains("\"success\":true"));

        Map<String, Object> account = Map.of(
                "carId", "CAR-REST-1",
                "userName", "Rest User",
                "carCapacity", 80.0
        );
        ResponseEntity<String> accountResponse = restTemplate.postForEntity("/api/accounts", account, String.class);

        assertEquals(HttpStatus.OK, accountResponse.getStatusCode());
        assertTrue(accountResponse.getBody().contains("CAR-REST-1"));
    }
}
