package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dao.AutoBidConfigDao;
import com.auction.dao.BidTransactionDao;
import com.auction.dao.BidTransactionDao.BidHistoryEntry;
import com.auction.dto.AutoBidRequest;
import com.auction.dto.BidUpdateMessage;
import com.auction.exception.NotFoundException;
import com.auction.model.Auction;
import com.auction.model.AutoBidConfig;
import com.auction.model.BidTransaction;
import com.auction.pattern.observer.AuctionEventManager;
import com.auction.pattern.state.AuctionState;
import com.auction.pattern.strategy.AutoBidStrategy;
import com.auction.pattern.strategy.BidStrategy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Lớp Service trung tâm xử lý Logic Đặt giá. Hợp nhất tính năng Tuần 3 (Core Bidding) và Tuần 4
 * (Concurrency, Anti-sniping, Auto-bidding).
 */
public class BidService {

  // --- 1. KHAI BÁO CÁC DEPENDENCY ---
  private final AuctionDao auctionDao;
  private final BidTransactionDao bidTransactionDao;
  private final AutoBidConfigDao autoBidConfigDao;
  private final AuctionService auctionService;
  private final BidStrategy manualBidStrategy;
  private final AuctionEventManager eventManager;

  // AutoBidStrategy được inject qua Setter để tránh Circular Dependency (Lỗi vòng lặp phụ thuộc)
  private AutoBidStrategy autoBidStrategy;

  // --- 2. CONSTRUCTOR ---
  public BidService(
      AuctionDao auctionDao,
      BidTransactionDao bidTransactionDao,
      AutoBidConfigDao autoBidConfigDao,
      AuctionService auctionService,
      BidStrategy manualBidStrategy,
      AuctionEventManager eventManager) {
    this.auctionDao = auctionDao;
    this.bidTransactionDao = bidTransactionDao;
    this.autoBidConfigDao = autoBidConfigDao;
    this.auctionService = auctionService;
    this.manualBidStrategy = manualBidStrategy;
    this.eventManager = eventManager;
  }

  // Setter để tiêm AutoBidStrategy vào sau khi khởi tạo
  public void setAutoBidStrategy(AutoBidStrategy autoBidStrategy) {
    this.autoBidStrategy = autoBidStrategy;
  }

  // --- 3. LOGIC ĐĂNG KÝ AUTO-BID (TUẦN 4) ---
  /** Người dùng cài đặt cấu hình tự động đặt giá. */
  public void setupAutoBid(Long auctionId, Long bidderId, AutoBidRequest req) {
    AutoBidConfig config = new AutoBidConfig();
    config.setAuctionId(auctionId);
    config.setBidderId(bidderId);
    config.setMaxBid(req.getMaxBid());
    config.setIncrement(req.getIncrement());
    config.setActive(true);
    config.setRegisteredAt(LocalDateTime.now());

    autoBidConfigDao.insert(config);
  }

  // --- 4. LOGIC ĐẶT GIÁ LÕI (HỢP NHẤT TUẦN 3 & TUẦN 4) ---
  /** Xử lý yêu cầu đặt giá (Bao gồm cả người dùng tự bấm và hệ thống tự động gọi). */
  public void placeBid(
      org.jdbi.v3.core.Handle handle,
      Long auctionId,
      Long bidderId,
      BigDecimal amount,
      boolean isAutoBid) {

    // BƯỚC 1: Tầng 2 DB-Lock (Xử lý Đa luồng)
    // Dùng SELECT ... FOR UPDATE để khóa dòng dữ liệu dưới Database
    Auction auction = auctionDao.findByIdForUpdate(handle, auctionId);
    if (auction == null) {
      throw new NotFoundException("Phiên đấu giá không tồn tại hoặc đã bị xóa");
    }
    // BƯỚC 2: State Pattern (Chặn từ vòng gửi xe nếu phiên không RUNNING)
    AuctionState state = auctionService.getState(auction);
    state.placeBid(auction, amount, bidderId);

    // BƯỚC 3: Tầng 1 App-Lock (Đảm bảo an toàn bộ nhớ trên Server)
    synchronized (auction) {

      // 3.1. Strategy Pattern: Xác thực giá hợp lệ
      manualBidStrategy.execute(auction, bidderId, amount);

      // 3.2. Tính năng Nâng cao: Anti-sniping (Chống bắn tỉa)
      LocalDateTime now = LocalDateTime.now();
      boolean isTimeExtended = false;
      long remainingMs = ChronoUnit.MILLIS.between(now, auction.getEndTime());

      // Nếu thời gian còn lại dưới 30 giây (30,000 milliseconds) -> Cộng thêm 60s
      if (remainingMs > 0 && remainingMs < 30_000) {
        auction.setEndTime(auction.getEndTime().plusSeconds(60));
        isTimeExtended = true;
      }

      // 3.3. Cập nhật dữ liệu xuống DB
      auctionDao.update(auction); // (Nên nằm trong Transaction của JDBI)

      BidTransaction txn = new BidTransaction();
      txn.setAuctionId(auctionId);
      txn.setBidderId(bidderId);
      txn.setAmount(amount);
      txn.setAutoBid(isAutoBid);
      txn.setCreatedAt(now);
      bidTransactionDao.insert(handle, txn);

      // 3.4. Observer Pattern: Thông báo Realtime cho WebSocket
      // Chú ý: Đã truyền đủ 6 tham số theo cấu trúc DTO mới nhất
      BidUpdateMessage updateMsg =
          BidUpdateMessage.bidUpdate(
              auctionId, amount, bidderId, "BID_UPDATE", auction.getEndTime(), isAutoBid);
      eventManager.notifyBidUpdate(auctionId, updateMsg);

      if (isTimeExtended) {
        BidUpdateMessage extendMsg =
            BidUpdateMessage.bidUpdate(
                auctionId, amount, bidderId, "TIME_EXTENDED", auction.getEndTime(), isAutoBid);
        eventManager.notifyTimeExtended(auctionId, extendMsg);
      }
    } // KẾT THÚC KHỐI SYNCHRONIZED

    // BƯỚC 4: Kích hoạt Auto-bidding (Của các đối thủ)
    // CHÚ Ý: Đặt NGOÀI khối synchronized để tránh Deadlock và lồng ghép đệ quy quá sâu
    if (autoBidStrategy != null) {
      autoBidStrategy.executeAll(handle, auction, amount);
    }
  }

  /**
   * Lấy lịch sử đặt giá để vẽ biểu đồ và hiển thị danh sách.
   *
   * @param auctionId ID của phiên đấu giá.
   * @return Danh sách các giao dịch đặt giá được sắp xếp theo thời gian.
   */
  public List<BidTransaction> getBidHistory(Long auctionId) {
    // Gọi DAO để lấy danh sách bid, sắp xếp theo thời gian từ cũ đến mới
    return bidTransactionDao.findByAuctionId(auctionId);
  }

  public List<BidHistoryEntry> getBidHistoryWithUsernames(Long auctionId) {
    return bidTransactionDao.findWithUsernames(auctionId);
  }
}
