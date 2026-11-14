package com.example.justgo.backend.spot;

import com.example.justgo.backend.integration.place.TodayEventSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TodayEventController {
    private final TodayEventSource source;

    public TodayEventController(TodayEventSource source) {
        this.source = source;
    }

    @GetMapping("/api/v1/events/today")
    public TodayEventSource.Result findTodayEvents() {
        return source.today();
    }
}
