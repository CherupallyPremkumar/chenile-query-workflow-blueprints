package org.chenile.query.repository.impl;

import org.chenile.query.model.ResponseRow;
import org.chenile.query.model.SearchRequest;
import org.chenile.query.model.SearchResponse;
import org.chenile.query.service.SearchService;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ChenileRepositoryTest {

    public static class TestEntity {
        private String id;
        private String name;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private ChenileRepositoryImpl<TestEntity> repository;

    private SearchRequest<Map<String, Object>> lastSearchRequest;
    private SearchResponse mockSearchResponse;

    private SearchService<Map<String, Object>> searchService = new SearchService<>() {
        @Override
        public SearchResponse search(SearchRequest<Map<String, Object>> searchInput) {
            lastSearchRequest = searchInput;
            return mockSearchResponse;
        }

        @Override
        public SearchResponse doSearch(SearchRequest<Map<String, Object>> request) {
            lastSearchRequest = request;
            return mockSearchResponse;
        }
    };

    @Before
    public void setUp() {
        repository = new ChenileRepositoryImpl<>(TestEntity.class);
        repository.searchService = searchService;
        lastSearchRequest = null;
        mockSearchResponse = null;
    }

    @Test
    public void testFindByFilter() {
        SearchResponse response = new SearchResponse();
        response.setList(new ArrayList<>());
        mockSearchResponse = response;

        TestFilter filter = new TestFilter();
        filter.setName("SearchName");

        repository.findByFilter(filter);

        assertNotNull(lastSearchRequest);
        assertTrue(lastSearchRequest.getFilters().containsKey("name"));
        assertEquals("SearchName", lastSearchRequest.getFilters().get("name"));
    }

    @Test
    public void testSmartMapping() {
        SearchResponse response = new SearchResponse();
        List<ResponseRow> rows = new ArrayList<>();
        Map<String, Object> rowData = new HashMap<>();
        // Test flattened mapping
        rowData.put("priceAmount", 100.50); // Double
        rowData.put("currency", "USD"); // Direct nested field name

        ResponseRow row = new ResponseRow();
        row.setRow(rowData);
        rows.add(row);
        response.setList(rows);
        mockSearchResponse = response;

        ChenileRepositoryImpl<NestedEntity> nestedRepo = new ChenileRepositoryImpl<>(NestedEntity.class);
        nestedRepo.searchService = searchService;

        List<NestedEntity> results = nestedRepo.findByFilters(java.util.Collections.emptyMap());
        assertFalse(results.isEmpty());
        NestedEntity entity = results.get(0);

        assertNotNull(entity.getPrice());
        // BeanWrapper should handle Double to BigDecimal conversion if price.amount is
        // BigDecimal
        assertEquals(new java.math.BigDecimal("100.5"), entity.getPrice().getAmount());
        assertEquals("USD", entity.getPrice().getCurrency());
    }

    public static class NestedEntity {
        private Money price;

        public Money getPrice() {
            return price;
        }

        public void setPrice(Money price) {
            this.price = price;
        }
    }

    public static class Money {
        private java.math.BigDecimal amount;
        private String currency;

        public java.math.BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(java.math.BigDecimal amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }
    }

    public static class TestFilter {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
