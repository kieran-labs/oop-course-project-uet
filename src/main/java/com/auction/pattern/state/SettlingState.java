package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái SETTLING — phiên đang được khóa để chốt kết quả và xử lý thanh toán.
 *
 * <p>Trong trạng thái này, hệ thống đã claim phiên để settlement nên không được nhận thêm bid, gia
 * hạn, chỉnh sửa hoặc đóng từ luồng khác. Mọi thao tác nghiệp vụ bên ngoài settlement đều bị từ
 * chối để tránh thay đổi winner/price trong lúc thanh toán.
 */
public class SettlingState implements AuctionState {

  private static final String ERROR_MSG_TEMPLATE =
      "Phiên đấu giá #%d đang được chốt kết quả. Không thể %s.";

  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId(), "đặt giá"));
  }

  @Override
  public void close(Auction auction) {
    throw new AuctionClosedException(
        String.format(ERROR_MSG_TEMPLATE, auction.getId(), "đóng phiên"));
  }

  @Override
  public void edit(Auction auction) {
    throw new AuctionClosedException(
        String.format(ERROR_MSG_TEMPLATE, auction.getId(), "chỉnh sửa"));
  }

  @Override
  public void extend(Auction auction, long extraSeconds) {
    throw new AuctionClosedException(String.format(ERROR_MSG_TEMPLATE, auction.getId(), "gia hạn"));
  }
}
