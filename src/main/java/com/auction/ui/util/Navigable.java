package com.auction.ui.util;

/**
 * Interface định nghĩa vòng đời (lifecycle) điều hướng cho các FXML Controller.
 *
 * <p>SceneManager tự động gọi các method này theo đúng thứ tự khi chuyển màn hình:
 *
 * <ol>
 *   <li>{@link #onNavigatedFrom()} — gọi trên controller của màn hình <em>đang rời đi</em>
 *   <li>{@link #onDataReceived(Object)} — gọi trên controller của màn hình <em>sắp hiển thị</em>
 *       (chỉ khi có data được truyền kèm)
 *   <li>{@link #onNavigatedTo()} — gọi trên controller của màn hình <em>vừa hiển thị</em>
 * </ol>
 *
 * <p>Tất cả method đều có implementation mặc định rỗng (default method), vì vậy controller chỉ cần
 * override những method thực sự có logic — không bắt buộc implement cả ba.
 *
 * <p><b>Ví dụ sử dụng:</b>
 *
 * <pre>{@code
 * public class AuctionDetailController implements Navigable {
 *
 *     private Long auctionId;
 *
 *     @Override
 *     public void onDataReceived(Object data) {
 *         this.auctionId = (Long) data; // nhận auctionId từ màn hình trước
 *     }
 *
 *     @Override
 *     public void onNavigatedTo() {
 *         loadAuctionDetail(auctionId); // gọi API sau khi đã có data
 *     }
 *
 *     @Override
 *     public void onNavigatedFrom() {
 *         webSocketClient.disconnect(); // cleanup khi rời màn hình
 *     }
 * }
 * }</pre>
 *
 * @see SceneManager#navigateTo(String)
 * @see SceneManager#navigateTo(String, Object)
 * @see SceneManager#navigateBack(String)
 */
public interface Navigable {

  /**
   * Được gọi mỗi khi điều hướng <em>tới</em> màn hình này.
   *
   * <p>Thích hợp để:
   *
   * <ul>
   *   <li>Refresh dữ liệu hiển thị (gọi lại API lấy danh sách mới nhất)
   *   <li>Reset trạng thái UI về giá trị mặc định
   *   <li>Bắt đầu các tác vụ nền (polling, animation...)
   * </ul>
   *
   * <p>Nếu màn hình cần nhận data từ màn hình trước, hãy đảm bảo {@link #onDataReceived(Object)}
   * được override — method đó sẽ được gọi <em>trước</em> {@code onNavigatedTo()}, nên data sẽ luôn
   * sẵn sàng tại thời điểm method này chạy.
   *
   * <p><b>Ví dụ:</b> {@code AuctionListController} gọi lại API để lấy danh sách đấu giá mới nhất
   * mỗi lần người dùng quay lại màn hình này.
   */
  default void onNavigatedTo() {}

  /**
   * Được gọi để truyền dữ liệu từ màn hình trước sang màn hình này, <em>trước</em> {@link
   * #onNavigatedTo()}.
   *
   * <p>Controller nhận {@code data} dưới dạng {@link Object} và tự cast về kiểu cụ thể mà nó mong
   * đợi. Vì vậy, cần có sự đồng thuận ngầm giữa màn hình gửi và màn hình nhận về kiểu dữ liệu được
   * truyền.
   *
   * <p><b>Ví dụ:</b> {@code auction-list.fxml} truyền một {@code Long auctionId} → {@code
   * auction-detail.fxml} cast về {@code Long} để tải chi tiết phiên đấu giá tương ứng.
   *
   * <p><b>Lưu ý:</b> Method này chỉ được gọi khi sử dụng {@link SceneManager#navigateTo(String,
   * Object)} — tức là khi có data kèm theo. Nếu điều hướng bằng {@link
   * SceneManager#navigateTo(String)} (không có data), method này sẽ không được gọi.
   *
   * @param data dữ liệu bất kỳ do màn hình trước truyền sang; controller tự cast về đúng kiểu mong
   *     đợi
   */
  default void onDataReceived(Object data) {}

  /**
   * Được gọi khi người dùng <em>rời khỏi</em> màn hình này để sang màn hình khác.
   *
   * <p>Thích hợp để giải phóng tài nguyên không còn cần thiết khi màn hình không hiển thị:
   *
   * <ul>
   *   <li>Đóng kết nối WebSocket / SSE
   *   <li>Hủy {@code Timer} hoặc {@code ScheduledExecutorService}
   *   <li>Dừng animation đang chạy
   *   <li>Hủy đăng ký listener / callback
   * </ul>
   *
   * <p><b>Lưu ý:</b> Vì controller được cache vĩnh viễn trong {@link SceneManager}, method này
   * <em>không</em> tương đương với destructor — màn hình vẫn tồn tại trong bộ nhớ và có thể được
   * điều hướng tới lại sau này. Không nên giải phóng dữ liệu cần thiết cho lần hiển thị tiếp theo.
   */
  default void onNavigatedFrom() {}
}
