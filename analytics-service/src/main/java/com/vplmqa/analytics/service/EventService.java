package com.vplmqa.analytics.service;

import com.vplmqa.analytics.entity.Event;
import com.vplmqa.analytics.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public Event recordEvent(String type, UUID projectId, Object payload) {
        Event event = new Event();
        event.setEventType(type);
        event.setProjectId(projectId);
        event.setPayload(payload instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of("value", payload));
        event.setOccurredAt(Instant.now());
        return eventRepository.save(event);
    }
}
