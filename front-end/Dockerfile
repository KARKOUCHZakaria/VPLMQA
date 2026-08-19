# Stage 1: Build the React application using Vite
FROM node:20-alpine AS builder
WORKDIR /app
ARG VITE_GATEWAY_URL=http://localhost:8080
ARG VITE_MINIO_PUBLIC_BASE_URL=http://localhost:9005/figma-designs
ARG NGINX_CONF=nginx.conf
ENV VITE_GATEWAY_URL=$VITE_GATEWAY_URL
ENV VITE_MINIO_PUBLIC_BASE_URL=$VITE_MINIO_PUBLIC_BASE_URL
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml* ./
RUN corepack enable && corepack prepare pnpm@10.22.0 --activate && pnpm install --frozen-lockfile
COPY . .
RUN pnpm run build

# Stage 2: Serve the React application using Nginx
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
ARG NGINX_CONF=nginx.conf
COPY ${NGINX_CONF} /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
