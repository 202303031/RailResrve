package com.railreserve.admin.web.dto;

import com.railreserve.catalog.domain.TrainType;

public record TrainResponse(Long id, String number, String name, TrainType type) {
}
