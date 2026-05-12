package com.auction.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.config.DatabaseConfig;
import com.auction.model.*;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;

/**
 * Test suite kiểm tra toàn bộ các thao tác của {@link ItemDao} — lớp DAO quản lý vật phẩm
 * đấu giá trên bảng {@code items}.
 *
 * <p><b>Phạm vi kiểm tra:</b>
 * <ul>
 *   <li>Insert đa hình: {@link Electronics}, {@link Art}, {@link Vehicle} — mỗi loại có trường
 *       đặc thù (brand, artist, year) phải được lưu và đọc lại đúng kiểu.</li>
 *   <li>Truy vấn: findById (trả về đúng subclass), findBySellerId, findAll,
 *       findByCategory, searchByName.</li>
 *   <li>Update và Delete theo ID.</li>
 *   <li>Kiểm tra quyền sở hữu: belongsToSeller.</li>
 * </ul>
 *
 * <p><b>Chiến lược dữ liệu:</b> Mỗi test chạy trong trạng thái DB sạch. {@code init()} TRUNCATE
 * toàn bộ bảng và tạo lại một Seller duy nhất làm owner cho tất cả Item trong test.
 *
 * <p><b>Điều kiện tiên quyết:</b> PostgreSQL phải đang chạy với thông tin kết nối được cấu hình
 * trong {@link DatabaseConfig}. Nếu không kết nối được, toàn bộ class bị bỏ qua (ABORTED).
 */
class ItemDaoTest {

  private static Jdbi jdbi;
  private static UserDao userDao;
  private static ItemDao itemDao;

  /** Người bán mặc định — owner của mọi Item được tạo trong class này. */
  private User testSeller;

  /**
   * Khởi tạo JDBI và các DAO một lần duy nhất cho cả class.
   *
   * <p>Nếu DB không khả dụng, class bị bỏ qua hoàn toàn qua {@link Assumptions#abort}
   * để tránh báo lỗi giả trong môi trường không có DB.
   */
  @BeforeAll
  static void setup() {
    try {
      jdbi = DatabaseConfig.create();
    } catch (Exception e) {
      Assumptions.abort("No DB available, skipping: " + e.getMessage());
    }
    userDao = new UserDao(jdbi);
    itemDao = new ItemDao(jdbi);
  }

  /**
   * Chuẩn bị trạng thái DB sạch và tạo seller mặc định trước mỗi test.
   *
   * <p><b>Bước 1 — Dọn dẹp:</b> TRUNCATE theo thứ tự con → cha để tuân thủ FK constraint.
   * {@code RESTART IDENTITY} trên {@code users} đưa sequence về 1 để ID luôn cố định.
   *
   * <p><b>Bước 2 — Seed dữ liệu:</b> Tạo Seller (id=1) làm owner cho mọi Item trong test.
   * Tất cả Item đều gắn với seller này nên các test về findBySellerId, belongsToSeller
   * không cần tạo seller riêng.
   */
  @BeforeEach
  void init() {
    // Bước 1: Dọn dẹp DB và đặt lại bộ đếm ID về 1 trước mỗi test case.
    jdbi.useHandle(
        handle -> {
          handle.execute("TRUNCATE TABLE auto_bid_configs CASCADE");
          handle.execute("TRUNCATE TABLE bid_transactions CASCADE");
          handle.execute("TRUNCATE TABLE auctions CASCADE");
          handle.execute("TRUNCATE TABLE items CASCADE");
          // RESTART IDENTITY đảm bảo ID bắt đầu từ 1 sau mỗi lần dọn dẹp.
          handle.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        });

    // Bước 2: Tạo seller mặc định — lúc này ID chắc chắn là 1 nhờ RESTART IDENTITY.
    testSeller = userDao.insert(new Seller("test_seller", "password123", "seller@test.com"));

    System.out.println("Cleaned DB & Created test seller with id: " + testSeller.getId());
  }

  /**
   * Kiểm tra insert Electronics: xác nhận ID được sinh, category là {@code ELECTRONICS},
   * đối tượng trả về là instance của {@link Electronics} và trường đặc thù {@code brand}
   * được lưu đúng.
   */
  @Test
  @DisplayName("Insert Electronics should work")
  void testInsertElectronics() {
    Electronics electronics =
        new Electronics("iPhone 15", "New phone", testSeller.getId(), "Apple");

    Item saved = itemDao.insert(electronics);

    assertNotNull(saved.getId());
    assertEquals("ELECTRONICS", saved.getCategory());
    assertTrue(saved instanceof Electronics);
    assertEquals("Apple", ((Electronics) saved).getBrand());
  }

  /**
   * Kiểm tra insert Art: xác nhận category là {@code ART}, đối tượng trả về là instance của
   * {@link Art} và trường đặc thù {@code artist} được lưu đúng.
   */
  @Test
  @DisplayName("Insert Art should work")
  void testInsertArt() {
    Art art = new Art("Mona Lisa", "Famous painting", testSeller.getId(), "Da Vinci");

    Item saved = itemDao.insert(art);

    assertNotNull(saved.getId());
    assertEquals("ART", saved.getCategory());
    assertTrue(saved instanceof Art);
    assertEquals("Da Vinci", ((Art) saved).getArtist());
  }

  /**
   * Kiểm tra insert Vehicle: xác nhận category là {@code VEHICLE}, đối tượng trả về là
   * instance của {@link Vehicle} và trường đặc thù {@code year} được lưu đúng.
   */
  @Test
  @DisplayName("Insert Vehicle should work")
  void testInsertVehicle() {
    Vehicle vehicle = new Vehicle("Camry", "Sedan", testSeller.getId(), 2022);

    Item saved = itemDao.insert(vehicle);

    assertNotNull(saved.getId());
    assertEquals("VEHICLE", saved.getCategory());
    assertTrue(saved instanceof Vehicle);
    assertEquals(2022, ((Vehicle) saved).getYear());
  }

  /**
   * Kiểm tra tính đa hình trong findById: DAO phải tự động map dữ liệu từ DB về đúng
   * subclass ({@link Electronics}) chứ không phải kiểu cha {@link Item}.
   * Trường đặc thù {@code brand} cũng phải đọc lại được sau khi đi qua DB.
   */
  @Test
  @DisplayName("FindById should return correct item type")
  void testFindById() {
    Electronics electronics =
        new Electronics("Test Laptop", "Gaming laptop", testSeller.getId(), "Dell");
    Item saved = itemDao.insert(electronics);

    Optional<Item> found = itemDao.findById(saved.getId());

    assertTrue(found.isPresent());
    assertTrue(found.get() instanceof Electronics);
    assertEquals("Test Laptop", found.get().getName());
    assertEquals("Dell", ((Electronics) found.get()).getBrand());
  }

  /**
   * Kiểm tra findBySellerId: sau khi insert 2 item thuộc cùng một seller, danh sách trả về
   * phải có đúng 2 phần tử.
   */
  @Test
  @DisplayName("FindBySellerId should return all items of a seller")
  void testFindBySellerId() {
    itemDao.insert(new Electronics("Laptop", "Dell", testSeller.getId(), "Dell"));
    itemDao.insert(new Art("Painting", "Art", testSeller.getId(), "Artist"));

    List<Item> items = itemDao.findBySellerId(testSeller.getId());

    assertEquals(2, items.size());
  }

  /**
   * Kiểm tra findAll: sau khi insert 1 item, danh sách không null và có ít nhất 1 phần tử.
   */
  @Test
  @DisplayName("FindAll should return all items")
  void testFindAll() {
    itemDao.insert(new Electronics("Item 1", "Desc", testSeller.getId(), "Brand"));
    List<Item> items = itemDao.findAll();
    assertNotNull(items);
    assertTrue(items.size() > 0);
  }

  /**
   * Kiểm tra findByCategory: sau khi insert cả Electronics và Art, mỗi category phải
   * chỉ trả về đúng các item thuộc category đó — không lẫn loại khác.
   */
  @Test
  @DisplayName("FindByCategory should filter by category")
  void testFindByCategory() {
    itemDao.insert(new Electronics("Phone", "Smartphone", testSeller.getId(), "Apple"));
    itemDao.insert(new Art("Sculpture", "Art", testSeller.getId(), "Artist"));

    List<Item> electronics = itemDao.findByCategory("ELECTRONICS");
    List<Item> arts = itemDao.findByCategory("ART");

    assertTrue(electronics.stream().allMatch(i -> "ELECTRONICS".equals(i.getCategory())));
    assertTrue(arts.stream().allMatch(i -> "ART".equals(i.getCategory())));
  }

  /**
   * Kiểm tra searchByName: tìm kiếm theo từ khóa {@code "Pro"} phải trả về tất cả item
   * có tên chứa chuỗi đó (case-sensitive tùy implementation). Cả 2 item được insert đều
   * có "Pro" trong tên, nên kết quả phải có ít nhất 2 phần tử và toàn bộ phải khớp điều kiện.
   */
  @Test
  @DisplayName("SearchByName should find items by keyword")
  void testSearchByName() {
    itemDao.insert(new Electronics("iPhone Pro Max", "Phone", testSeller.getId(), "Apple"));
    itemDao.insert(new Electronics("iPad Air Pro", "Tablet", testSeller.getId(), "Apple"));

    List<Item> results = itemDao.searchByName("Pro");

    assertTrue(results.size() >= 2);
    assertTrue(results.stream().allMatch(i -> i.getName().contains("Pro")));
  }

  /**
   * Kiểm tra update: sau khi thay đổi {@code name}, {@code description} và {@code brand},
   * tất cả giá trị mới phải được persist và đọc lại chính xác từ DB.
   */
  @Test
  @DisplayName("Update should modify item details")
  void testUpdate() {
    Electronics electronics =
        new Electronics("Old Name", "Old desc", testSeller.getId(), "Old Brand");
    Item saved = itemDao.insert(electronics);

    saved.setName("New Name");
    saved.setDescription("New desc");
    ((Electronics) saved).setBrand("New Brand");

    boolean updated = itemDao.update(saved);

    assertTrue(updated);

    Optional<Item> found = itemDao.findById(saved.getId());
    assertTrue(found.isPresent());
    assertEquals("New Name", found.get().getName());
    assertEquals("New Brand", ((Electronics) found.get()).getBrand());
  }

  /**
   * Kiểm tra delete: item bị xóa không còn tìm thấy qua findById.
   * {@code delete()} phải trả về {@code true} khi xóa thành công.
   */
  @Test
  @DisplayName("Delete should remove item")
  void testDelete() {
    Electronics electronics = new Electronics("To Delete", "Temp", testSeller.getId(), "Brand");
    Item saved = itemDao.insert(electronics);

    boolean deleted = itemDao.delete(saved.getId());

    assertTrue(deleted);
    Optional<Item> found = itemDao.findById(saved.getId());
    assertFalse(found.isPresent());
  }

  /**
   * Kiểm tra belongsToSeller: xác nhận item thuộc đúng seller của nó (trả về {@code true})
   * và không thuộc seller khác (trả về {@code false} với id=999 không tồn tại).
   */
  @Test
  @DisplayName("BelongsToSeller should verify ownership")
  void testBelongsToSeller() {
    Electronics electronics = new Electronics("My Item", "Desc", testSeller.getId(), "Brand");
    Item saved = itemDao.insert(electronics);

    assertTrue(itemDao.belongsToSeller(saved.getId(), testSeller.getId()));
    assertFalse(itemDao.belongsToSeller(saved.getId(), 999L));
  }
}
