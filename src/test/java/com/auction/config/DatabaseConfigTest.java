package com.auction.config;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigTest {

    private static Jdbi jdbi;

    @BeforeAll
    static void setup() {
        jdbi = DatabaseConfig.create();
    }

    @Test
    @DisplayName("Database connection should work")
    void testConnection() {
        assertDoesNotThrow(() -> {
            boolean result = jdbi.withHandle(handle ->
                handle.createQuery("SELECT 1")
                    .mapTo(Integer.class)
                    .one() == 1
            );
            assertTrue(result);
        });
    }

    @Test
    @DisplayName("Should have 5 tables")
    void testTableCount() {
        // In ra danh sách các bảng để debug
        System.out.println("=== Danh sách bảng trong database ===");
        var tables = jdbi.withHandle(handle ->
            handle.createQuery(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
            ).mapTo(String.class).list()
        );
        
        System.out.println("Số bảng tìm thấy: " + tables.size());
        tables.forEach(table -> System.out.println("  - " + table));
        
        assertEquals(5, tables.size(), 
            "Cần có 5 bảng, nhưng tìm thấy " + tables.size() + " bảng: " + tables);
    }
}