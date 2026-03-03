package org.chenile.query.repository.impl;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ChenileMapperTest {

    public static class SimpleEntity {
        private String name;
        private BigDecimal amount;
        private LocalDate startDate;
        private LocalDateTime updatedAt;
        private UUID identifier;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public UUID getIdentifier() { return identifier; }
        public void setIdentifier(UUID identifier) { this.identifier = identifier; }
    }

    public static class NestedEntity {
        private String city;
        private Address address;

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public Address getAddress() { return address; }
        public void setAddress(Address address) { this.address = address; }
    }

    public static class Address {
        private String city;

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
    }

    @Test
    public void testModernJavaTypes() {
        Map<String, Object> row = new HashMap<>();
        row.put("name", "Test");
        row.put("amount", new BigDecimal("100.50"));
        row.put("startDate", LocalDate.of(2026, 1, 1));
        row.put("updatedAt", LocalDateTime.of(2026, 1, 1, 10, 0));
        row.put("identifier", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

        SimpleEntity entity = ChenileMapper.mapRowToEntity(row, SimpleEntity.class);

        assertNotNull(entity);
        assertEquals("Test", entity.getName());
        assertEquals(new BigDecimal("100.50"), entity.getAmount());
        assertEquals(LocalDate.of(2026, 1, 1), entity.getStartDate());
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), entity.getUpdatedAt());
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), entity.getIdentifier());
    }

    @Test
    public void testNestedFieldCollision() {
        Map<String, Object> row = new HashMap<>();
        row.put("city", "TopLevelCity"); // Should map to NestedEntity.city
        row.put("addressCity", "NestedCity"); // Should map to NestedEntity.address.city

        NestedEntity entity = ChenileMapper.mapRowToEntity(row, NestedEntity.class);

        assertNotNull(entity);
        assertEquals("TopLevelCity", entity.getCity());
        assertNotNull(entity.getAddress());
        assertEquals("NestedCity", entity.getAddress().getCity());
    }
}
