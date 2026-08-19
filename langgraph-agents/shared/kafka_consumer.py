"""Kafka consumer helpers for the agents service."""

from typing import AsyncIterator


async def consume() -> AsyncIterator[str]:
    """Yields consumed Kafka messages."""
    if False:
        yield ""
