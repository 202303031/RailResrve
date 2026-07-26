package com.railreserve.scheduling.domain;

import com.railreserve.common.domain.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "coach")
public class Coach extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    private String coachCode;

    // Persisted via TravelClassConverter (autoApply) as its short code (SL, 3A, ...).
    private TravelClass travelClass;

    private int totalSeats;

    protected Coach() {
    }

    public Coach(Schedule schedule, String coachCode, TravelClass travelClass, int totalSeats) {
        this.schedule = schedule;
        this.coachCode = coachCode;
        this.travelClass = travelClass;
        this.totalSeats = totalSeats;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public String getCoachCode() {
        return coachCode;
    }

    public TravelClass getTravelClass() {
        return travelClass;
    }

    public int getTotalSeats() {
        return totalSeats;
    }
}
