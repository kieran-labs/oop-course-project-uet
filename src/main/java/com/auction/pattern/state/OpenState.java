package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

public class OpenState implements AuctionState {

  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    // Chặn luồng đặt giá
    throw new AuctionClosedException("Phiên đấu giá chưa bắt đầu. Bạn chưa thể đặt giá!");
  }

  @Override
  public void edit(Auction auction) {
    // Hợp lệ, cho phép đi tiếp logic cập nhật thông tin
    System.out.println("Đang cập nhật thông tin phiên đấu giá...");
  }

  @Override
  public void close(Auction auction) {
    // Có thể cho phép người bán hủy/đóng phiên sớm
    auction.setStatus("CANCELED");
  }

  @Override
  public void extend(Auction auction, long seconds) {
    throw new AuctionClosedException("Phiên chưa chạy, không thể gia hạn!");
  }
}
