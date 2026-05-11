package com.auction.exception;

/**
 * Thrown khi một bid vi phạm business rule, ví dụ:
 *
 * <ul>
 *   <li>Giá bid không dương (≤ 0)
 *   <li>Giá bid thấp hơn hoặc bằng giá hiện tại
 *   <li>Người bid là seller của chính item đó (self-bidding)
 *   <li>Mức tăng bid thấp hơn mức tối thiểu cho phép
 * </ul>
 *
 * <p>Cách dùng điển hình:
 *
 * <pre>{@code
 * if (amount.compareTo(auction.getCurrentPrice()) <= 0) {
 *     throw new InvalidBidException(
 *         "Bid " + amount + " must exceed current price " + auction.getCurrentPrice());
 * }
 * }</pre>
 *
 * @see AuctionException
 */
public class InvalidBidException extends AuctionException {

  private static final long serialVersionUID = 1L;

  /**
   * Khởi tạo InvalidBidException với message mô tả lý do bid không hợp lệ.
   *
   * @param message mô tả lý do bid không hợp lệ
   */
  public InvalidBidException(String message) {
    super(message);
  }

  /**
   * Khởi tạo InvalidBidException với message và nguyên nhân gốc.
   *
   * @param message mô tả lý do bid không hợp lệ
   * @param cause exception gốc dẫn đến lỗi này
   */
  public InvalidBidException(String message, Throwable cause) {
    super(message, cause);
  }
}
