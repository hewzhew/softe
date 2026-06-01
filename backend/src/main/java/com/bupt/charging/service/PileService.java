package com.bupt.charging.service;

import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.dto.PileDtos;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.support.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PileService {
    private final ChargingPileRepository pileRepository;

    public PileService(ChargingPileRepository pileRepository) {
        this.pileRepository = pileRepository;
    }

    public List<PileDtos.PileStateResponse> findAll() {
        return pileRepository.findAll().stream().map(this::toResponse).toList();
    }

    public ChargingPile requirePile(String pileId) {
        return pileRepository.findByPileId(pileId)
                .orElseThrow(() -> new BusinessException("pile not found"));
    }

    @Transactional
    public PileDtos.PileStateResponse powerOn(String pileId) {
        ChargingPile pile = requirePile(pileId);
        pile.powerOn();
        return toResponse(pileRepository.save(pile));
    }

    @Transactional
    public PileDtos.PileStateResponse startPile(String pileId) {
        ChargingPile pile = requirePile(pileId);
        pile.startPile();
        return toResponse(pileRepository.save(pile));
    }

    @Transactional
    public PileDtos.PileStateResponse powerOff(String pileId) {
        ChargingPile pile = requirePile(pileId);
        pile.powerOff();
        return toResponse(pileRepository.save(pile));
    }

    public PileDtos.PileStateResponse toResponse(ChargingPile pile) {
        return new PileDtos.PileStateResponse(
                pile.getPileId(),
                pile.getMode(),
                pile.getPower(),
                pile.getStatus(),
                pile.getTotalChargeNum(),
                pile.getTotalChargeTime(),
                pile.getTotalCapacity(),
                pile.getCurrentCarId()
        );
    }
}
