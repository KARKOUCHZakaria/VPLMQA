FROM python:3.11-slim
WORKDIR /app
COPY langgraph-agents/requirements.txt .
RUN pip install --upgrade pip
RUN pip install --no-cache-dir -r requirements.txt
RUN pip install --no-cache-dir --no-deps xgboost==3.2.0
RUN apt-get update \
    && apt-get install -y --no-install-recommends chromium xvfb openbox x11vnc novnc websockify \
    && rm -rf /var/lib/apt/lists/*
ENV PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium
COPY langgraph-agents/ .
COPY ml/*.pkl /app/ml/
EXPOSE 8090 7900
CMD ["sh", "/app/start.sh"]
