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
import org.springframework.boot.test.web.server.LocalServerPort;
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

    @LocalServerPort
    private int port;

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
    void authLoginAndMeReturnRoleSession() throws Exception {
        ResponseEntity<String> accountResponse = restTemplate.postForEntity(
                "/api/accounts",
                Map.of("carId", "CAR-LOGIN-API", "userName", "Login API", "carCapacity", 80.0),
                String.class
        );
        assertEquals(HttpStatus.OK, accountResponse.getStatusCode());
        ResponseEntity<String> passwordResponse = restTemplate.postForEntity(
                "/api/accounts/CAR-LOGIN-API/password",
                Map.of("password", "secret"),
                String.class
        );
        assertEquals(HttpStatus.OK, passwordResponse.getStatusCode());

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/auth/login",
                Map.of("loginName", "CAR-LOGIN-API", "password", "secret"),
                String.class
        );

        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        JsonNode loginData = objectMapper.readTree(loginResponse.getBody()).path("data");
        assertEquals("OWNER", loginData.path("role").asText());
        assertEquals("CAR-LOGIN-API", loginData.path("carId").asText());

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("X-Session-Token", loginData.path("token").asText())
                .GET()
                .build();
        java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonNode meData = objectMapper.readTree(response.body()).path("data");
        assertEquals("OWNER", meData.path("role").asText());
        assertEquals("CAR-LOGIN-API", meData.path("carId").asText());
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
    void dispatchOneMovesOnlyOneWaitingVehicle() throws Exception {
        ResponseEntity<String> seedResponse = restTemplate.postForEntity("/api/demo/seed", null, String.class);
        assertEquals(HttpStatus.OK, seedResponse.getStatusCode());

        ResponseEntity<String> dispatchResponse = restTemplate.postForEntity(
                "/api/scheduler/dispatch-one",
                Map.of("mode", "FAST"),
                String.class
        );

        assertEquals(HttpStatus.OK, dispatchResponse.getStatusCode());
        JsonNode assignment = objectMapper.readTree(dispatchResponse.getBody()).path("data");
        assertEquals("CAR-F-1", assignment.path("carId").asText());
        ResponseEntity<String> snapshotResponse = restTemplate.getForEntity("/api/demo/snapshot", String.class);
        JsonNode snapshotData = objectMapper.readTree(snapshotResponse.getBody()).path("data");
        assertEquals(1, snapshotData.path("queues").path("pileQueues").size());
        assertEquals(3, snapshotData.path("queues").path("waitingArea").size());
    }

    @Test
    void dispatchOneCanMoveSpecifiedWaitingVehicle() throws Exception {
        ResponseEntity<String> seedResponse = restTemplate.postForEntity("/api/demo/seed", null, String.class);
        assertEquals(HttpStatus.OK, seedResponse.getStatusCode());

        ResponseEntity<String> dispatchResponse = restTemplate.postForEntity(
                "/api/scheduler/dispatch-one",
                Map.of("carId", "CAR-S-1"),
                String.class
        );

        assertEquals(HttpStatus.OK, dispatchResponse.getStatusCode());
        JsonNode assignment = objectMapper.readTree(dispatchResponse.getBody()).path("data");
        assertEquals("CAR-S-1", assignment.path("carId").asText());
        assertTrue(assignment.path("pileId").asText().startsWith("T-"));
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
        assertEquals("SIMULATION", data.path("session").path("mode").asText());
        assertEquals("COURSE_SEQUENCE", data.path("sourceSummary").path("primarySourceType").asText());
        assertEquals("PRECOMPUTED", data.path("session").path("materializationPolicy").asText());
        assertEquals("COURSE_SEQUENCE", data.path("commands").get(0).path("sourceType").asText());
        assertEquals("PROVISIONAL", data.path("commands").get(0).path("commitState").asText());
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
        assertEquals("LIVE", data.path("sessionMode").asText());
        assertEquals("LIVE_MANUAL", data.path("sourceSummary").path("primarySourceType").asText());
        assertTrue(data.path("metrics").path("pileQueueCount").asInt() > 0);
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

    @Test
    void stationSnapshotActivePileCountExcludesFaultPiles() throws Exception {
        ResponseEntity<String> seedResponse = restTemplate.postForEntity("/api/demo/seed", null, String.class);
        assertEquals(HttpStatus.OK, seedResponse.getStatusCode());

        ResponseEntity<String> faultResponse = restTemplate.postForEntity(
                "/api/faults",
                Map.of("pileId", "F-1", "strategy", "PRIORITY"),
                String.class
        );
        assertEquals(HttpStatus.OK, faultResponse.getStatusCode());

        ResponseEntity<String> response = restTemplate.getForEntity("/api/station/snapshot", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode metrics = objectMapper.readTree(response.getBody()).path("data").path("metrics");
        assertEquals(1, metrics.path("faultPileCount").asInt());
        assertEquals(4, metrics.path("activePileCount").asInt());
    }

    @Test
    void stationClockPatchDoesNotSkipRuntimeCursorForSnapshotAdvance() throws Exception {
        ResponseEntity<String> resetResponse = restTemplate.postForEntity("/api/demo/reset", null, String.class);
        assertEquals(HttpStatus.OK, resetResponse.getStatusCode());
        ResponseEntity<String> configResponse = restTemplate.postForEntity(
                "/api/config",
                Map.of(
                        "fastPileCount", 1,
                        "slowPileCount", 0,
                        "waitingAreaSize", 10,
                        "queueLength", 2,
                        "fastPower", 30.0,
                        "slowPower", 10.0
                ),
                String.class
        );
        assertEquals(HttpStatus.OK, configResponse.getStatusCode());
        ResponseEntity<String> clockAtStart = patchForString(
                "/api/station/clock",
                Map.of(
                        "currentTime", "2026-06-15T06:00:00",
                        "rate", 1.0,
                        "running", false
                )
        );
        assertEquals(HttpStatus.OK, clockAtStart.getStatusCode());
        ResponseEntity<String> accountResponse = restTemplate.postForEntity(
                "/api/accounts",
                Map.of("carId", "CAR-PATCH", "userName", "Patch User", "carCapacity", 80.0),
                String.class
        );
        assertEquals(HttpStatus.OK, accountResponse.getStatusCode());
        ResponseEntity<String> requestResponse = restTemplate.postForEntity(
                "/api/charging/requests",
                Map.of("carId", "CAR-PATCH", "requestAmount", 30.0, "mode", "FAST"),
                String.class
        );
        assertEquals(HttpStatus.OK, requestResponse.getStatusCode());
        ResponseEntity<String> dispatchResponse = restTemplate.postForEntity("/api/scheduler/dispatch", null, String.class);
        assertEquals(HttpStatus.OK, dispatchResponse.getStatusCode());

        ResponseEntity<String> clockAtTarget = patchForString(
                "/api/station/clock",
                Map.of(
                        "currentTime", "2026-06-15T07:01:00",
                        "rate", 1.0,
                        "running", false
                )
        );
        assertEquals(HttpStatus.OK, clockAtTarget.getStatusCode());
        ResponseEntity<String> response = restTemplate.getForEntity("/api/station/snapshot", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ResponseEntity<String> carStateResponse = restTemplate.getForEntity(
                "/api/charging/cars/CAR-PATCH/state",
                String.class
        );
        assertEquals(HttpStatus.OK, carStateResponse.getStatusCode());
        assertEquals("FINISHED", objectMapper.readTree(carStateResponse.getBody())
                .path("data")
                .path("carState")
                .asText());
        ResponseEntity<String> billResponse = restTemplate.getForEntity(
                "/api/bills?carId=CAR-PATCH&date=2026-06-15",
                String.class
        );
        assertEquals(HttpStatus.OK, billResponse.getStatusCode());
        assertTrue(objectMapper.readTree(billResponse.getBody()).path("data").size() > 0);
    }

    @Test
    void requestSubmittedAfterClockPatchIsNotBackdatedBySnapshotAdvance() throws Exception {
        ResponseEntity<String> resetResponse = restTemplate.postForEntity("/api/demo/reset", null, String.class);
        assertEquals(HttpStatus.OK, resetResponse.getStatusCode());
        ResponseEntity<String> configResponse = restTemplate.postForEntity(
                "/api/config",
                Map.of(
                        "fastPileCount", 1,
                        "slowPileCount", 0,
                        "waitingAreaSize", 10,
                        "queueLength", 2,
                        "fastPower", 30.0,
                        "slowPower", 10.0
                ),
                String.class
        );
        assertEquals(HttpStatus.OK, configResponse.getStatusCode());
        assertEquals(HttpStatus.OK, patchForString(
                "/api/station/clock",
                Map.of("currentTime", "2026-06-15T06:00:00", "rate", 1.0, "running", false)
        ).getStatusCode());
        assertEquals(HttpStatus.OK, patchForString(
                "/api/station/clock",
                Map.of("currentTime", "2026-06-15T07:00:00", "rate", 1.0, "running", false)
        ).getStatusCode());
        ResponseEntity<String> accountResponse = restTemplate.postForEntity(
                "/api/accounts",
                Map.of("carId", "CAR-LATE-API", "userName", "Late API", "carCapacity", 80.0),
                String.class
        );
        assertEquals(HttpStatus.OK, accountResponse.getStatusCode());
        ResponseEntity<String> requestResponse = restTemplate.postForEntity(
                "/api/charging/requests",
                Map.of("carId", "CAR-LATE-API", "requestAmount", 30.0, "mode", "FAST"),
                String.class
        );
        assertEquals(HttpStatus.OK, requestResponse.getStatusCode());
        assertEquals(HttpStatus.OK, restTemplate.postForEntity("/api/scheduler/dispatch", null, String.class)
                .getStatusCode());
        assertEquals(HttpStatus.OK, patchForString(
                "/api/station/clock",
                Map.of("currentTime", "2026-06-15T07:30:00", "rate", 1.0, "running", false)
        ).getStatusCode());

        ResponseEntity<String> snapshotResponse = restTemplate.getForEntity("/api/station/snapshot", String.class);
        ResponseEntity<String> carStateResponse = restTemplate.getForEntity(
                "/api/charging/cars/CAR-LATE-API/state",
                String.class
        );
        ResponseEntity<String> billResponse = restTemplate.getForEntity(
                "/api/bills?carId=CAR-LATE-API&date=2026-06-15",
                String.class
        );

        assertEquals(HttpStatus.OK, snapshotResponse.getStatusCode());
        assertEquals(HttpStatus.OK, carStateResponse.getStatusCode());
        assertEquals("CHARGING", objectMapper.readTree(carStateResponse.getBody())
                .path("data")
                .path("carState")
                .asText());
        assertEquals(HttpStatus.OK, billResponse.getStatusCode());
        assertEquals(0, objectMapper.readTree(billResponse.getBody()).path("data").size());
    }

    @Test
    void startEndpointIsIdempotentAfterRuntimeAdvanceStartsQueuedHead() throws Exception {
        ResponseEntity<String> resetResponse = restTemplate.postForEntity("/api/demo/reset", null, String.class);
        assertEquals(HttpStatus.OK, resetResponse.getStatusCode());
        ResponseEntity<String> configResponse = restTemplate.postForEntity(
                "/api/config",
                Map.of(
                        "fastPileCount", 1,
                        "slowPileCount", 0,
                        "waitingAreaSize", 10,
                        "queueLength", 2,
                        "fastPower", 30.0,
                        "slowPower", 10.0
                ),
                String.class
        );
        assertEquals(HttpStatus.OK, configResponse.getStatusCode());
        assertEquals(HttpStatus.OK, patchForString(
                "/api/station/clock",
                Map.of("currentTime", "2026-06-15T06:00:00", "rate", 1.0, "running", false)
        ).getStatusCode());
        assertEquals(HttpStatus.OK, restTemplate.postForEntity(
                "/api/accounts",
                Map.of("carId", "CAR-START-1", "userName", "Start 1", "carCapacity", 80.0),
                String.class
        ).getStatusCode());
        assertEquals(HttpStatus.OK, restTemplate.postForEntity(
                "/api/accounts",
                Map.of("carId", "CAR-START-2", "userName", "Start 2", "carCapacity", 80.0),
                String.class
        ).getStatusCode());
        assertEquals(HttpStatus.OK, restTemplate.postForEntity(
                "/api/charging/requests",
                Map.of("carId", "CAR-START-1", "requestAmount", 30.0, "mode", "FAST"),
                String.class
        ).getStatusCode());
        assertEquals(HttpStatus.OK, restTemplate.postForEntity(
                "/api/charging/requests",
                Map.of("carId", "CAR-START-2", "requestAmount", 15.0, "mode", "FAST"),
                String.class
        ).getStatusCode());
        assertEquals(HttpStatus.OK, restTemplate.postForEntity("/api/scheduler/dispatch", null, String.class)
                .getStatusCode());
        assertEquals(HttpStatus.OK, restTemplate.postForEntity(
                "/api/charging/CAR-START-1/start",
                Map.of("pileId", "F-1"),
                String.class
        ).getStatusCode());
        assertEquals(HttpStatus.OK, patchForString(
                "/api/station/clock",
                Map.of("currentTime", "2026-06-15T07:01:00", "rate", 1.0, "running", false)
        ).getStatusCode());

        ResponseEntity<String> startResponse = restTemplate.postForEntity(
                "/api/charging/CAR-START-2/start",
                Map.of("pileId", "F-1"),
                String.class
        );

        assertEquals(HttpStatus.OK, startResponse.getStatusCode());
        ResponseEntity<String> carStateResponse = restTemplate.getForEntity(
                "/api/charging/cars/CAR-START-2/state",
                String.class
        );
        assertEquals("CHARGING", objectMapper.readTree(carStateResponse.getBody())
                .path("data")
                .path("carState")
                .asText());
    }

    private ResponseEntity<String> patchForString(String path, Map<String, Object> body) throws Exception {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method("PATCH", java.net.http.HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        return new ResponseEntity<>(response.body(), HttpStatus.valueOf(response.statusCode()));
    }
}
