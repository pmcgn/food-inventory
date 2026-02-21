package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

// Config holds all runtime configuration loaded from environment variables.
type Config struct {
	DatabaseURL   string
	Port          string
	DBSSLCACert   string        // PEM-encoded CA certificate; empty means use system roots
	OFFTimeout    time.Duration // timeout for Open Food Facts HTTP requests
}

// Load reads configuration from environment variables.
//
// Database connection — two modes:
//
//  1. DATABASE_URL is set → used as-is as the libpq connection string.
//     DB_HOST / DB_PORT / DB_USER / DB_PASSWORD / DB_NAME / DB_SSL_MODE are ignored.
//
//  2. DATABASE_URL is not set → connection string is built from the individual
//     DB_* variables listed below.
//
// CA certificate resolution (in priority order):
//
//  1. DB_SSL_CA_CERT  — inline PEM content
//  2. DB_SSL_CA_CERT_FILE — path to a PEM file (Kubernetes secret / configmap mount)
//  3. Neither set → system / container trust store is used
//
// Environment variables:
//
//	DATABASE_URL          full libpq connection string (overrides all DB_* vars)
//	DB_HOST               database hostname            (default: localhost)
//	DB_PORT               database port               (default: 5432)
//	DB_USER               database user               (default: postgres)
//	DB_PASSWORD           database password           (default: postgres)
//	DB_NAME               database name               (default: foodinventory)
//	DB_SSL_MODE           sslmode query param         (default: disable)
//	                        disable | require | verify-ca | verify-full
//	DB_SSL_CA_CERT        PEM-encoded CA certificate (inline)
//	DB_SSL_CA_CERT_FILE   path to PEM CA certificate file
//	PORT                  HTTP listen port            (default: 8080)
func Load() (*Config, error) {
	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		dbURL = fmt.Sprintf(
			"postgres://%s:%s@%s:%s/%s?sslmode=%s",
			getEnv("DB_USER", "postgres"),
			getEnv("DB_PASSWORD", "postgres"),
			getEnv("DB_HOST", "localhost"),
			getEnv("DB_PORT", "5432"),
			getEnv("DB_NAME", "foodinventory"),
			getEnv("DB_SSL_MODE", "disable"),
		)
	}

	caCert, err := resolveSSLCACert()
	if err != nil {
		return nil, err
	}

	offTimeout, err := parseDurationMS("PRODUCT_LOOKUP_TIMEOUT_MS", 500)
	if err != nil {
		return nil, err
	}

	return &Config{
		DatabaseURL: dbURL,
		Port:        getEnv("PORT", "8080"),
		DBSSLCACert: caCert,
		OFFTimeout:  offTimeout,
	}, nil
}

// resolveSSLCACert returns the PEM-encoded CA certificate to use, or an empty
// string if neither variable is set (system roots will be used).
// DB_SSL_CA_CERT (inline PEM) takes priority over DB_SSL_CA_CERT_FILE.
func resolveSSLCACert() (string, error) {
	if cert := os.Getenv("DB_SSL_CA_CERT"); cert != "" {
		return cert, nil
	}
	if path := os.Getenv("DB_SSL_CA_CERT_FILE"); path != "" {
		data, err := os.ReadFile(path)
		if err != nil {
			return "", fmt.Errorf("DB_SSL_CA_CERT_FILE: %w", err)
		}
		return string(data), nil
	}
	return "", nil
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func parseDurationMS(key string, defaultMS int) (time.Duration, error) {
	v := os.Getenv(key)
	if v == "" {
		return time.Duration(defaultMS) * time.Millisecond, nil
	}
	ms, err := strconv.Atoi(v)
	if err != nil || ms <= 0 {
		return 0, fmt.Errorf("%s must be a positive integer (milliseconds), got %q", key, v)
	}
	return time.Duration(ms) * time.Millisecond, nil
}
