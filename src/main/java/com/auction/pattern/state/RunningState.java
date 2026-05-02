package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

public class RunningState implements AuctionState {

  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    // Hợp lệ! Logic kiểm tra giá hợp lệ (lớn hơn giá hiện tại)
    // sẽ được nhường lại cho BidStrategy xử lý ở Tuần 3.
  }

  @Override
  public void edit(Auction auction) {
    // Chặn luồng sửa đổi
    throw new AuctionClosedException("Không thể sửa đổi thông tin khi phiên đấu giá đang diễn ra!");
  }

  @Override
  public void close(Auction auction) {
    auction.setStatus("FINISHED");
  }

  @Override
  public void extend(Auction auction, long seconds) {
    auction.setEndTime(auction.getEndTime().plusSeconds(seconds));
  }
}
