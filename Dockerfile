# Stage 1: build frontend
FROM node:24-alpine AS frontend-build

WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

# Stage 2: build backend (embeds the frontend build)
FROM golang:1.26-alpine AS backend-build

ARG VERSION=dev

WORKDIR /app
COPY backend/go.mod backend/go.sum ./
RUN go mod download
COPY backend/ .
# Copy the frontend build into the embed path before compiling
COPY --from=frontend-build /app/build ./cmd/server/ui
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-X main.version=${VERSION}" -o server ./cmd/server

# Stage 3: minimal runtime image
FROM alpine:3.23

RUN apk --no-cache add ca-certificates

WORKDIR /app
COPY --from=backend-build /app/server .

EXPOSE 8080

CMD ["./server"]
