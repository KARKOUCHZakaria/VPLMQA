import os

services = {
    "auth-service": {"port": 8081, "db": "auth_db"},
    "design-service": {"port": 8082, "db": "design_db"},
    "e2e-service": {"port": 8083, "db": "e2e_db"},
    "ticket-service": {"port": 8084, "db": "ticket_db"},
    "notification-service": {"port": 8085, "db": "notification_db"},
    "analytics-service": {"port": 8086, "db": "analytics_db"},
    "project-service": {"port": 8088, "db": "project_db"},
    "member-service": {"port": 8089, "db": "member_db"}
}

template = """server:
  port: ${{SERVER_PORT:{port}}}

spring:
  application:
    name: {service}
  config:
    import: ${{SPRING_CONFIG_IMPORT:optional:configserver:http://localhost:8087}}
  datasource:
    url: jdbc:postgresql://${{DB_HOST:localhost}}:${{DB_PORT:5432}}/${{DB_NAME:{db}}}
    username: ${{DB_USER:vplmqa}}
    password: ${{DB_PASSWORD:vplmqa_db_pass}}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
  kafka:
    bootstrap-servers: ${{KAFKA_BOOTSTRAP_SERVERS:localhost:9092}}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: ${{spring.application.name}}
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer

eureka:
  client:
    service-url:
      defaultZone: ${{EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:http://localhost:8761/eureka}}
  instance:
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 10

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: ${{MANAGEMENT_OTLP_TRACING_ENDPOINT:http://localhost:4317}}

logging:
  structured: true
"""

base_dir = r"d:\VPLMQA"

for svc, config in services.items():
    yaml_path = os.path.join(base_dir, svc, "src", "main", "resources", "application.yml")
    os.makedirs(os.path.dirname(yaml_path), exist_ok=True)
    with open(yaml_path, "w", encoding="utf-8") as f:
        f.write(template.format(service=svc, port=config["port"], db=config["db"]))
    print(f"Updated {yaml_path}")
