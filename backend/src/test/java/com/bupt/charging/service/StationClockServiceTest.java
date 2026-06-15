package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.support.TimeProvider;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:station-clock-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class StationClockServiceTest {
    @Autowired
    private StationClockService stationClockService;

    @Autowired
    private MutableTimeProvider timeProvider;

    @Test
    void pausedClockKeepsManualStationTime() {
        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 0));

        RuntimeDtos.ClockResponse clock = stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 0),
                10.0,
                false,
                LocalDateTime.of(2026, 6, 15, 6, 0),
                LocalDateTime.of(2026, 6, 15, 9, 30)
        ));

        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 5));

        RuntimeDtos.ClockResponse current = stationClockService.currentClock();
        assertFalse(current.running());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 0), current.currentTime());
        assertEquals(10.0, current.rate());
    }

    @Test
    void runningClockAdvancesByRate() {
        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 0));

        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 0),
                10.0,
                true,
                null,
                null
        ));

        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 1));

        RuntimeDtos.ClockResponse current = stationClockService.currentClock();
        assertTrue(current.running());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 10), current.currentTime());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        MutableTimeProvider mutableTimeProvider() {
            return new MutableTimeProvider();
        }
    }

    static class MutableTimeProvider implements TimeProvider {
        private LocalDateTime now = LocalDateTime.of(2026, 6, 15, 0, 0);

        void setNow(LocalDateTime now) {
            this.now = now;
        }

        @Override
        public LocalDateTime now() {
            return now;
        }
    }
}
