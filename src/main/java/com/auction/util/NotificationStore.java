package com.auction.util;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton lưu trữ danh sách thông báo bid trong phiên làm việc hiện tại.
 *
 * <h2>Nguồn dữ liệu</h2>
 *
 * Có hai nguồn đẩy thông báo vào store:
 *
 * <ul>
 *   <li>{@link UserBalanceWatcher} — thông báo liên quan đến biến động số dư (deposit được duyệt /
 *       từ chối), bao gồm cả thông báo offline load khi vừa đăng nhập.
 *   <li>{@link BackgroundBidWatcher} — thông báo realtime khi bị vượt giá, auto-bid kích hoạt, hoặc
 *       phiên đấu giá kết thúc trong khi user đã rời màn hình chi tiết.
 * </ul>
 *
 * <p><b>Quan trọng:</b> Chỉ được gọi {@link #add(String)} từ <em>JavaFX Application Thread</em> (FX
 * thread). Các caller từ background thread phải bọc trong {@code Platform.runLater()}.
 *
 * <h2>Unread count</h2>
 *
 * {@link #unreadCountProperty()} là {@link ReadOnlyIntegerProperty} có thể bind trực tiếp vào UI
 * (badge, label...). Count tăng mỗi khi một {@link NotificationItem} chưa đọc được thêm vào và
 * reset về 0 khi {@link #markAllRead()} hoàn tất với server.
 *
 * <h2>Persistence</h2>
 *
 * {@link #markAllRead()} gọi {@code PATCH /api/notifications/mark-all-read} trên background thread
 * rồi cập nhật local state sau khi server xác nhận. State đọc/chưa đọc là sự thật từ server — không
 * dùng {@code java.util.prefs.Preferences}.
 *
 * <h2>Vòng đời</h2>
 *
 * Store tồn tại trong suốt phiên. Khi user đăng xuất, {@link
 * com.auction.ui.util.SceneManager#logout()} gọi {@link #clear()} để xóa toàn bộ thông báo trong bộ
 * nhớ (không ghi Preferences).
 *
 * @see UserBalanceWatcher
 * @see BackgroundBidWatcher
 * @see com.auction.ui.util.SceneManager#logout()
 */
public class NotificationStore {

  private static final NotificationStore INSTANCE = new NotificationStore();
  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationStore.class);

  /**
   * Danh sách thông báo theo thứ tự thêm vào ngược (thông báo mới nhất ở index 0).
   *
   * <p>{@link ObservableList} cho phép UI (ListView, badge...) tự động cập nhật khi list thay đổi
   * mà không cần polling hay callback thủ công.
   */
  private final ObservableList<NotificationItem> notifications =
      FXCollections.observableArrayList();

  /**
   * Số lượng thông báo chưa đọc — tăng khi item chưa đọc được thêm vào, reset khi {@link
   * #markAllRead()} thành công.
   */
  private final SimpleIntegerProperty unreadCount = new SimpleIntegerProperty(0);

  private NotificationStore() {}

  /**
   * Trả về instance duy nhất của {@code NotificationStore}.
   *
   * @return singleton instance
   */
  public static NotificationStore getInstance() {
    return INSTANCE;
  }

  /**
   * Thêm một thông báo client-only (WebSocket / local) vào đầu danh sách.
   *
   * <p>Tạo một {@link NotificationItem} không có server id, chưa đọc, timestamped now, rồi gọi
   * {@link #add(NotificationItem)}.
   *
   * @param notification nội dung thông báo
   */
  public void add(String notification) {
    add(NotificationItem.clientOnly(notification));
  }

  /**
   * Thêm một {@link NotificationItem} vào đầu danh sách và tăng unread count nếu chưa đọc.
   *
   * <p>Dedup: bỏ qua nếu một item có cùng {@code id} (server-persisted) hoặc cùng {@code message}
   * (client-only) đã tồn tại trong 5 phần tử đầu.
   *
   * <p><b>Thread safety:</b> Phải gọi từ FX thread.
   *
   * @param item notification item cần thêm
   */
  public void add(NotificationItem item) {
    int checkLimit = Math.min(notifications.size(), 5);
    for (int i = 0; i < checkLimit; i++) {
      NotificationItem existing = notifications.get(i);
      if (item.getId() != null && item.getId().equals(existing.getId())) {
        return;
      }
      if (item.getId() == null && item.getMessage().equals(existing.getMessage())) {
        return;
      }
    }
    notifications.add(0, item);
    if (!item.isRead()) {
      unreadCount.set(unreadCount.get() + 1);
    }
  }

  /**
   * Trả về số lượng thông báo hiện chưa được đọc.
   *
   * @return số thông báo chưa đọc (luôn {@code >= 0})
   */
  public int getUnreadCount() {
    return unreadCount.get();
  }

  /**
   * Trả về {@link ReadOnlyIntegerProperty} của unread count — dùng để bind vào UI hoặc đăng ký
   * {@code ChangeListener}.
   *
   * @return read-only property của unread count
   */
  public ReadOnlyIntegerProperty unreadCountProperty() {
    return unreadCount;
  }

  /**
   * Đánh dấu tất cả thông báo là đã đọc: gọi {@code PATCH /api/notifications/mark-all-read} trên
   * background thread, sau khi server xác nhận thì cập nhật local state trên FX thread.
   *
   * <p>Nếu gọi API thất bại, log warning nhưng không revert — trạng thái sẽ đúng lại ở lần login
   * tiếp theo.
   */
  public void markAllRead() {
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                var response = RestClient.patch("/api/notifications/mark-all-read", null);
                if (response.statusCode() < 400) {
                  Platform.runLater(
                      () -> {
                        notifications.forEach(item -> item.setRead(true));
                        unreadCount.set(0);
                      });
                } else {
                  LOGGER.warn("markAllRead: server returned HTTP {}", response.statusCode());
                }
              } catch (Exception e) {
                LOGGER.warn("markAllRead failed: {}", e.getMessage());
              }
            });
  }

  /**
   * Trả về danh sách thông báo dưới dạng {@link ObservableList} — có thể bind trực tiếp vào {@code
   * ListView} hoặc các control khác trong JavaFX.
   *
   * @return observable list các {@link NotificationItem}, index 0 là thông báo mới nhất
   */
  public ObservableList<NotificationItem> getNotifications() {
    return notifications;
  }

  /**
   * Xóa toàn bộ thông báo trong bộ nhớ và reset unread count về 0.
   *
   * <p>Không ghi bất kỳ giá trị nào vào {@code Preferences}. Được gọi bởi {@link
   * com.auction.ui.util.SceneManager#logout()} khi người dùng đăng xuất.
   */
  public void clear() {
    notifications.clear();
    unreadCount.set(0);
  }
}
