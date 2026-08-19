package com.vplmqa.e2e.services;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StepFunctionRegistryService {
    private final JdbcTemplate jdbc;

    public StepFunctionRegistryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String status) {
        if (status == null || status.isBlank()) {
            return jdbc.queryForList("""
                    SELECT id,intent_signature,function_name,project_id,version,status,
                    requires_secret,validation_evidence,created_at,updated_at
                    FROM step_function_registry ORDER BY updated_at DESC
                    """);
        }
        return jdbc.queryForList("""
                SELECT id,intent_signature,function_name,project_id,version,status,
                requires_secret,validation_evidence,created_at,updated_at
                FROM step_function_registry WHERE status=? ORDER BY updated_at DESC
                """, status.toUpperCase());
    }

    @Transactional
    public void setStatus(UUID id, String status) {
        String normalized = status.toUpperCase();
        if (!List.of("APPROVED", "REJECTED").contains(normalized)) {
            throw new IllegalArgumentException("Status must be APPROVED or REJECTED");
        }
        int updated = jdbc.update("UPDATE step_function_registry SET status=?,updated_at=NOW() WHERE id=?", normalized, id);
        if (updated == 0) throw new IllegalArgumentException("Step function not found: " + id);
    }
}
