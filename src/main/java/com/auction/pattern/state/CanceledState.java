package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/** Trạng thái CANCELED: Phiên bị huỷ bởi người bán hoặc Admin. */
public class CanceledState implements AuctionState {

  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    throw new AuctionClosedException("Phiên đấu giá này đã bị huỷ!");
  }

  @Override
  public void edit(Auction auction) {
    throw new AuctionClosedException("Phiên đấu giá đã bị huỷ. Không thể sửa thông tin!");
  }

  @Override
  public void close(Auction auction) {
    throw new AuctionClosedException("Phiên đấu giá đã bị huỷ từ trước!");
  }

  @Override
  public void extend(Auction auction, long seconds) {
    throw new AuctionClosedException("Phiên đấu giá đã bị huỷ, không thể gia hạn!");
  }
}
