package com.prearbbot.service;

import com.prearbbot.core.model.RawEvent;
import java.util.List;

public interface EventFetcher {
    List<RawEvent> fetchEvents();
    String getExchangeName();
}