package com.railreserve.catalog.web;

import com.railreserve.catalog.service.TrainSearchService;
import com.railreserve.catalog.web.dto.TrainSearchResult;
import com.railreserve.common.api.ApiResponse;
import com.railreserve.common.api.PageResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/trains")
@Validated
public class TrainSearchController {

    private final TrainSearchService searchService;

    public TrainSearchController(TrainSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<TrainSearchResult>> search(
            @RequestParam @NotBlank String from,
            @RequestParam @NotBlank String to,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<TrainSearchResult> results = searchService.search(from, to, date, pageable);
        return ApiResponse.ok(PageResponse.from(results));
    }
}
