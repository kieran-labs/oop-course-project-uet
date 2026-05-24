package com.auction.ui.controller;

import com.auction.model.Item;
import com.auction.ui.util.Navigable;
import com.auction.ui.util.SceneManager;
import com.auction.util.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.DatePickerSkin;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller cho màn hình tạo phiên đấu giá (create-auction.fxml).
 *
 * <p><b>Mục đích:</b> Cho phép SELLER tạo phiên đấu giá mới bằng cách chọn sản phẩm, đặt giá khởi
 * điểm và lên lịch thời gian bắt đầu/kết thúc. Gửi request đến {@code POST /api/auctions}.
 *
 * <p><b>Các phương thức chính:</b>
 *
 * <ul>
 *   <li>{@link #onNavigatedTo()} — Load danh sách sản phẩm của seller vào ComboBox.
 *   <li>{@link #handleCreate()} — Validate và gửi request tạo phiên.
 *   <li>{@link #goToCreateItem()} — Chuyển sang màn hình tạo sản phẩm mới.
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b> CreateAuctionController phụ thuộc vào ItemController (GET
 * /api/items để lấy danh sách) và AuctionController (POST /api/auctions để tạo phiên).
 * AuctionScheduler phía server sẽ tự động chuyển trạng thái phiên từ OPEN → RUNNING → FINISHED.
 */
public class CreateAuctionController implements Navigable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateAuctionController.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

  @FXML private ComboBox<Item> itemCombo;
  @FXML private TextField startingPriceField;
  @FXML private DatePicker startDatePicker;
  @FXML private TextField startTimeField;
  @FXML private DatePicker endDatePicker;
  @FXML private TextField endTimeField;
  @FXML private Label statusLabel;
  @FXML private Button createButton;

  @FXML
  private void initialize() {
    installDatePickerMotion(startDatePicker);
    installDatePickerMotion(endDatePicker);
    configureDatePicker(startDatePicker);
    configureDatePicker(endDatePicker);
  }

  // ========== NAVIGABLE LIFECYCLE ==========

  /** Load danh sách sản phẩm của seller từ API khi vào màn hình. */
  @Override
  public void onNavigatedTo() {
    clearForm();
    loadMyItems();
  }

  // ========== FXML ACTIONS ==========

  /**
   * Xử lý tạo phiên đấu giá. Validate: sản phẩm đã chọn, giá > 0, thời gian hợp lệ, endTime >
   * startTime.
   */
  @FXML
  public void handleCreate() {
    Item selectedItem = itemCombo.getValue();
    String priceText = startingPriceField.getText().trim();
    LocalDate startDate = startDatePicker.getValue();
    String startTimeStr = startTimeField.getText().trim();
    LocalDate endDate = endDatePicker.getValue();
    String endTimeStr = endTimeField.getText().trim();

    if (selectedItem == null) {
      showStatus("Please choose a product.", true);
      return;
    }

    BigDecimal startingPrice;
    try {
      startingPrice = new BigDecimal(priceText.replace(",", ""));
      if (startingPrice.compareTo(BigDecimal.ZERO) <= 0) {
        showStatus("Starting price must be greater than 0.", true);
        return;
      }
    } catch (NumberFormatException e) {
      showStatus("That starting price doesn't look valid.", true);
      return;
    }

    if (startDate == null || startTimeStr.isEmpty()) {
      showStatus("Please enter a start date and time.", true);
      return;
    }
    if (endDate == null || endTimeStr.isEmpty()) {
      showStatus("Please enter an end date and time.", true);
      return;
    }

    LocalDateTime startTime;
    LocalDateTime endTime;
    try {
      startTime = LocalDateTime.of(startDate, LocalTime.parse(startTimeStr, TIME_FMT));
      endTime = LocalDateTime.of(endDate, LocalTime.parse(endTimeStr, TIME_FMT));
    } catch (DateTimeParseException e) {
      showStatus("Invalid time format. Please use HH:mm (e.g. 09:00).", true);
      return;
    }

    if (!endTime.isAfter(startTime)) {
      showStatus("End time must be after start time.", true);
      return;
    }

    createButton.setDisable(true);
    hideStatus();

    Map<String, Object> body = new HashMap<>();
    body.put("itemId", selectedItem.getId());
    body.put("startingPrice", startingPrice);
    body.put("startTime", startTime.toString());
    body.put("endTime", endTime.toString());

    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.post("/api/auctions", body);
                if (response.statusCode() == 201) {
                  // FIX: Hiển thị thông báo thành công, sau đó navigate về auction-list
                  Platform.runLater(
                      () -> {
                        showStatus("Auction created successfully.", false);
                        createButton.setDisable(false);
                      });
                  Thread.sleep(1500);
                  Platform.runLater(
                      () -> SceneManager.getInstance().navigateTo("auction-list.fxml"));
                } else {
                  String msg = extractMessage(response.body(), "Couldn't create the auction.");
                  Platform.runLater(
                      () -> {
                        showStatus(msg, true);
                        createButton.setDisable(false);
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi tạo phiên đấu giá", e);
                Platform.runLater(
                    () -> {
                      showStatus("Unable to reach the server.", true);
                      createButton.setDisable(false);
                    });
              }
            });
  }

  /** Chuyển sang màn hình tạo sản phẩm mới. */
  @FXML
  public void goToCreateItem() {
    SceneManager.getInstance().navigateTo("create-item.fxml");
  }

  /** Quay lại danh sách phiên. */
  @FXML
  public void goBack() {
    SceneManager.getInstance().navigateBack("auction-list.fxml");
  }

  // ========== DATA LOADING ==========

  /** Load danh sách sản phẩm thuộc seller hiện tại từ GET /api/items?sellerId=X. */
  private void loadMyItems() {
    Long sellerId = SceneManager.getInstance().getCurrentUserId();
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                HttpResponse<String> response = RestClient.get("/api/items?sellerId=" + sellerId);
                if (response.statusCode() == 200) {
                  List<Item> items = RestClient.parseList(response.body(), Item.class);
                  Platform.runLater(
                      () -> {
                        itemCombo.setItems(FXCollections.observableArrayList(items));
                        itemCombo.setCellFactory(
                            lv ->
                                new ListCell<>() {
                                  @Override
                                  protected void updateItem(Item item, boolean empty) {
                                    super.updateItem(item, empty);
                                    setText(empty || item == null ? null : item.getName());
                                  }
                                });
                        itemCombo.setButtonCell(
                            new ListCell<>() {
                              @Override
                              protected void updateItem(Item item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(
                                    empty || item == null
                                        ? "Choose one of your products"
                                        : item.getName());
                              }
                            });
                        if (items.isEmpty()) {
                          showStatus("You don't have any products yet. Create one first.", true);
                        }
                      });
                }
              } catch (Exception e) {
                LOGGER.error("Lỗi load danh sách sản phẩm", e);
                Platform.runLater(() -> showStatus("Couldn't load your product list.", true));
              }
            });
  }

  // ========== PRIVATE HELPERS ==========

  /**
   * Hiển thị thông báo kết quả trên statusLabel, áp dụng CSS class tương ứng.
   *
   * @param msg nội dung thông báo
   * @param isError {@code true} → class {@code error-label} (đỏ); {@code false} → {@code
   *     status-label} (xanh)
   */
  private void showStatus(String msg, boolean isError) {
    statusLabel.setText(msg);
    statusLabel.setStyle("");
    statusLabel.getStyleClass().setAll(isError ? "error-label" : "status-label");
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);
  }

  /** Ẩn statusLabel, giải phóng layout space và reset về CSS class mặc định. */
  private void hideStatus() {
    statusLabel.setVisible(false);
    statusLabel.setManaged(false);
    statusLabel.setStyle("");
    statusLabel.getStyleClass().setAll("status-label");
  }

  /**
   * Xóa trắng toàn bộ form và reset trạng thái nút tạo phiên. Gọi trong {@link #onNavigatedTo()} để
   * đảm bảo form sạch mỗi lần vào màn hình.
   */
  private void clearForm() {
    if (itemCombo != null) {
      itemCombo.setValue(null);
    }
    if (startingPriceField != null) {
      startingPriceField.clear();
    }
    if (startDatePicker != null) {
      startDatePicker.setValue(null);
    }
    if (startTimeField != null) {
      startTimeField.clear();
    }
    if (endDatePicker != null) {
      endDatePicker.setValue(null);
    }
    if (endTimeField != null) {
      endTimeField.clear();
    }
    hideStatus();
    if (createButton != null) {
      createButton.setDisable(false);
    }
  }

  /**
   * Trích xuất trường {@code message} từ JSON body phản hồi lỗi của server. Trả về {@code fallback}
   * nếu body không hợp lệ.
   */
  private String extractMessage(String body, String fallback) {
    try {
      return MAPPER.readTree(body).path("message").asText(fallback);
    } catch (Exception e) {
      return fallback;
    }
  }

  /**
   * Adds a subtle scale pulse when the picker gains focus or opens, so the field feels consistent
   * with the rest of the animated UI.
   */
  private void installDatePickerMotion(DatePicker picker) {
    if (picker == null) {
      return;
    }

    picker
        .focusedProperty()
        .addListener(
            (obs, wasFocused, isFocused) ->
                Platform.runLater(
                    () -> {
                      if (!picker.isShowing()) {
                        animateDatePicker(picker, isFocused);
                      }
                    }));
  }

  private void animateDatePicker(DatePicker picker, boolean active) {
    ScaleTransition existing = (ScaleTransition) picker.getProperties().get("datePickerPulse");
    if (existing != null) {
      existing.stop();
    }

    ScaleTransition pulse = new ScaleTransition(Duration.millis(active ? 160 : 120), picker);
    pulse.setToX(active ? 1.01 : 1.0);
    pulse.setToY(active ? 1.01 : 1.0);
    pulse.setInterpolator(Interpolator.EASE_OUT);
    picker.getProperties().put("datePickerPulse", pulse);
    pulse.playFromStart();
  }

  private void configureDatePicker(DatePicker picker) {
    if (picker == null) {
      return;
    }

    if (!picker.getStyleClass().contains("auction-calendar-glass-picker")) {
      picker.getStyleClass().add("auction-calendar-glass-picker");
    }

    GlassCalendarState state = new GlassCalendarState();
    picker.getProperties().put("glassCalendarState", state);

    picker.setDayCellFactory(dp -> new GlassDateCell(picker, state));
    picker
        .skinProperty()
        .addListener(
            (obs, oldSkin, newSkin) ->
                Platform.runLater(() -> applyPopupStyleWithRetry(picker, 3)));
    picker
        .showingProperty()
        .addListener(
            (obs, wasShowing, isShowing) -> {
              if (isShowing) {
                ScaleTransition pulse =
                    (ScaleTransition) picker.getProperties().get("datePickerPulse");
                if (pulse != null) {
                  pulse.stop();
                }
                picker.setScaleX(1.0);
                picker.setScaleY(1.0);
                applyPopupStyleWithRetry(picker, 3);
                freezePopupAnchorWithRetry(picker, 5);
              }
            });
  }

  /**
   * Capture the popup window's coordinates once it is stable, then forcibly hold them via an {@link
   * javafx.animation.AnimationTimer} that runs every frame. This is more aggressive than a
   * ChangeListener because it catches movements that happen between property writes and listener
   * firings, and bypasses any property-binding shenanigans inside JavaFX's popup machinery. The
   * visible symptom we are killing: the popup window slides 1-2px horizontally as the cursor moves
   * across day cells, because picker.localToScreen() fluctuates as the picker's parent re-layouts
   * on every cell-hover state change.
   */
  private void freezePopupAnchorWithRetry(DatePicker picker, int retries) {
    if (retries <= 0 || !picker.isShowing()) {
      LOGGER.info("[freeze] giving up: retries={} showing={}", retries, picker.isShowing());
      return;
    }
    Platform.runLater(
        () -> {
          if (!picker.isShowing()) {
            return;
          }
          if (!(picker.getSkin() instanceof DatePickerSkin skin)) {
            freezePopupAnchorWithRetry(picker, retries - 1);
            return;
          }
          Region popupContent = (Region) skin.getPopupContent();
          if (popupContent == null || popupContent.getScene() == null) {
            freezePopupAnchorWithRetry(picker, retries - 1);
            return;
          }
          javafx.stage.Window window = popupContent.getScene().getWindow();
          if (window == null) {
            freezePopupAnchorWithRetry(picker, retries - 1);
            return;
          }
          double x = window.getX();
          double y = window.getY();
          if (Double.isNaN(x) || Double.isNaN(y) || x <= 0 || y <= 0) {
            freezePopupAnchorWithRetry(picker, retries - 1);
            return;
          }
          LOGGER.info(
              "[freeze] locking popup of class {} at x={} y={}", window.getClass().getName(), x, y);
          applyWindowPositionLock(window, picker, x, y);
        });
  }

  private void applyWindowPositionLock(
      javafx.stage.Window window, DatePicker picker, double lockedX, double lockedY) {
    if (Boolean.TRUE.equals(window.getProperties().get("popupPositionLocked"))) {
      return;
    }
    window.getProperties().put("popupPositionLocked", Boolean.TRUE);

    javafx.animation.AnimationTimer timer =
        new javafx.animation.AnimationTimer() {
          @Override
          public void handle(long now) {
            if (window.getX() != lockedX) {
              window.setX(lockedX);
            }
            if (window.getY() != lockedY) {
              window.setY(lockedY);
            }
            if (window instanceof javafx.stage.PopupWindow popup) {
              if (popup.getAnchorX() != lockedX) {
                popup.setAnchorX(lockedX);
              }
              if (popup.getAnchorY() != lockedY) {
                popup.setAnchorY(lockedY);
              }
            }
          }
        };
    timer.start();

    javafx.beans.value.ChangeListener<Boolean> cleanup =
        new javafx.beans.value.ChangeListener<>() {
          @Override
          public void changed(
              javafx.beans.value.ObservableValue<? extends Boolean> obs,
              Boolean wasShowing,
              Boolean isShowing) {
            if (!isShowing) {
              timer.stop();
              window.getProperties().remove("popupPositionLocked");
              picker.showingProperty().removeListener(this);
            }
          }
        };
    picker.showingProperty().addListener(cleanup);
  }

  private void applyPopupStyleWithRetry(DatePicker picker, int attemptsRemaining) {
    if (picker == null || attemptsRemaining < 0) {
      return;
    }
    if (applyPopupStyle(picker)) {
      return;
    }
    Platform.runLater(() -> applyPopupStyleWithRetry(picker, attemptsRemaining - 1));
  }

  private boolean applyPopupStyle(DatePicker picker) {
    if (!(picker.getSkin() instanceof DatePickerSkin skin)) {
      return false;
    }
    Region popup = (Region) skin.getPopupContent();
    if (popup == null) {
      return false;
    }
    if (!popup.getStyleClass().contains("auction-calendar-glass-popup")) {
      popup.getStyleClass().add("auction-calendar-glass-popup");
    }
    lockPopupWidth(popup);
    return true;
  }

  /**
   * Lock popup width once it has been laid out. Without this, sub-pixel oscillations in the
   * GridPane's preferred width (caused by per-cell {@code requestParentLayout()} during hover
   * transitions) make ComboBoxPopupControl#reconfigurePopup re-anchor the popup horizontally each
   * pulse — the visible 1-2px lateral jitter when the cursor moves across day cells.
   */
  private void lockPopupWidth(Region popup) {
    if (popup.getProperties().containsKey("popupWidthLocked")) {
      return;
    }
    Runnable apply =
        () -> {
          double width = popup.getWidth();
          if (width <= 0) {
            return;
          }
          popup.setMinWidth(width);
          popup.setPrefWidth(width);
          popup.setMaxWidth(width);
          popup.getProperties().put("popupWidthLocked", Boolean.TRUE);
        };
    if (popup.getWidth() > 0) {
      apply.run();
      return;
    }
    popup
        .widthProperty()
        .addListener(
            new javafx.beans.value.ChangeListener<Number>() {
              @Override
              public void changed(
                  javafx.beans.value.ObservableValue<? extends Number> obs,
                  Number oldValue,
                  Number newValue) {
                if (newValue != null && newValue.doubleValue() > 0) {
                  popup.widthProperty().removeListener(this);
                  apply.run();
                }
              }
            });
  }

  private static final javafx.scene.layout.Border TRANSPARENT_CALENDAR_BORDER =
      new javafx.scene.layout.Border(
          new javafx.scene.layout.BorderStroke(
              Color.TRANSPARENT,
              javafx.scene.layout.BorderStrokeStyle.SOLID,
              new CornerRadii(999),
              new javafx.scene.layout.BorderWidths(1)));

  private static final Color DEFAULT_TEXT_COLOR = Color.rgb(51, 65, 85);

  private static final Background SELECTED_BG =
      new Background(
          new BackgroundFill(Color.rgb(35, 92, 226, 0.92), new CornerRadii(999), Insets.EMPTY),
          new BackgroundFill(Color.rgb(16, 102, 204, 0.84), new CornerRadii(999), new Insets(1.1)));
  private static final javafx.scene.layout.Border SELECTED_BORDER =
      new javafx.scene.layout.Border(
          new javafx.scene.layout.BorderStroke(
              Color.rgb(255, 255, 255, 0.52),
              javafx.scene.layout.BorderStrokeStyle.SOLID,
              new CornerRadii(999),
              new javafx.scene.layout.BorderWidths(1)));
  private static final Background TODAY_BG =
      new Background(
          new BackgroundFill(Color.rgb(255, 255, 255, 0.20), new CornerRadii(999), Insets.EMPTY),
          new BackgroundFill(
              Color.rgb(191, 219, 254, 0.64), new CornerRadii(999), new Insets(1.1)));
  private static final javafx.scene.layout.Border TODAY_BORDER =
      new javafx.scene.layout.Border(
          new javafx.scene.layout.BorderStroke(
              Color.rgb(37, 99, 235, 0.68),
              javafx.scene.layout.BorderStrokeStyle.SOLID,
              new CornerRadii(999),
              new javafx.scene.layout.BorderWidths(1)));
  private static final Background SELECTED_TODAY_BG =
      new Background(
          new BackgroundFill(Color.rgb(31, 111, 235, 0.98), new CornerRadii(999), Insets.EMPTY),
          new BackgroundFill(Color.rgb(15, 95, 184, 0.94), new CornerRadii(999), new Insets(1.1)));
  private static final javafx.scene.layout.Border SELECTED_TODAY_BORDER =
      new javafx.scene.layout.Border(
          new javafx.scene.layout.BorderStroke(
              Color.rgb(255, 255, 255, 0.70),
              javafx.scene.layout.BorderStrokeStyle.SOLID,
              new CornerRadii(999),
              new javafx.scene.layout.BorderWidths(1)));
  private static final Background HOVERED_BG =
      new Background(
          new BackgroundFill(Color.rgb(255, 255, 255, 0.18), new CornerRadii(999), Insets.EMPTY),
          new BackgroundFill(
              Color.rgb(191, 219, 254, 0.72), new CornerRadii(999), new Insets(1.1)));
  private static final javafx.scene.layout.Border HOVERED_BORDER =
      new javafx.scene.layout.Border(
          new javafx.scene.layout.BorderStroke(
              Color.rgb(96, 165, 250, 0.46),
              javafx.scene.layout.BorderStrokeStyle.SOLID,
              new CornerRadii(999),
              new javafx.scene.layout.BorderWidths(1)));

  private final class GlassDateCell extends DateCell {
    private final DatePicker picker;
    private final GlassCalendarState state;
    private final DropShadow shadow = new DropShadow();

    private GlassDateCell(DatePicker picker, GlassCalendarState state) {
      this.picker = picker;
      this.state = state;
      setFocusTraversable(false);
      shadow.setColor(Color.rgb(21, 101, 192, 0.12));
      shadow.setRadius(8);
      shadow.setOffsetY(1.0);

      // Lock cell size so the GridPane can't recompute column widths on hover. Without this, every
      // setBackground/setBorder on a cell triggers requestParentLayout on the GridPane; floating-
      // point rounding in the recomputed column widths shifts all cells to the right of the hovered
      // column sub-pixel — visible most strongly when the cursor is in the leftmost columns.
      Platform.runLater(this::lockSize);

      hoverProperty()
          .addListener(
              (obs, wasHover, isHover) -> {
                if (isHover) {
                  state.hoveredCell.set(this);
                  animateHover(state, 1.0);
                } else if (state.hoveredCell.get() == this) {
                  animateHover(state, 0.0);
                }
              });

      state.hoveredCell.addListener((obs, oldValue, newValue) -> refreshAppearance());
      state.hoverProgress.addListener((obs, oldValue, newValue) -> refreshAppearance());
      picker.valueProperty().addListener((obs, oldValue, newValue) -> refreshAppearance());
    }

    private void lockSize() {
      if (getProperties().containsKey("sizeLocked")) {
        return;
      }
      double w = getWidth();
      double h = getHeight();
      if (w <= 0 || h <= 0) {
        Platform.runLater(this::lockSize);
        return;
      }
      setMinSize(w, h);
      setPrefSize(w, h);
      setMaxSize(w, h);
      getProperties().put("sizeLocked", Boolean.TRUE);
    }

    @Override
    public void updateItem(LocalDate item, boolean empty) {
      super.updateItem(item, empty);
      refreshAppearance();
    }

    private void refreshAppearance() {
      applyDayCellStyle(this, picker, state, shadow);
    }
  }

  private static final class GlassCalendarState {
    private final javafx.beans.property.ObjectProperty<DateCell> hoveredCell =
        new javafx.beans.property.SimpleObjectProperty<>(null);
    private final DoubleProperty hoverProgress = new SimpleDoubleProperty(0.0);
    private final Timeline hoverTimeline = new Timeline();
  }

  private void animateHover(GlassCalendarState state, double target) {
    state.hoverTimeline.stop();
    state
        .hoverTimeline
        .getKeyFrames()
        .setAll(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(
                    state.hoverProgress, state.hoverProgress.get(), Interpolator.EASE_BOTH)),
            new KeyFrame(
                Duration.millis(target > state.hoverProgress.get() ? 185 : 145),
                new KeyValue(state.hoverProgress, target, Interpolator.EASE_BOTH)));
    if (target == 0.0) {
      DateCell leaving = state.hoveredCell.get();
      state.hoverTimeline.setOnFinished(
          e -> {
            if (state.hoveredCell.get() == leaving) {
              state.hoveredCell.set(null);
            }
          });
    } else {
      state.hoverTimeline.setOnFinished(null);
    }
    state.hoverTimeline.playFromStart();
  }

  private void applyDayCellStyle(
      DateCell cell, DatePicker picker, GlassCalendarState state, DropShadow shadow) {
    LocalDate item = cell.getItem();
    if (cell.isEmpty() || item == null) {
      cell.setBackground(Background.EMPTY);
      cell.setBorder(javafx.scene.layout.Border.EMPTY);
      cell.setEffect(null);
      cell.setOpacity(1.0);
      cell.setTextFill(Color.TRANSPARENT);
      return;
    }

    DateCell hovered = state.hoveredCell.get();
    boolean isHovered = hovered == cell;
    boolean hasHover = hovered != null;
    boolean selected = picker.getValue() != null && picker.getValue().equals(item);
    boolean today = LocalDate.now().equals(item);
    boolean previousMonth = cell.getStyleClass().contains("previous-month");
    boolean nextMonth = cell.getStyleClass().contains("next-month");
    boolean outOfMonth =
        previousMonth
            || nextMonth
            || (picker.getValue() != null && item.getMonth() != picker.getValue().getMonth());
    double hover = state.hoverProgress.get();

    Color text = DEFAULT_TEXT_COLOR;
    double opacity = 1.0;

    if (selected && today) {
      cell.setBackground(SELECTED_TODAY_BG);
      cell.setBorder(SELECTED_TODAY_BORDER);
      shadow.setColor(Color.rgb(21, 101, 192, 0.36));
      shadow.setRadius(12);
      shadow.setOffsetY(1.0);
      cell.setEffect(shadow);
      text = Color.WHITE;
    } else if (selected) {
      cell.setBackground(SELECTED_BG);
      cell.setBorder(SELECTED_BORDER);
      shadow.setColor(Color.rgb(21, 101, 192, 0.30 + hover * 0.06));
      shadow.setRadius(12);
      shadow.setOffsetY(1.0);
      cell.setEffect(shadow);
      text = Color.WHITE;
    } else if (today) {
      cell.setBackground(TODAY_BG);
      cell.setBorder(TODAY_BORDER);
      shadow.setColor(Color.rgb(21, 101, 192, 0.18 + hover * 0.04));
      shadow.setRadius(10);
      shadow.setOffsetY(1.0);
      cell.setEffect(shadow);
      text = Color.rgb(14, 91, 181);
    } else if (isHovered && hover > 0.001) {
      cell.setBackground(HOVERED_BG);
      cell.setBorder(HOVERED_BORDER);
      shadow.setColor(Color.rgb(21, 101, 192, 0.36 * hover));
      shadow.setRadius(16);
      shadow.setOffsetY(1.4);
      cell.setEffect(shadow);
      text = Color.rgb(15, 23, 42);
    } else {
      cell.setBackground(Background.EMPTY);
      cell.setBorder(TRANSPARENT_CALENDAR_BORDER);
      cell.setEffect(null);
      if (!isHovered && outOfMonth) {
        opacity = hasHover ? (0.45 + 0.10 * (1.0 - hover)) : 0.55;
      } else if (!isHovered && hasHover) {
        opacity = 0.58 + (0.42 * (1.0 - hover));
      }
    }

    cell.setTextFill(text);
    cell.setOpacity(opacity);
  }
}
