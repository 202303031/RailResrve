package com.railreserve.booking.lock;

import com.railreserve.booking.exception.SeatUnavailableException;
import com.railreserve.common.exception.ErrorCode;
import com.railreserve.common.exception.ResourceNotFoundException;
import com.railreserve.scheduling.domain.SeatInventory;
import com.railreserve.scheduling.repository.SeatInventoryRepository;
import org.springframework.stereotype.Component;

/**
 * Optimistic strategy: read the inventory row, decrement it, and rely on the {@code @Version}
 * column to detect a concurrent change at flush time. No row is locked while a user thinks.
 * When two threads collide, one flush raises {@code OptimisticLockingFailureException} and the
 * booking service retries the whole hold (bounded).
 */
@Component
public class OptimisticSeatLockStrategy implements SeatLockStrategy {

    private final SeatInventoryRepository inventoryRepository;

    public OptimisticSeatLockStrategy(SeatInventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public String name() {
        return "optimistic";
    }

    @Override
    public void reserve(Long scheduleId, Long coachId, int count) {
        SeatInventory inventory = load(scheduleId, coachId);
        if (inventory.getAvailableCount() < count) {
            throw new SeatUnavailableException("Only " + inventory.getAvailableCount() + " seat(s) left");
        }
        inventory.reserve(count);
        // Flush immediately so the version check (and the DB CHECK available_count >= 0)
        // fire here; a stale version raises OptimisticLockingFailureException to the caller.
        inventoryRepository.saveAndFlush(inventory);
    }

    @Override
    public void release(Long scheduleId, Long coachId, int count) {
        SeatInventory inventory = load(scheduleId, coachId);
        inventory.release(count);
        inventoryRepository.saveAndFlush(inventory);
    }

    private SeatInventory load(Long scheduleId, Long coachId) {
        return inventoryRepository.findByScheduleIdAndCoachId(scheduleId, coachId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No inventory for schedule " + scheduleId + ", coach " + coachId));
    }
}
