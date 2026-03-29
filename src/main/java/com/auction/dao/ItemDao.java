package com.auction.dao;

import com.auction.model.Art;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Vehicle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * DAO (Data Access Object) cho bảng items.
 * 
 * <p>Class này chịu trách nhiệm giao tiếp với bảng items trong database.
 * Điểm đặc biệt của ItemDao là phải xử lý polymorphism: bảng items có cột
 * category để phân biệt loại sản phẩm (ELECTRONICS, ART, VEHICLE), và các
 * cột đặc thù (brand, artist, year) chỉ có giá trị cho từng loại.
 * 
 * <h3>Polymorphism trong ItemDao</h3>
 * <p>Khi đọc từ database, RowMapper phải dựa vào cột "category" để tạo đúng subclass:
 * <ul>
 *   <li>category = "ELECTRONICS" → new Electronics(..., brand, ...)</li>
 *   <li>category = "ART" → new Art(..., artist, ...)</li>
 *   <li>category = "VEHICLE" → new Vehicle(..., year, ...)</li>
 * </ul>
 * 
 * <p>Khi ghi vào database, ItemService sẽ gọi ItemFactory để tạo đúng subclass,
 * và DAO chỉ việc lấy các field chung + field đặc thù tương ứng.
 * 
 * <h3>Factory Method pattern liên kết</h3>
 * <p>ItemFactory (trong pattern/factory/) tạo Item subclass từ CreateItemRequest.
 * DAO chỉ đọc/ghi, không quyết định logic tạo object.
 * 
 * <h3>Liên kết với các file khác</h3>
 * <ul>
 *   <li><b>Item.java, Electronics.java, Art.java, Vehicle.java</b> — model classes</li>
 *   <li><b>ItemService.java</b> — gọi các method của DAO này</li>
 *   <li><b>ItemFactory.java</b> — tạo subclass từ request</li>
 *   <li><b>AuctionDao.java</b> — auctions tham chiếu đến items (item_id foreign key)</li>
 *   <li><b>V1__initial_schema.sql</b> — định nghĩa bảng items</li>
 * </ul>
 */
public class ItemDao {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemDao.class);
    private final Jdbi jdbi;
    
    public ItemDao(Jdbi jdbi) {
        this.jdbi = jdbi;
    }
    
    /**
     * RowMapper chuyển ResultSet thành Item object (subclass phù hợp).
     * 
     * <p>Đây là nơi thể hiện POLYMORPHISM khi đọc từ database.
     * Dựa vào cột "category", mapper tạo đúng subclass:
     * <ul>
     *   <li>ELECTRONICS: đọc thêm cột "brand"</li>
     *   <li>ART: đọc thêm cột "artist"</li>
     *   <li>VEHICLE: đọc thêm cột "year"</li>
     * </ul>
     * 
     * <p>Các cột không thuộc subclass sẽ bị bỏ qua (null hoặc 0).
     */
    private static class ItemMapper implements RowMapper<Item> {
        @Override
        public Item map(ResultSet rs, StatementContext ctx) throws SQLException {
            Long id = rs.getLong("id");
            String name = rs.getString("name");
            String description = rs.getString("description");
            Long sellerId = rs.getLong("seller_id");
            String category = rs.getString("category");
            LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
            
            switch (category) {
                case "ELECTRONICS":
                    String brand = rs.getString("brand");
                    return new Electronics(id, name, description, sellerId, brand, createdAt);
                    
                case "ART":
                    String artist = rs.getString("artist");
                    return new Art(id, name, description, sellerId, artist, createdAt);
                    
                case "VEHICLE":
                    int year = rs.getInt("year");
                    return new Vehicle(id, name, description, sellerId, year, createdAt);
                    
                default:
                    LOGGER.warn("Unknown category: {}, returning null", category);
                    return null;
            }
        }
    }
    
    // ============================================================
    // CREATE (INSERT)
    // ============================================================
    
    /**
     * Thêm sản phẩm mới vào database.
     * 
     * <p>Method này xử lý cả 3 loại sản phẩm (Electronics, Art, Vehicle).
     * Tùy vào subclass thực tế của item, nó sẽ lấy các field đặc thù tương ứng.
     * 
     * <p>Luồng xử lý:
     * <ol>
     *   <li>ItemService nhận CreateItemRequest, dùng ItemFactory tạo Item subclass</li>
     *   <li>Gọi itemDao.insert(item) với item có id = null</li>
     *   <li>JDBI thực thi INSERT với các field phù hợp</li>
     *   <li>Trả về item đã có id</li>
     * </ol>
     * 
     * @param item đối tượng Item (Electronics/Art/Vehicle, chưa có id)
     * @return Item đã được gán id từ database
     */
    public Item insert(Item item) {
        String sql = """
            INSERT INTO items (name, description, seller_id, category, 
                               brand, artist, year, created_at, updated_at)
            VALUES (:name, :description, :sellerId, :category,
                    :brand, :artist, :year, :createdAt, :updatedAt)
            RETURNING id
            """;
        
        return jdbi.withHandle(handle -> {
            long id = handle.createQuery(sql)
                    .bind("name", item.getName())
                    .bind("description", item.getDescription())
                    .bind("sellerId", item.getSellerId())
                    .bind("category", item.getCategory())
                    .bind("brand", getBrand(item))
                    .bind("artist", getArtist(item))
                    .bind("year", getYear(item))
                    .bind("createdAt", item.getCreatedAt())
                    .bind("updatedAt", item.getCreatedAt()) // mới tạo, updatedAt = createdAt
                    .mapTo(Long.class)
                    .one();
            
            item.setId(id);
            LOGGER.debug("Inserted item: id={}, name={}, category={}, seller={}", 
                    id, item.getName(), item.getCategory(), item.getSellerId());
            return item;
        });
    }
    
    /**
     * Lấy brand nếu item là Electronics, ngược lại trả về null.
     */
    private String getBrand(Item item) {
        if (item instanceof Electronics) {
            return ((Electronics) item).getBrand();
        }
        return null;
    }
    
    /**
     * Lấy artist nếu item là Art, ngược lại trả về null.
     */
    private String getArtist(Item item) {
        if (item instanceof Art) {
            return ((Art) item).getArtist();
        }
        return null;
    }
    
    /**
     * Lấy year nếu item là Vehicle, ngược lại trả về 0.
     */
    private int getYear(Item item) {
        if (item instanceof Vehicle) {
            return ((Vehicle) item).getYear();
        }
        return 0;
    }
    
    // ============================================================
    // READ (SELECT)
    // ============================================================
    
    /**
     * Tìm sản phẩm theo ID.
     * 
     * @param id ID của sản phẩm
     * @return Optional chứa Item nếu tìm thấy, Optional.empty() nếu không
     */
    public Optional<Item> findById(Long id) {
        String sql = """
            SELECT id, name, description, seller_id, category, 
                   brand, artist, year, created_at, updated_at
            FROM items
            WHERE id = :id
            """;
        
        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("id", id)
                    .map(new ItemMapper())
                    .findOne()
        );
    }
    
    /**
     * Lấy tất cả sản phẩm của một người bán.
     * 
     * <p>Dùng cho:
     * <ul>
     *   <li>Seller xem danh sách sản phẩm của mình</li>
     *   <li>Khi tạo auction, chỉ hiển thị items của seller đó</li>
     * </ul>
     * 
     * @param sellerId ID của người bán
     * @return List các Item của seller đó
     */
    public List<Item> findBySellerId(Long sellerId) {
        String sql = """
            SELECT id, name, description, seller_id, category, 
                   brand, artist, year, created_at, updated_at
            FROM items
            WHERE seller_id = :sellerId
            ORDER BY created_at DESC
            """;
        
        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("sellerId", sellerId)
                    .map(new ItemMapper())
                    .list()
        );
    }
    
    /**
     * Lấy tất cả sản phẩm trong hệ thống.
     * 
     * <p>Dùng cho:
     * <ul>
     *   <li>Admin Panel: quản lý sản phẩm</li>
     *   <li>Màn hình tìm kiếm sản phẩm</li>
     * </ul>
     * 
     * @return List tất cả Item
     */
    public List<Item> findAll() {
        String sql = """
            SELECT id, name, description, seller_id, category, 
                   brand, artist, year, created_at, updated_at
            FROM items
            ORDER BY created_at DESC
            """;
        
        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .map(new ItemMapper())
                    .list()
        );
    }
    
    /**
     * Lấy sản phẩm theo category.
     * 
     * @param category "ELECTRONICS", "ART", hoặc "VEHICLE"
     * @return List các Item thuộc category đó
     */
    public List<Item> findByCategory(String category) {
        String sql = """
            SELECT id, name, description, seller_id, category, 
                   brand, artist, year, created_at, updated_at
            FROM items
            WHERE category = :category
            ORDER BY created_at DESC
            """;
        
        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("category", category)
                    .map(new ItemMapper())
                    .list()
        );
    }
    
    /**
     * Tìm kiếm sản phẩm theo tên (chứa keyword, không phân biệt hoa thường).
     * 
     * <p>Dùng cho chức năng tìm kiếm sản phẩm trên client.
     * 
     * @param keyword từ khóa cần tìm
     * @return List các Item có tên chứa keyword
     */
    public List<Item> searchByName(String keyword) {
        String sql = """
            SELECT id, name, description, seller_id, category, 
                   brand, artist, year, created_at, updated_at
            FROM items
            WHERE LOWER(name) LIKE LOWER(:keyword)
            ORDER BY created_at DESC
            """;
        
        String searchPattern = "%" + keyword + "%";
        
        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("keyword", searchPattern)
                    .map(new ItemMapper())
                    .list()
        );
    }
    
    // ============================================================
    // UPDATE
    // ============================================================
    
    /**
     * Cập nhật thông tin sản phẩm.
     * 
     * <p>Seller có thể sửa tên, mô tả, và các field đặc thù của sản phẩm.
     * Không cho phép sửa seller_id (không thể chuyển sản phẩm sang người khác).
     * 
     * @param item Item đã được cập nhật (phải có id)
     * @return true nếu cập nhật thành công, false nếu không tìm thấy item
     */
    public boolean update(Item item) {
        String sql = """
            UPDATE items
            SET name = :name,
                description = :description,
                brand = :brand,
                artist = :artist,
                year = :year,
                updated_at = :updatedAt
            WHERE id = :id
            """;
        
        int rowsAffected = jdbi.withHandle(handle ->
            handle.createUpdate(sql)
                    .bind("name", item.getName())
                    .bind("description", item.getDescription())
                    .bind("brand", getBrand(item))
                    .bind("artist", getArtist(item))
                    .bind("year", getYear(item))
                    .bind("updatedAt", LocalDateTime.now())
                    .bind("id", item.getId())
                    .execute()
        );
        
        if (rowsAffected > 0) {
            LOGGER.debug("Updated item: id={}, name={}", item.getId(), item.getName());
            return true;
        }
        
        LOGGER.warn("Item not found for update: id={}", item.getId());
        return false;
    }
    
    // ============================================================
    // DELETE
    // ============================================================
    
    /**
     * Xóa sản phẩm.
     * 
     * <p><b>Lưu ý:</b> Do có ON DELETE CASCADE trong bảng auctions,
     * khi xóa item, tất cả phiên đấu giá liên quan cũng bị xóa.
     * 
     * <p>Chỉ Seller (chủ sở hữu) hoặc Admin mới có quyền xóa.
     * 
     * @param id ID của sản phẩm cần xóa
     * @return true nếu xóa thành công, false nếu không tìm thấy
     */
    public boolean delete(Long id) {
        String sql = "DELETE FROM items WHERE id = :id";
        
        int rowsAffected = jdbi.withHandle(handle ->
            handle.createUpdate(sql)
                    .bind("id", id)
                    .execute()
        );
        
        if (rowsAffected > 0) {
            LOGGER.info("Deleted item: id={}", id);
            return true;
        }
        
        LOGGER.warn("Item not found for deletion: id={}", id);
        return false;
    }
    
    /**
     * Xóa tất cả sản phẩm của một người bán.
     * 
     * <p><b>CHỈ DÙNG CHO TEST.</b> Không gọi trong production.
     * 
     * @param sellerId ID của người bán
     * @return số lượng sản phẩm bị xóa
     */
    public int deleteBySellerId(Long sellerId) {
        String sql = "DELETE FROM items WHERE seller_id = :sellerId";
        
        int rowsAffected = jdbi.withHandle(handle ->
            handle.createUpdate(sql)
                    .bind("sellerId", sellerId)
                    .execute()
        );
        
        LOGGER.debug("Deleted {} items for seller: {}", rowsAffected, sellerId);
        return rowsAffected;
    }
    
    // ============================================================
    // HELPER METHODS
    // ============================================================
    
    /**
     * Kiểm tra sản phẩm có tồn tại không.
     * 
     * @param id ID cần kiểm tra
     * @return true nếu tồn tại, false nếu không
     */
    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM items WHERE id = :id";
        
        long count = jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("id", id)
                    .mapTo(Long.class)
                    .one()
        );
        
        return count > 0;
    }
    
    /**
     * Kiểm tra sản phẩm có thuộc về seller không.
     * 
     * <p>Dùng để xác thực khi Seller muốn sửa/xóa item hoặc tạo auction.
     * 
     * @param itemId ID sản phẩm
     * @param sellerId ID người bán
     * @return true nếu sản phẩm thuộc về seller đó
     */
    public boolean belongsToSeller(Long itemId, Long sellerId) {
        String sql = "SELECT COUNT(*) FROM items WHERE id = :itemId AND seller_id = :sellerId";
        
        long count = jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("itemId", itemId)
                    .bind("sellerId", sellerId)
                    .mapTo(Long.class)
                    .one()
        );
        
        return count > 0;
    }
    
    /**
     * Lấy số lượng sản phẩm của một seller.
     * 
     * @param sellerId ID người bán
     * @return số lượng sản phẩm
     */
    public int countBySellerId(Long sellerId) {
        String sql = "SELECT COUNT(*) FROM items WHERE seller_id = :sellerId";
        
        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                    .bind("sellerId", sellerId)
                    .mapTo(Integer.class)
                    .one()
        );
    }
}