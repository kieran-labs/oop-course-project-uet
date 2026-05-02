package com.auction.pattern.state;

import com.auction.model.Auction;
import java.math.BigDecimal;

public interface AuctionState {
  void placeBid(Auction auction, BigDecimal amount, Long bidderId);

  void edit(Auction auction);

  void close(Auction auction);

  void extend(Auction auction, long seconds);
}
