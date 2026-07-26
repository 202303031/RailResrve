package com.railreserve.scheduling.domain;

import com.railreserve.catalog.domain.Train;
import com.railreserve.common.domain.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "schedule")
public class Schedule extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "train_id")
    private Train train;

    private LocalDate journeyDate;

    @Enumerated(EnumType.STRING)
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;

    protected Schedule() {
    }

    public Schedule(Train train, LocalDate journeyDate) {
        this.train = train;
        this.journeyDate = journeyDate;
        this.status = ScheduleStatus.SCHEDULED;
    }

    public Train getTrain() {
        return train;
    }

    public LocalDate getJourneyDate() {
        return journeyDate;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    public void setStatus(ScheduleStatus status) {
        this.status = status;
    }
}
