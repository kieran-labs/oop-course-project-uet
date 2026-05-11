package com.auction.exception;

/**
 * Thrown khi thao tác được thực hiện trên một phiên đấu giá không ở trạng thái {@code RUNNING}.
 *
 * <p>Bao gồm các phiên đang ở trạng thái {@code OPEN} (chưa bắt đầu), {@code FINISHED} (đã kết
 * thúc), {@code CANCELED}, hoặc {@code PAID}.
 *
 * <p>Cách dùng điển hình:
 *
 * <pre>{@code
 * if (!"RUNNING".equals(auction.getStatus())) {
 *     throw new AuctionClosedException(
 *         "Cannot place bid on auction in state: " + auction.getStatus());
 * }
 * }</pre>
 *
 * @see AuctionException
 */
public class AuctionClosedException extends AuctionException {

  private static final long serialVersionUID = 1L;

  /**
   * Khởi tạo AuctionClosedException với message mô tả trạng thái phiên và thao tác bị từ chối.
   *
   * @param message mô tả trạng thái phiên và thao tác bị từ chối
   */
  public AuctionClosedException(String message) {
    super(message);
  }

  /**
   * Khởi tạo AuctionClosedException với message và nguyên nhân gốc.
   *
   * @param message mô tả trạng thái phiên và thao tác bị từ chối
   * @param cause exception gốc dẫn đến lỗi này
   */
  public AuctionClosedException(String message, Throwable cause) {
    super(message, cause);
  }
}
