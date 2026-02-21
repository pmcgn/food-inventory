package db

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"embed"
	"fmt"
	"io/fs"
	"log"
	"sort"
	"strconv"
	"strings"

	"github.com/jackc/pgx/v5/pgxpool"
)

//go:embed migrations/*.sql
var migrationsFS embed.FS

type migration struct {
	version int
	name    string
	sql     string
}

// loadMigrations reads all *.sql files from the embedded migrations directory,
// parses their version numbers from the NNN_name.sql filename pattern, and
// returns them sorted by version ascending.
func loadMigrations() ([]migration, error) {
	entries, err := fs.ReadDir(migrationsFS, "migrations")
	if err != nil {
		return nil, fmt.Errorf("read migrations dir: %w", err)
	}

	var migrations []migration
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".sql") {
			continue
		}
		// Filename format: NNN_name.sql
		parts := strings.SplitN(entry.Name(), "_", 2)
		if len(parts) < 2 {
			return nil, fmt.Errorf("migration %q does not match NNN_name.sql pattern", entry.Name())
		}
		ver, err := strconv.Atoi(parts[0])
		if err != nil {
			return nil, fmt.Errorf("migration %q: cannot parse version number: %w", entry.Name(), err)
		}
		content, err := fs.ReadFile(migrationsFS, "migrations/"+entry.Name())
		if err != nil {
			return nil, fmt.Errorf("read migration %q: %w", entry.Name(), err)
		}
		migrations = append(migrations, migration{
			version: ver,
			name:    entry.Name(),
			sql:     string(content),
		})
	}

	sort.Slice(migrations, func(i, j int) bool {
		return migrations[i].version < migrations[j].version
	})
	return migrations, nil
}

// NewPool creates a pgx connection pool and verifies connectivity.
// sslCACert is the PEM-encoded CA certificate used to verify the server's
// TLS certificate. When empty the system root CAs are used.
func NewPool(ctx context.Context, databaseURL, sslCACert string) (*pgxpool.Pool, error) {
	cfg, err := pgxpool.ParseConfig(databaseURL)
	if err != nil {
		return nil, fmt.Errorf("parse database URL: %w", err)
	}

	if sslCACert != "" {
		certPool := x509.NewCertPool()
		if !certPool.AppendCertsFromPEM([]byte(sslCACert)) {
			return nil, fmt.Errorf("DB_SSL_CA_CERT: no valid PEM certificate block found")
		}
		if cfg.ConnConfig.TLSConfig == nil {
			cfg.ConnConfig.TLSConfig = &tls.Config{}
		}
		cfg.ConnConfig.TLSConfig.RootCAs = certPool
	}

	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, err
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, err
	}
	return pool, nil
}

// SchemaVersion returns the highest migration version that has been applied,
// or 0 if the schema_migrations table does not exist yet.
func SchemaVersion(ctx context.Context, pool *pgxpool.Pool) (int, error) {
	var version int
	err := pool.QueryRow(ctx, `
		SELECT COALESCE(MAX(version), 0)
		FROM schema_migrations`).Scan(&version)
	if err != nil {
		return 0, fmt.Errorf("query schema version: %w", err)
	}
	return version, nil
}

const createMigrationsTable = `
CREATE TABLE IF NOT EXISTS schema_migrations (
    version    INT         PRIMARY KEY,
    name       TEXT        NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);`

// RunMigrations ensures the schema_migrations tracking table exists, then
// applies every pending migration in version order. Each migration runs
// inside its own transaction so a failure leaves the database in a
// consistent, known-version state. Safe to call on every startup.
func RunMigrations(ctx context.Context, pool *pgxpool.Pool) error {
	if _, err := pool.Exec(ctx, createMigrationsTable); err != nil {
		return fmt.Errorf("create schema_migrations table: %w", err)
	}

	migrations, err := loadMigrations()
	if err != nil {
		return err
	}

	for _, m := range migrations {
		applied, err := isMigrationApplied(ctx, pool, m.version)
		if err != nil {
			return err
		}
		if applied {
			continue
		}
		if err := applyMigration(ctx, pool, m); err != nil {
			return fmt.Errorf("apply migration %q: %w", m.name, err)
		}
		log.Printf("db: applied migration v%d %s", m.version, m.name)
	}

	ver, err := SchemaVersion(ctx, pool)
	if err != nil {
		return err
	}
	log.Printf("db: schema version %d", ver)
	return nil
}

func isMigrationApplied(ctx context.Context, pool *pgxpool.Pool, version int) (bool, error) {
	var count int
	err := pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM schema_migrations WHERE version = $1`, version,
	).Scan(&count)
	if err != nil {
		return false, fmt.Errorf("check migration v%d: %w", version, err)
	}
	return count > 0, nil
}

func applyMigration(ctx context.Context, pool *pgxpool.Pool, m migration) error {
	tx, err := pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	if _, err := tx.Exec(ctx, m.sql); err != nil {
		return err
	}
	if _, err := tx.Exec(ctx,
		`INSERT INTO schema_migrations (version, name) VALUES ($1, $2)`,
		m.version, m.name,
	); err != nil {
		return err
	}
	return tx.Commit(ctx)
}
