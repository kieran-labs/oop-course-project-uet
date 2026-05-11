package com.auction.exception;

/**
 * Thrown khi thao tác vi phạm ràng buộc unique, ví dụ:
 *
 * <ul>
 *   <li>Đăng ký user với email hoặc username đã tồn tại
 *   <li>Tạo item với SKU trùng lặp
 *   <li>Insert entity có primary key đã tồn tại
 * </ul>
 *
 * <p>Cách dùng điển hình:
 *
 * <pre>{@code
 * if (userDao.existsByEmail(email)) {
 *     throw new DuplicateException("Email already registered: " + email);
 * }
 * }</pre>
 *
 * @see AuctionException
 */
public class DuplicateException extends AuctionException {

  private static final long serialVersionUID = 1L;

  /**
   * Khởi tạo DuplicateException với message mô tả ràng buộc unique bị vi phạm.
   *
   * @param message mô tả ràng buộc unique bị vi phạm
   */
  public DuplicateException(String message) {
    super(message);
  }

  /**
   * Khởi tạo DuplicateException với message và nguyên nhân gốc.
   *
   * @param message mô tả ràng buộc unique bị vi phạm
   * @param cause exception gốc dẫn đến lỗi này (ví dụ: SQLIntegrityConstraintViolationException)
   */
  public DuplicateException(String message, Throwable cause) {
    super(message, cause);
  }
}
