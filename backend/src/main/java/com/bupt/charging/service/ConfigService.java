package com.bupt.charging.service;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.StationConfig;
import com.bupt.charging.domain.TariffRule;
import com.bupt.charging.dto.ConfigDtos;
import com.bupt.charging.repository.BillRepository;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.repository.ChargingRequestRepository;
import com.bupt.charging.repository.ChargingSessionRepository;
import com.bupt.charging.repository.DetailedListRepository;
import com.bupt.charging.repository.FaultRecordRepository;
import com.bupt.charging.repository.StationClockRepository;
import com.bupt.charging.repository.StationConfigRepository;
import com.bupt.charging.repository.TariffRuleRepository;
import com.bupt.charging.repository.UserAccountRepository;
import com.bupt.charging.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigService {
    private final StationConfigRepository configRepository;
    private final TariffRuleRepository tariffRuleRepository;
    private final ChargingPileRepository pileRepository;
    private final ChargingRequestRepository requestRepository;
    private final ChargingSessionRepository sessionRepository;
    private final DetailedListRepository detailedListRepository;
    private final BillRepository billRepository;
    private final FaultRecordRepository faultRecordRepository;
    private final VehicleRepository vehicleRepository;
    private final UserAccountRepository userAccountRepository;
    private final StationClockRepository stationClockRepository;

    public ConfigService(
            StationConfigRepository configRepository,
            TariffRuleRepository tariffRuleRepository,
            ChargingPileRepository pileRepository,
            ChargingRequestRepository requestRepository,
            ChargingSessionRepository sessionRepository,
            DetailedListRepository detailedListRepository,
            BillRepository billRepository,
            FaultRecordRepository faultRecordRepository,
            VehicleRepository vehicleRepository,
            UserAccountRepository userAccountRepository,
            StationClockRepository stationClockRepository
    ) {
        this.configRepository = configRepository;
        this.tariffRuleRepository = tariffRuleRepository;
        this.pileRepository = pileRepository;
        this.requestRepository = requestRepository;
        this.sessionRepository = sessionRepository;
        this.detailedListRepository = detailedListRepository;
        this.billRepository = billRepository;
        this.faultRecordRepository = faultRecordRepository;
        this.vehicleRepository = vehicleRepository;
        this.userAccountRepository = userAccountRepository;
        this.stationClockRepository = stationClockRepository;
    }

    @Transactional
    public ConfigDtos.ConfigResponse initialize(ConfigDtos.UpdateConfigRequest request) {
        StationConfig config = new StationConfig(
                request.fastPileCount(),
                request.slowPileCount(),
                request.waitingAreaSize(),
                request.queueLength(),
                request.fastPower(),
                request.slowPower()
        );
        pileRepository.deleteAllInBatch();
        pileRepository.flush();
        StationConfig saved = configRepository.save(config);
        if (tariffRuleRepository.findFirstByOrderByIdDesc().isEmpty()) {
            tariffRuleRepository.save(TariffRule.defaults());
        }
        for (int i = 1; i <= request.fastPileCount(); i++) {
            pileRepository.save(new ChargingPile("F-" + i, ChargeMode.FAST, request.fastPower()));
        }
        for (int i = 1; i <= request.slowPileCount(); i++) {
            pileRepository.save(new ChargingPile("T-" + i, ChargeMode.SLOW, request.slowPower()));
        }
        return toResponse(saved);
    }

    @Transactional
    public void resetDemoData() {
        detailedListRepository.deleteAll();
        billRepository.deleteAll();
        sessionRepository.deleteAll();
        requestRepository.deleteAll();
        faultRecordRepository.deleteAll();
        pileRepository.deleteAll();
        vehicleRepository.deleteAll();
        userAccountRepository.deleteAll();
        stationClockRepository.deleteAll();
        tariffRuleRepository.deleteAll();
        configRepository.deleteAll();
    }

    public StationConfig currentConfig() {
        return configRepository.findFirstByOrderByIdDesc().orElseGet(StationConfig::defaults);
    }

    public ConfigDtos.ConfigResponse currentConfigResponse() {
        return toResponse(currentConfig());
    }

    private ConfigDtos.ConfigResponse toResponse(StationConfig config) {
        return new ConfigDtos.ConfigResponse(
                config.getFastPileCount(),
                config.getSlowPileCount(),
                config.getWaitingAreaSize(),
                config.getQueueLength(),
                config.getFastPower(),
                config.getSlowPower()
        );
    }
}
