package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái FINISHED: Phiên đấu giá đã kết thúc thời gian (hết giờ). Chờ người thắng thanh toán.
 */
public class FinishedState implements AuctionState {

  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    throw new AuctionClosedException("Phiên đấu giá đã kết thúc. Không thể đặt giá nữa!");
  }

  @Override
  public void edit(Auction auction) {
    throw new AuctionClosedException("Phiên đấu giá đã kết thúc. Không thể sửa đổi thông tin!");
  }

  @Override
  public void close(Auction auction) {
    // Đã đóng rồi thì không làm gì cả, hoặc có thể throw Exception tuỳ thiết kế
    throw new AuctionClosedException("Phiên đấu giá đã ở trạng thái kết thúc!");
  }

  @Override
  public void extend(Auction auction, long seconds) {
    throw new AuctionClosedException("Không thể gia hạn phiên đấu giá đã kết thúc!");
  }
}
