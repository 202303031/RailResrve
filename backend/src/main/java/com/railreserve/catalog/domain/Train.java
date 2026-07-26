package com.railreserve.catalog.domain;

import com.railreserve.common.domain.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "train")
public class Train extends AbstractEntity {

    private String number;
    private String name;

    @Enumerated(EnumType.STRING)
    private TrainType type;

    protected Train() {
    }

    public Train(String number, String name, TrainType type) {
        this.number = number;
        this.name = name;
        this.type = type;
    }

    public String getNumber() {
        return number;
    }

    public String getName() {
        return name;
    }

    public TrainType getType() {
        return type;
    }
}
