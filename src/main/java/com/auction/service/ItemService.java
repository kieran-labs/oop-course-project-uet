package com.auction.service;

import com.auction.dao.ItemDao;
import com.auction.dto.CreateItemRequest;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Item;
import com.auction.pattern.factory.ItemFactory;
import java.util.List;

/** Lớp Service xử lý logic nghiệp vụ cho Sản phẩm (Item). */
public class ItemService {

  private final ItemDao itemDao;

  // Inject DAO thông qua constructor
  public ItemService(ItemDao itemDao) {
    this.itemDao = itemDao;
  }

  /** Tạo mới một sản phẩm. Áp dụng Factory Method Pattern để khởi tạo đúng subclass. */
  public Item create(CreateItemRequest req, Long sellerId) {
    // 1. Gọi Factory để tạo đối tượng Item (Electronics, Art, hoặc Vehicle)
    Item newItem = ItemFactory.create(req, sellerId);

    // 2. Lưu xuống cơ sở dữ liệu thông qua DAO
    itemDao.insert(newItem);

    return newItem;
  }

  /** Lấy danh sách tất cả sản phẩm. */
  public List<Item> getAll() {
    return itemDao.findAll();
  }

  /** Lấy danh sách sản phẩm theo ID của người bán. */
  public List<Item> getBySellerId(Long sellerId) {
    return itemDao.findBySellerId(sellerId);
  }

  /** Lấy thông tin chi tiết một sản phẩm. Đã cập nhật xử lý Optional<Item> */
  public Item getById(Long id) {
    return itemDao
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm với ID: " + id));
  }

  /** Cập nhật thông tin sản phẩm. Logic quan trọng: Phải kiểm tra quyền sở hữu (Ownership). */
  public void update(Long itemId, CreateItemRequest req, Long currentUserId) {
    // 1. Lấy sản phẩm từ DB (sẽ tự động throw lỗi nếu không thấy nhờ hàm getById mới)
    Item item = getById(itemId);

    // 2. Kiểm tra quyền sở hữu: ID người đang đăng nhập có khớp với sellerId của sản phẩm không?
    if (!item.getSellerId().equals(currentUserId)) {
      throw new UnauthorizedException("Bạn không có quyền sửa sản phẩm của người khác!");
    }

    // 3. Cập nhật thông tin cơ bản
    item.setName(req.getName());
    item.setDescription(req.getDescription());

    // 4. Lưu lại xuống Database
    itemDao.update(item);
  }

  /** Xóa sản phẩm. Logic quan trọng: Kiểm tra quyền sở hữu trước khi xóa. */
  public void delete(Long itemId, Long currentUserId) {
    // 1. Lấy sản phẩm từ DB
    Item item = getById(itemId);

    // 2. Kiểm tra quyền sở hữu
    if (!item.getSellerId().equals(currentUserId)) {
      throw new UnauthorizedException("Bạn không có quyền xóa sản phẩm của người khác!");
    }

    // 3. Xóa khỏi Database
    itemDao.delete(itemId);
  }
}
