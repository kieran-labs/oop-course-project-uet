package com.auction.service;

import com.auction.dao.AuctionDao;
import com.auction.dao.ItemDao;
import com.auction.dto.CreateAuctionRequest;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Auction;
import com.auction.model.Item;
import com.auction.pattern.state.*;

/** Lớp Service xử lý logic nghiệp vụ cho Phiên đấu giá (Auction). */
public class AuctionService {

  private final AuctionDao auctionDao;
  private final ItemDao itemDao; // Thêm ItemDao để truy xuất thông tin Sản phẩm

  // Inject cả 2 DAO qua constructor
  public AuctionService(AuctionDao auctionDao, ItemDao itemDao) {
    this.auctionDao = auctionDao;
    this.itemDao = itemDao;
  }

  /**
   * Hàm phụ trợ (Factory for State): Chuyển đổi trạng thái dạng String từ DB thành đối tượng State
   * Pattern tương ứng.
   */
  public AuctionState getState(Auction auction) {
    switch (auction.getStatus().toUpperCase()) {
      case "OPEN":
        return new OpenState();
      case "RUNNING":
        return new RunningState();
      case "FINISHED":
        return new FinishedState();
      case "PAID":
        return new PaidState();
      case "CANCELED":
        return new CanceledState();
      default:
        throw new IllegalStateException(
            "Hệ thống không nhận diện được trạng thái: " + auction.getStatus());
    }
  }

  /**
   * Seller cập nhật thông tin phiên đấu giá. Áp dụng State Pattern: Chỉ được sửa khi trạng thái là
   * OPEN.
   */
  public void update(Long auctionId, CreateAuctionRequest req, Long currentUserId) {

    // 1. Lấy dữ liệu Auction từ Database (Xử lý Optional)
    Auction auction =
        auctionDao
            .findById(auctionId)
            .orElseThrow(
                () -> new NotFoundException("Không tìm thấy phiên đấu giá với ID: " + auctionId));

    // 2. Tìm Item để lấy thông tin người bán (Seller) (Xử lý Optional)
    Item item =
        itemDao
            .findById(auction.getItemId())
            .orElseThrow(
                () ->
                    new NotFoundException("Sản phẩm của phiên đấu giá bị lỗi hoặc không tồn tại"));

    // 3. Kiểm tra quyền (Authorization): Chỉ người tạo (Seller) mới được sửa
    if (!item.getSellerId().equals(currentUserId)) {
      throw new UnauthorizedException("Bạn không có quyền sửa phiên đấu giá của người khác!");
    }

    // 4. Phân giải State hiện tại
    AuctionState currentState = getState(auction);

    // 5. Kiểm tra xem trạng thái hiện tại có cho phép sửa không
    // Nếu không cho phép (ví dụ đang RUNNING), hàm edit() sẽ tự ném ra AuctionClosedException
    currentState.edit(auction);

    // 6. Nếu vượt qua an toàn, tiến hành cập nhật dữ liệu
    auction.setStartingPrice(req.getStartingPrice());
    auction.setStartTime(req.getStartTime());
    auction.setEndTime(req.getEndTime());

    // 7. Lưu xuống Database
    auctionDao.update(auction);
  }

  /** Quyền năng của Admin: Hủy phiên đấu giá bất kể trạng thái hiện tại. */
  public void cancelAuctionByAdmin(Long auctionId, Long adminId, String role) {
    // 1. Kiểm tra quyền Admin từ JWT token truyền xuống
    if (!"ADMIN".equals(role)) {
      throw new UnauthorizedException("Chỉ Admin mới có quyền thực hiện hành động này!");
    }

    // 2. Lấy phiên đấu giá
    Auction auction =
        auctionDao
            .findById(auctionId)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy phiên đấu giá"));

    // 3. Ép trạng thái về CANCELED
    auction.setStatus("CANCELED");
    auctionDao.update(auction);

    // 4. (Tùy chọn) Notify cho các bên liên quan qua WebSocket
    // updateMsg = BidUpdateMessage.auctionCanceled(auctionId);
    // eventManager.notifyAuctionEnd(auctionId, updateMsg);
  }
}
