package com.auction.pattern.strategy;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logic xử lý tự động kết nối lại WebSocket khi bị rớt mạng. Retry tối đa 5 lần, mỗi lần cách nhau
 * 3 giây.
 */
public class WebSocketRetryStrategy {
  private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketRetryStrategy.class);

  private static final int MAX_RETRIES = 5;
  private static final int RETRY_DELAY_SECONDS = 3;
  private int currentAttempt = 0;

  private final Runnable connectAction; // Hành động kết nối do Frontend truyền vào
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  public WebSocketRetryStrategy(Runnable connectAction) {
    this.connectAction = connectAction;
  }

  /** Bắt đầu tiến trình thử kết nối lại. */
  public void attemptReconnect() {
    if (currentAttempt < MAX_RETRIES) {
      currentAttempt++;
      LOGGER.warn(
          "Kết nối WebSocket bị ngắt. Đang thử lại lần {}/{} sau {} giây...",
          currentAttempt,
          MAX_RETRIES,
          RETRY_DELAY_SECONDS);

      // Lên lịch chạy lại hàm connectAction sau 3 giây
      scheduler.schedule(
          () -> {
            try {
              connectAction.run();
              // Nếu kết nối thành công, bạn B sẽ gọi hàm reset()
            } catch (Exception e) {
              LOGGER.error("Thử kết nối lại thất bại: {}", e.getMessage());
              attemptReconnect(); // Đệ quy thử lại lần tiếp theo
            }
          },
          RETRY_DELAY_SECONDS,
          TimeUnit.SECONDS);

    } else {
      LOGGER.error(
          "Đã thử kết nối lại {} lần nhưng thất bại. Vui lòng kiểm tra lại mạng!", MAX_RETRIES);
      // Ở đây có thể bắn một sự kiện ra giao diện để báo người dùng F5 lại trang
    }
  }

  /** Gọi hàm này khi kết nối thành công để reset bộ đếm. */
  public void reset() {
    currentAttempt = 0;
    LOGGER.info("WebSocket đã kết nối ổn định.");
  }
}
