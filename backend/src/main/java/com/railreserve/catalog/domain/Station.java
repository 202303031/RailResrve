package com.railreserve.catalog.domain;

import com.railreserve.common.domain.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "station")
public class Station extends AbstractEntity {

    private String code;
    private String name;
    private String city;

    protected Station() {
    }

    public Station(String code, String name, String city) {
        this.code = code;
        this.name = name;
        this.city = city;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }
}
