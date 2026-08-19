#!/bin/bash
TOPICS=(
  "mismatch.detected:1:1"
  "token.validated:1:1"
  "test.started:1:1"
  "test.failed:1:1"
  "test.passed:1:1"
  "ticket.created:1:1"
  "validation.required:1:1"
  "config.refresh:1:1"
  "project.created:1:1"
  "invitation.created:1:1"
)
for TOPIC_DEF in "${TOPICS[@]}"; do
  IFS=':' read -r NAME PARTITIONS REPLICATION <<< "$TOPIC_DEF"
  kafka-topics.sh --create --if-not-exists \
    --bootstrap-server kafka:9092 \
    --topic "$NAME" \
    --partitions "$PARTITIONS" \
    --replication-factor "$REPLICATION"
done
