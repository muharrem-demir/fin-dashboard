-- A portfolio is the aggregate root: a named container the user manages from the dashboard.
CREATE TABLE portfolio (
    id         UUID         NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_portfolio PRIMARY KEY (id),
    CONSTRAINT ck_portfolio_name_not_blank CHECK (length(btrim(name)) > 0)
);

COMMENT ON TABLE portfolio IS 'A named collection of stock holdings.';
COMMENT ON COLUMN portfolio.id IS 'Identity assigned by the domain, not by the database.';
