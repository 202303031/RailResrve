package com.railreserve.scheduling.domain;

import com.railreserve.common.domain.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Aggregate availability counter for one (schedule, coach). The {@link Version} field
 * powers the optimistic-locking strategy in Phase 4; the DB CHECK (available_count &gt;= 0)
 * is the structural backstop that makes overselling impossible even if code is wrong.
 */
@Entity
@Table(name = "seat_inventory")
public class SeatInventory extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id")
    private Coach coach;

    private int availableCount;
    private int bookedCount;

    @Version
    private long version;

    protected SeatInventory() {
    }

    public SeatInventory(Schedule schedule, Coach coach, int availableCount) {
        this.schedule = schedule;
        this.coach = coach;
        this.availableCount = availableCount;
        this.bookedCount = 0;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public Coach getCoach() {
        return coach;
    }

    public int getAvailableCount() {
        return availableCount;
    }

    public int getBookedCount() {
        return bookedCount;
    }

    public long getVersion() {
        return version;
    }

    /** Reserve {@code count} seats. Guarded here and by the DB CHECK (available_count &gt;= 0). */
    public void reserve(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        if (availableCount < count) {
            throw new IllegalStateException("Not enough seats available in inventory " + getId());
        }
        this.availableCount -= count;
        this.bookedCount += count;
    }

    /** Return {@code count} previously-reserved seats to availability. */
    public void release(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        this.availableCount += count;
        this.bookedCount = Math.max(0, this.bookedCount - count);
    }
}
