package com.auction.pattern.factory;

import com.auction.dto.CreateItemRequest;
import com.auction.model.Art;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Vehicle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemFactoryTest {

    private final Long SELLER_ID = 1L;

    @Test
    void testCreateElectronics() {
        CreateItemRequest req = new CreateItemRequest();
        req.setName("iPhone 15");
        req.setDescription("Mới 99%");
        req.setCategory("ELECTRONICS");
        req.setCategoryDetail("Apple");

        // Đã sửa: Tách cái hộp req ra thành 5 tham số rời rạc để chiều ý Dev C
        Item item = ItemFactory.create(
                req.getName(),
                req.getDescription(),
                SELLER_ID,
                req.getCategory(),
                req.getCategoryDetail()
        );

        assertNotNull(item);
        assertTrue(item instanceof Electronics, "Phải tạo ra đối tượng Electronics");
        Electronics electronics = (Electronics) item;
        assertEquals("Apple", electronics.getBrand(), "Brand (Thương hiệu) phải được map chính xác");
    }

    @Test
    void testCreateArt() {
        CreateItemRequest req = new CreateItemRequest();
        req.setName("Mona Lisa Replica");
        req.setDescription("Bản sao chép chuẩn"); // Thêm mô tả cho khỏi bị lỗi null
        req.setCategory("ART");
        req.setCategoryDetail("Leonardo da Vinci");

        Item item = ItemFactory.create(
                req.getName(),
                req.getDescription(),
                SELLER_ID,
                req.getCategory(),
                req.getCategoryDetail()
        );

        assertTrue(item instanceof Art, "Phải tạo ra đối tượng Art");
        Art art = (Art) item;
        assertEquals("Leonardo da Vinci", art.getArtist(), "Artist (Nghệ sĩ) phải được map chính xác");
    }

    @Test
    void testCreateVehicle() {
        CreateItemRequest req = new CreateItemRequest();
        req.setName("Toyota Camry");
        req.setDescription("Xe lướt chạy lướt sóng");
        req.setCategory("VEHICLE");
        req.setCategoryDetail("2024");

        Item item = ItemFactory.create(
                req.getName(),
                req.getDescription(),
                SELLER_ID,
                req.getCategory(),
                req.getCategoryDetail()
        );

        assertTrue(item instanceof Vehicle, "Phải tạo ra đối tượng Vehicle");
        Vehicle vehicle = (Vehicle) item;
        assertEquals(2024, vehicle.getYear(), "Year (Năm sản xuất) phải được map chính xác");
    }

    @Test
    void testInvalidCategory() {
        CreateItemRequest req = new CreateItemRequest();
        req.setName("Bàn ghế");
        req.setDescription("Gỗ lim xịn");
        req.setCategory("FURNITURE"); 

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ItemFactory.create(
                    req.getName(),
                    req.getDescription(),
                    SELLER_ID,
                    req.getCategory(),
                    req.getCategoryDetail()
            );
        });
        
        assertNotNull(exception);
    }
}