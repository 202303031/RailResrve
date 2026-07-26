package com.railreserve.booking.lock;

import com.railreserve.booking.exception.SeatUnavailableException;
import com.railreserve.common.exception.ErrorCode;
import com.railreserve.common.exception.ResourceNotFoundException;
import com.railreserve.scheduling.domain.SeatInventory;
import com.railreserve.scheduling.repository.SeatInventoryRepository;
import org.springframework.stereotype.Component;

/**
 * Pessimistic strategy: take a {@code SELECT ... FOR UPDATE} row lock on the inventory before
 * touching it. Concurrent reservers of the same coach queue on the lock and run one at a time,
 * so there is never a conflict to retry -- at the cost of holding the lock for the transaction.
 */
@Component
public class PessimisticSeatLockStrategy implements SeatLockStrategy {

    private final SeatInventoryRepository inventoryRepository;

    public PessimisticSeatLockStrategy(SeatInventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public String name() {
        return "pessimistic";
    }

    @Override
    public void reserve(Long scheduleId, Long coachId, int count) {
        SeatInventory inventory = loadForUpdate(scheduleId, coachId);
        if (inventory.getAvailableCount() < count) {
            throw new SeatUnavailableException("Only " + inventory.getAvailableCount() + " seat(s) left");
        }
        inventory.reserve(count);
        inventoryRepository.save(inventory);
    }

    @Override
    public void release(Long scheduleId, Long coachId, int count) {
        SeatInventory inventory = loadForUpdate(scheduleId, coachId);
        inventory.release(count);
        inventoryRepository.save(inventory);
    }

    private SeatInventory loadForUpdate(Long scheduleId, Long coachId) {
        return inventoryRepository.findForUpdate(scheduleId, coachId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No inventory for schedule " + scheduleId + ", coach " + coachId));
    }
}
