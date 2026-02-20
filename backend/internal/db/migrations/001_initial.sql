-- Product metadata cached from Open Food Facts.
-- Keyed by EAN so each product is only fetched once from the external API.
CREATE TABLE IF NOT EXISTS products (
    ean       VARCHAR(13) PRIMARY KEY,
    name      TEXT        NOT NULL,
    category  TEXT,
    image_url TEXT
);

-- Current stock levels.
-- quantity is enforced > 0; rows are deleted when quantity reaches 0.
CREATE TABLE IF NOT EXISTS inventory (
    id                  SERIAL      PRIMARY KEY,
    ean                 VARCHAR(13) NOT NULL REFERENCES products(ean),
    quantity            INT         NOT NULL DEFAULT 1
                            CHECK (quantity > 0),
    expiry_date         DATE,
    low_stock_threshold INT         NOT NULL DEFAULT 1
                            CHECK (low_stock_threshold > 0)
);

-- Singleton settings row (id is always 1).
CREATE TABLE IF NOT EXISTS settings (
    id                  INT PRIMARY KEY DEFAULT 1
                            CHECK (id = 1),
    expiry_warning_days INT NOT NULL DEFAULT 7
                            CHECK (expiry_warning_days > 0)
);

INSERT INTO settings (id, expiry_warning_days)
VALUES (1, 7)
ON CONFLICT DO NOTHING;
