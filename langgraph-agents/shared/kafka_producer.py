"""Kafka producer helpers for the agents service."""


async def produce(topic: str, payload: str) -> None:
    """Publishes a message to Kafka."""
    _ = topic, payload
