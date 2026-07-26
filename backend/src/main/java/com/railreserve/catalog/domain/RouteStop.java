package com.railreserve.catalog.domain;

import com.railreserve.common.domain.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalTime;

@Entity
@Table(name = "route_stop")
public class RouteStop extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "train_id")
    private Train train;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id")
    private Station station;

    private int stopOrder;
    private LocalTime arrivalTime;
    private LocalTime departureTime;
    private int distanceKm;

    protected RouteStop() {
    }

    public RouteStop(Train train, Station station, int stopOrder,
                     LocalTime arrivalTime, LocalTime departureTime, int distanceKm) {
        this.train = train;
        this.station = station;
        this.stopOrder = stopOrder;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.distanceKm = distanceKm;
    }

    public Train getTrain() {
        return train;
    }

    public Station getStation() {
        return station;
    }

    public int getStopOrder() {
        return stopOrder;
    }

    public LocalTime getArrivalTime() {
        return arrivalTime;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public int getDistanceKm() {
        return distanceKm;
    }
}
