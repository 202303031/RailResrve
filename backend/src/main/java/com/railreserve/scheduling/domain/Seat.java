package com.railreserve.scheduling.domain;

import com.railreserve.common.domain.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "seat")
public class Seat extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id")
    private Coach coach;

    private String seatNumber;

    @Enumerated(EnumType.STRING)
    private BerthType berthType;

    protected Seat() {
    }

    public Seat(Coach coach, String seatNumber, BerthType berthType) {
        this.coach = coach;
        this.seatNumber = seatNumber;
        this.berthType = berthType;
    }

    public Coach getCoach() {
        return coach;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public BerthType getBerthType() {
        return berthType;
    }
}
