-- Auto-bid chains are bounded to protect a request from unbounded work.
-- Persist the terminal reason so clients do not continue displaying ACTIVE state.
ALTER TABLE auto_bid_configs DROP CONSTRAINT IF EXISTS auto_bid_configs_failure_reason_check;

ALTER TABLE auto_bid_configs
    ADD CONSTRAINT auto_bid_configs_failure_reason_check
    CHECK (
        failure_reason IS NULL
        OR failure_reason IN (
            'MAX_PRICE_TOO_LOW',
            'INSUFFICIENT_BALANCE',
            'CHAIN_LIMIT_REACHED',
            'AUCTION_NOT_RUNNING',
            'BIDDER_ALREADY_HIGHEST',
            'ACTIVE_AUTOBID_EXISTS'
        )
    );
