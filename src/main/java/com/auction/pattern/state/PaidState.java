package com.auction.pattern.state;

import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import java.math.BigDecimal;

/**
 * Trạng thái PAID: Người thắng đã thanh toán xong. Trạng thái cuối cùng của một phiên thành công.
 */
public class PaidState implements AuctionState {

  @Override
  public void placeBid(Auction auction, BigDecimal amount, Long bidderId) {
    throw new AuctionClosedException("Sản phẩm đã được thanh toán. Giao dịch hoàn tất!");
  }

  @Override
  public void edit(Auction auction) {
    throw new AuctionClosedException("Sản phẩm đã được thanh toán. Không thể sửa thông tin!");
  }

  @Override
  public void close(Auction auction) {
    throw new AuctionClosedException("Giao dịch đã hoàn tất!");
  }

  @Override
  public void extend(Auction auction, long seconds) {
    throw new AuctionClosedException("Sản phẩm đã được thanh toán, không thể gia hạn!");
  }
}
