package com.auction.util;

import com.auction.dto.BidUpdateMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton theo dõi bid trong nền cho các phiên đấu giá mà user đã đặt giá nhưng đã rời màn hình
 * chi tiết.
 *
 * <p>Khi user rời {@code AuctionDetailController} mà vẫn còn bid, controller gọi {@link
 * #watch(Long, String, String, Long)} để đăng ký. BackgroundBidWatcher mở kết nối WebSocket riêng
 * cho phiên đó và đẩy thông báo qua {@link NotificationStore} khi có cập nhật.
 *
 * <p>Khi user quay lại màn hình chi tiết của phiên đó, {@link #stopWatching(Long)} được gọi để
 * tránh nhận event trùng với kết nối WebSocket của controller. {@link #stopAll()} được gọi khi đăng
 * xuất.
 */
public class BackgroundBidWatcher {

  private static final BackgroundBidWatcher INSTANCE = new BackgroundBidWatcher();
  private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundBidWatcher.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  /** Map auctionId → WebSocketClient đang watch. */
  private final Map<Long, WebSocketClient> watchers = new ConcurrentHashMap<>();

  private BackgroundBidWatcher() {}

  public static BackgroundBidWatcher getInstance() {
    return INSTANCE;
  }

  /**
   * Bắt đầu theo dõi một phiên đấu giá trong nền.
   *
   * <p>Nếu đã có watcher cho {@code auctionId}, phương thức này không-op (tránh kết nối đôi).
   *
   * @param auctionId ID phiên đấu giá cần theo dõi
   * @param token JWT token của user
   * @param itemName Tên sản phẩm (dùng cho nội dung thông báo)
   * @param userId ID user hiện tại
   */
  public void watch(Long auctionId, String token, String itemName, Long userId) {
    if (watchers.containsKey(auctionId)) {
      LOGGER.debug("BackgroundBidWatcher: đã watch auctionId={}, bỏ qua.", auctionId);
      return;
    }

    WebSocketClient client = new WebSocketClient();
    watchers.put(auctionId, client);

    String name = itemName != null ? itemName : "Phiên #" + auctionId;

    client.connect(
        auctionId,
        token,
        json -> {
          try {
            BidUpdateMessage msg = MAPPER.readValue(json, BidUpdateMessage.class);
            String type = msg.getType();
            if (type == null) {
              return;
            }

            switch (type) {
              case BidUpdateMessage.TYPE_BID_UPDATE -> {
                Long leaderId = msg.getLeadingBidderId();
                BigDecimal price = msg.getCurrentPrice();
                if (leaderId != null && !leaderId.equals(userId)) {
                  // Người khác vừa vượt giá của user
                  String notification = "⚠️ Bạn bị vượt giá tại " + name + ": " + price;
                  Platform.runLater(() -> NotificationStore.getInstance().add(notification));
                }
              }
              case BidUpdateMessage.TYPE_AUTO_BID_TRIGGERED -> {
                Long leaderId = msg.getLeadingBidderId();
                BigDecimal price = msg.getCurrentPrice();
                if (leaderId != null && leaderId.equals(userId)) {
                  // Auto-bid đặt thành công cho chính user này
                  String notification = "🤖 Auto-bid đặt " + price + " cho bạn tại " + name;
                  Platform.runLater(() -> NotificationStore.getInstance().add(notification));
                }
              }
              case BidUpdateMessage.TYPE_AUCTION_ENDED -> {
                String winner = msg.getLeadingBidderUsername();
                String notification =
                    "🏁 Phiên kết thúc: "
                        + name
                        + (winner != null ? " — Người thắng: " + winner : "");
                Platform.runLater(
                    () -> {
                      NotificationStore.getInstance().add(notification);
                      stopWatching(auctionId);
                    });
              }
              default -> {
                // TYPE_TIME_EXTENDED và các type khác: bỏ qua
              }
            }
          } catch (Exception e) {
            LOGGER.debug(
                "BackgroundBidWatcher parse error (auction={}): {}", auctionId, e.getMessage());
          }
        });

    LOGGER.info("BackgroundBidWatcher: bắt đầu watch auctionId={}", auctionId);
  }

  /**
   * Dừng theo dõi một phiên đấu giá cụ thể.
   *
   * <p>Thường được gọi khi user quay lại màn hình chi tiết của phiên đó.
   *
   * @param auctionId ID phiên đấu giá cần dừng
   */
  public void stopWatching(Long auctionId) {
    WebSocketClient client = watchers.remove(auctionId);
    if (client != null) {
      client.disconnect();
      LOGGER.info("BackgroundBidWatcher: dừng watch auctionId={}", auctionId);
    }
  }

  /** Dừng tất cả watcher — gọi khi người dùng đăng xuất. */
  public void stopAll() {
    watchers.forEach(
        (id, client) -> {
          client.disconnect();
          LOGGER.info("BackgroundBidWatcher: dừng watch auctionId={} (stopAll)", id);
        });
    watchers.clear();
  }
}
