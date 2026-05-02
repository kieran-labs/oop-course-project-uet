package com.auction.pattern.strategy;

import com.auction.model.Auction;
import java.math.BigDecimal;

/** Strategy Pattern - Giao diện chung cho mọi thuật toán đặt giá. */
public interface BidStrategy {
  void execute(Auction auction, Long bidderId, BigDecimal amount);
}
