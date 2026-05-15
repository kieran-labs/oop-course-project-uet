package com.auction.dao;

import java.math.BigDecimal;
import org.jdbi.v3.core.Handle;

/** Append-only ledger for balance and reserved-balance movements. */
public final class WalletTransactionDao {

  private WalletTransactionDao() {}

  public static void insert(
      Handle handle,
      Long userId,
      Long auctionId,
      Long bidTransactionId,
      String kind,
      BigDecimal amount,
      String referenceInfo) {
    handle.execute(
        """
        INSERT INTO wallet_transactions (
            user_id, auction_id, bid_transaction_id, kind, amount, reference_info
        )
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        userId,
        auctionId,
        bidTransactionId,
        kind,
        amount,
        referenceInfo);
  }
}
