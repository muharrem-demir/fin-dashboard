-- A watchlist entry: one symbol the user follows, independently of any portfolio.
--
-- The unique constraint on ticker is a safety net, not the implementation of the "already watched"
-- rule. That rule lives in AddWatchlistEntryUseCase, which checks and inserts in one transaction;
-- this constraint guarantees the table can never hold the same symbol twice even if that code is
-- bypassed. Symbols are stored upper case because Ticker normalises them, which is what makes the
-- constraint case-correct without a functional index.
CREATE TABLE watchlist (
    id         UUID        NOT NULL,
    ticker     VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_watchlist PRIMARY KEY (id),
    CONSTRAINT uq_watchlist_ticker UNIQUE (ticker),
    CONSTRAINT ck_watchlist_ticker_upper CHECK (ticker = upper(ticker))
);

COMMENT ON TABLE watchlist IS 'Symbols the user follows, outside any portfolio.';
COMMENT ON COLUMN watchlist.id IS 'Identity assigned by the domain, not by the database.';
COMMENT ON COLUMN watchlist.created_at IS 'When the symbol was added; the order a listing is returned in.';
