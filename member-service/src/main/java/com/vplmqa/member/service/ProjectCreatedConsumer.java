package com.vplmqa.member.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vplmqa.member.event.ProjectCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for project.created events.
 */
@Component
public class ProjectCreatedConsumer {

    private final ProjectMemberService projectMemberService;
    private final ObjectMapper objectMapper;

    public ProjectCreatedConsumer(ProjectMemberService projectMemberService, ObjectMapper objectMapper) {
        this.projectMemberService = projectMemberService;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles a project.created event.
     *
     * @param payload event payload
     */
    @KafkaListener(topics = "project.created", groupId = "member-service")
    public void onMessage(String payload) throws Exception {
        ProjectCreatedEvent event = objectMapper.readValue(payload, ProjectCreatedEvent.class);
        projectMemberService.addProjectCreator(event);
    }
}
