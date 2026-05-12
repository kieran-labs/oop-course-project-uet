-- ============================================================
-- Migration V7: Thêm cột increment_amount vào auto_bid_configs
-- Mô tả : Fix lỗi thiếu cột do database cũ được tạo trước khi
--         schema V1 có định nghĩa cột này.
-- ============================================================

ALTER TABLE auto_bid_configs
    ADD COLUMN IF NOT EXISTS increment_amount DECIMAL(15,2) NOT NULL DEFAULT 0;