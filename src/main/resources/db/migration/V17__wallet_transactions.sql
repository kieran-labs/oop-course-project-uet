CREATE TABLE wallet_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    auction_id          BIGINT REFERENCES auctions(id),
    bid_transaction_id  BIGINT REFERENCES bid_transactions(id),
    kind                VARCHAR(32) NOT NULL CHECK (
                            kind IN (
                                'DEPOSIT',
                                'FREEZE',
                                'RELEASE',
                                'WIN_CONSUME',
                                'SELLER_PAYOUT',
                                'CANCEL_RELEASE'
                            )
                        ),
    amount              DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    reference_info      TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wallet_transactions_user_created
    ON wallet_transactions(user_id, created_at DESC);

CREATE INDEX idx_wallet_transactions_auction
    ON wallet_transactions(auction_id);

CREATE INDEX idx_wallet_transactions_bid
    ON wallet_transactions(bid_transaction_id);
