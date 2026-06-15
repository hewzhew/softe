package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bupt.charging.domain.StationClock;
import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.repository.StationClockRepository;
import com.bupt.charging.support.TimeProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
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

    @Autowired
    private StationClockRepository clockRepository;

    @BeforeEach
    void resetClock() {
        clockRepository.deleteAll();
        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 0, 0));
        timeProvider.setAdvanceAfterRead(Duration.ZERO);
        timeProvider.resetReadCount();
    }

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

    @Test
    void currentClockCreatesSingleFixedClockRow() {
        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 0));

        stationClockService.currentClock();
        stationClockService.currentClock();

        assertEquals(1, clockRepository.count());
        assertTrue(clockRepository.findById(StationClock.SINGLETON_ID).isPresent());
    }

    @Test
    void partialSetClockPreservesExistingClockState() {
        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 0));
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 0),
                10.0,
                true,
                LocalDateTime.of(2026, 6, 15, 6, 0),
                LocalDateTime.of(2026, 6, 15, 9, 30)
        ));

        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 1));
        RuntimeDtos.ClockResponse updated = stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                null,
                20.0,
                null,
                null,
                null
        ));

        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 10), updated.currentTime());
        assertEquals(20.0, updated.rate());
        assertTrue(updated.running());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 0), updated.windowStart());
        assertEquals(LocalDateTime.of(2026, 6, 15, 9, 30), updated.windowEnd());
    }

    @Test
    void playAndPausePreserveWindowMetadataAndContinuity() {
        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 0));
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 0),
                10.0,
                false,
                LocalDateTime.of(2026, 6, 15, 6, 0),
                LocalDateTime.of(2026, 6, 15, 9, 30)
        ));

        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 5));
        RuntimeDtos.ClockResponse playing = stationClockService.play();
        assertTrue(playing.running());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 0), playing.currentTime());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 0), playing.windowStart());
        assertEquals(LocalDateTime.of(2026, 6, 15, 9, 30), playing.windowEnd());

        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 6));
        RuntimeDtos.ClockResponse paused = stationClockService.pause();
        assertFalse(paused.running());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 10), paused.currentTime());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 0), paused.windowStart());
        assertEquals(LocalDateTime.of(2026, 6, 15, 9, 30), paused.windowEnd());

        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 10));
        RuntimeDtos.ClockResponse current = stationClockService.currentClock();
        assertFalse(current.running());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 10), current.currentTime());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 0), current.windowStart());
        assertEquals(LocalDateTime.of(2026, 6, 15, 9, 30), current.windowEnd());
    }

    @Test
    void pauseUsesSingleWallClockSampleForStationTime() {
        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 0));
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 0),
                60.0,
                true,
                null,
                null
        ));

        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 0, 1));
        timeProvider.resetReadCount();
        timeProvider.setAdvanceAfterRead(Duration.ofSeconds(1));

        RuntimeDtos.ClockResponse paused = stationClockService.pause();

        assertFalse(paused.running());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 1), paused.currentTime());
        assertEquals(1, timeProvider.readCount());
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
        private Duration advanceAfterRead = Duration.ZERO;
        private int readCount;

        void setNow(LocalDateTime now) {
            this.now = now;
        }

        void setAdvanceAfterRead(Duration advanceAfterRead) {
            this.advanceAfterRead = advanceAfterRead;
        }

        void resetReadCount() {
            this.readCount = 0;
        }

        int readCount() {
            return readCount;
        }

        @Override
        public LocalDateTime now() {
            readCount++;
            LocalDateTime current = now;
            now = now.plus(advanceAfterRead);
            return current;
        }
    }
}
