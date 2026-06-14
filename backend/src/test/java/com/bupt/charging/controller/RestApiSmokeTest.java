package com.bupt.charging.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void seedCreatesWaitingRequestsAndDispatchMovesThemToPileQueues() throws Exception {
        ResponseEntity<String> seedResponse = restTemplate.postForEntity("/api/demo/seed", null, String.class);

        assertEquals(HttpStatus.OK, seedResponse.getStatusCode());
        JsonNode seedData = objectMapper.readTree(seedResponse.getBody()).path("data");
        assertEquals(4, seedData.path("queues").path("waitingArea").size());
        assertEquals(0, seedData.path("queues").path("pileQueues").size());

        ResponseEntity<String> dispatchResponse = restTemplate.postForEntity("/api/scheduler/dispatch", null, String.class);

        assertEquals(HttpStatus.OK, dispatchResponse.getStatusCode());
        ResponseEntity<String> snapshotResponse = restTemplate.getForEntity("/api/demo/snapshot", String.class);
        JsonNode snapshotData = objectMapper.readTree(snapshotResponse.getBody()).path("data");
        assertTrue(snapshotData.path("queues").path("pileQueues").size() > 0);
    }

    @Test
    void acceptanceScenarioEndpointReturnsTeacherCaseRows() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/acceptance/scenario", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertEquals(36, data.path("rows").size());
        assertEquals("06:00", data.path("rows").get(0).path("time").asText());
        assertTrue(data.path("sampleChecks").get(0).path("matched").asBoolean());
    }

    @Test
    void courseScenarioReplayEndpointReturnsPlaybackBundle() throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/scenarios/course-sample/run",
                null,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertEquals("course-sample", data.path("scenario").path("id").asText());
        assertEquals(36, data.path("commands").size());
        assertEquals(37, data.path("snapshots").size());
        assertEquals(36, data.path("transitions").size());
        assertEquals(36, data.path("tableRows").size());
        JsonNode checks = data.path("checks");
        assertEquals(6, checks.size());
        checks.forEach(check -> assertTrue(check.path("passed").asBoolean()));
    }

    @Test
    void stationSnapshotEndpointProjectsCurrentBusinessData() throws Exception {
        ResponseEntity<String> seedResponse = restTemplate.postForEntity("/api/demo/seed", null, String.class);
        assertEquals(HttpStatus.OK, seedResponse.getStatusCode());

        ResponseEntity<String> response = restTemplate.getForEntity("/api/station/snapshot", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertTrue(data.path("station").path("waitingArea").size() > 0);
        assertTrue(data.path("station").has("fastPiles"));
        assertTrue(data.path("station").has("slowPiles"));
        assertTrue(data.path("vehicles").has("CAR-F-1"));
    }

    @Test
    void stationSnapshotIncludesChargingVehicleInPileProjection() throws Exception {
        ResponseEntity<String> seedResponse = restTemplate.postForEntity("/api/demo/seed", null, String.class);
        assertEquals(HttpStatus.OK, seedResponse.getStatusCode());
        ResponseEntity<String> dispatchResponse = restTemplate.postForEntity(
                "/api/scheduler/dispatch",
                null,
                String.class
        );
        assertEquals(HttpStatus.OK, dispatchResponse.getStatusCode());
        ResponseEntity<String> carStateResponse = restTemplate.getForEntity(
                "/api/charging/cars/CAR-F-1/state",
                String.class
        );
        assertEquals(HttpStatus.OK, carStateResponse.getStatusCode());
        String assignedPileId = objectMapper.readTree(carStateResponse.getBody())
                .path("data")
                .path("assignedPileId")
                .asText();

        ResponseEntity<String> startResponse = restTemplate.postForEntity(
                "/api/charging/CAR-F-1/start",
                Map.of("pileId", assignedPileId),
                String.class
        );
        assertEquals(HttpStatus.OK, startResponse.getStatusCode());

        ResponseEntity<String> response = restTemplate.getForEntity("/api/station/snapshot", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        JsonNode chargingVehicle = data.path("vehicles").path("CAR-F-1");
        assertEquals("CHARGING", chargingVehicle.path("state").asText());
        assertEquals(assignedPileId, chargingVehicle.path("position").asText());
        assertEquals(4, data.path("metrics").path("pileQueueCount").asInt());

        JsonNode fastPiles = data.path("station").path("fastPiles");
        boolean chargingVehicleInPileQueue = false;
        for (JsonNode pile : fastPiles) {
            if (assignedPileId.equals(pile.path("id").asText())) {
                for (JsonNode vehicleId : pile.path("queue")) {
                    chargingVehicleInPileQueue = chargingVehicleInPileQueue || "CAR-F-1".equals(vehicleId.asText());
                }
            }
        }
        assertTrue(chargingVehicleInPileQueue);
    }
}
