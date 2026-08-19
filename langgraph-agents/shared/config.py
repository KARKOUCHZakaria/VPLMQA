"""Application settings for the agents service."""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime settings loaded from environment variables."""

    gemini_api_key: str
    gemini_model_code: str = "gemini-2.5-flash"
    gemini_model_text: str = "gemini-2.5-flash"
    azure_openai_endpoint: str
    azure_openai_api_key: str
    azure_openai_deployment: str = "gpt-4o"
    kafka_bootstrap_servers: str = "kafka:9092"
    project_service_url: str = "http://project-service:8088"
    design_service_url: str = "http://design-service:8082"
    e2e_service_url: str = "http://e2e-service:8083"
    ticket_service_url: str = "http://ticket-service:8084"
    scrub_pii_before_llm: bool = True

    model_config = SettingsConfigDict(env_file=".env")
