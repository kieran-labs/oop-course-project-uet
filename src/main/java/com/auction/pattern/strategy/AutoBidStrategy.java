package com.auction.pattern.strategy;

import com.auction.dao.AutoBidConfigDao;
import com.auction.model.Auction;
import com.auction.model.AutoBidConfig;
import com.auction.service.BidService;
import java.math.BigDecimal;
import java.util.List;
import java.util.PriorityQueue;

/** Strategy Pattern - Thuật toán Đặt giá tự động (Auto-bidding). */
public class AutoBidStrategy {

  private final AutoBidConfigDao autoBidConfigDao;
  private final BidService
      bidService; // Cần gọi ngược lại BidService để thực hiện hành động đặt giá

  public AutoBidStrategy(AutoBidConfigDao autoBidConfigDao, BidService bidService) {
    this.autoBidConfigDao = autoBidConfigDao;
    this.bidService = bidService;
  }

  /** Hàm này được gọi MỖI KHI có một mức giá mới được thiết lập. */
  public void executeAll(org.jdbi.v3.core.Handle handle, Auction auction, BigDecimal currentPrice) {
    // 1. Lấy tất cả các cấu hình Auto-bid ĐANG HOẠT ĐỘNG của phiên này
    List<AutoBidConfig> configs = autoBidConfigDao.findActiveByAuctionId(auction.getId());
    if (configs.isEmpty()) {
      return;
    }

    // 2. Sử dụng PriorityQueue để ưu tiên người đăng ký trước (registeredAt nhỏ hơn)
    PriorityQueue<AutoBidConfig> queue =
        new PriorityQueue<>((c1, c2) -> c1.getRegisteredAt().compareTo(c2.getRegisteredAt()));
    queue.addAll(configs);

    // 3. Duyệt hàng đợi để thực thi tự động
    while (!queue.isEmpty()) {
      AutoBidConfig config = queue.poll();

      // Bỏ qua nếu người dẫn đầu hiện tại chính là người cài auto-bid này
      if (config.getBidderId().equals(auction.getLeadingBidderId())) {
        continue;
      }

      // Tính toán giá dự kiến = Giá hiện tại + Bước giá (increment)
      BigDecimal nextBidAmount = currentPrice.add(config.getIncrement());

      // 4. Kiểm tra ngân sách (maxBid)
      if (nextBidAmount.compareTo(config.getMaxBid()) <= 0) {
        // Đủ ngân sách -> Tự động gọi hàm placeBid với cờ isAutoBid = true
        // (Vì placeBid đệ quy gọi lại chính nó, ta sẽ giải quyết chuỗi chain auto-bids)
        bidService.placeBid(handle, auction.getId(), config.getBidderId(), nextBidAmount, true);
        break; // Xử lý xong 1 người thì dừng vòng lặp hiện tại, để đệ quy tự chạy tiếp vòng mới
      } else {
        // Không đủ ngân sách -> Tắt auto-bid của người này
        config.setActive(false);
        autoBidConfigDao.update(config);
      }
    }
  }
}
