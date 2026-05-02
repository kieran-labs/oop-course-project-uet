package com.auction.pattern.strategy;

import com.auction.dao.ItemDao;
import com.auction.exception.InvalidBidException;
import com.auction.exception.NotFoundException;
import com.auction.model.Auction;
import com.auction.model.Item;
import java.math.BigDecimal;

/** Strategy Pattern - Thuật toán xác thực khi người dùng tự tay đặt giá. */
public class ManualBidStrategy implements BidStrategy {

  private final ItemDao itemDao;

  // Inject ItemDao vào qua constructor
  public ManualBidStrategy(ItemDao itemDao) {
    this.itemDao = itemDao;
  }

  @Override
  public void execute(Auction auction, Long bidderId, BigDecimal amount) {

    // 1. Dùng itemId của Auction để tìm ra Item gốc
    Item item =
        itemDao
            .findById(auction.getItemId())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Dữ liệu lỗi: Không tìm thấy sản phẩm của phiên đấu giá này"));

    // 2. Lấy sellerId từ Item để kiểm tra người bán không được tự mồi giá
    if (item.getSellerId().equals(bidderId)) {
      throw new InvalidBidException("Không thể đặt giá cho sản phẩm của chính mình!");
    }

    // 3. Giá đặt phải lớn hơn giá hiện tại
    // Dùng compareTo với BigDecimal: 1 (Lớn hơn), 0 (Bằng), -1 (Nhỏ hơn)
    if (amount.compareTo(auction.getCurrentPrice()) <= 0) {
      throw new InvalidBidException(
          "Giá đặt phải lớn hơn giá hiện tại (" + auction.getCurrentPrice() + ")!");
    }

    // 4. Nếu hợp lệ, cập nhật trạng thái ngay trong bộ nhớ (chưa xuống DB)
    auction.setCurrentPrice(amount);
    auction.setLeadingBidderId(bidderId);
  }
}
