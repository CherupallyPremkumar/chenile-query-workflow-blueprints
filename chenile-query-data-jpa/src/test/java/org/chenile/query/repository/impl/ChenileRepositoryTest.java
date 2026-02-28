
package org.chenile.query.repository.impl;

import org.chenile.query.model.ResponseRow;
import org.chenile.query.model.SearchRequest;
import org.chenile.query.model.SearchResponse;
import org.chenile.query.repository.ChenileRepository;
import org.chenile.query.repository.impl.ChenileRepositoryImpl;
import org.chenile.query.service.QueryStore;
import org.chenile.query.service.SearchService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ChenileRepositoryTest {

    public static class TestEntity {
        private String id;
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    private ChenileRepositoryImpl<TestEntity> repository;

    @Mock
    private SearchService<Map<String, Object>> searchService;

    @Mock
    private QueryStore queryStore;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        repository = new ChenileRepositoryImpl<>(TestEntity.class);
        repository.searchService = searchService;
        repository.queryStore = queryStore;
    }

    @Test
    public void testFindById() {
        SearchResponse response = new SearchResponse();
        List<ResponseRow> rows = new ArrayList<>();
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("id", "123");
        rowData.put("name", "Test Name");
        ResponseRow row = new ResponseRow();
        row.setRow(rowData);
        rows.add(row);
        response.setList(rows);

        when(searchService.search(any())).thenReturn(response);

        TestEntity entity = repository.findById("123");

        assertNotNull(entity);
        assertEquals("123", entity.getId());
        assertEquals("Test Name", entity.getName());

        ArgumentCaptor<SearchRequest<Map<String, Object>>> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(searchService).search(captor.capture());
        
        SearchRequest<Map<String, Object>> request = captor.getValue();
        assertEquals("testEntity", request.getQueryName());
        assertEquals("123", request.getFilters().get("id"));
    }

    @Test
    public void testFindAll() {
        SearchResponse response = new SearchResponse();
        response.setList(new ArrayList<>());
        when(searchService.search(any())).thenReturn(response);

        List<TestEntity> results = repository.findAll();

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(searchService).search(argThat(request -> 
            request.getQueryName().equals("testEntity") && 
            request.getFilters().isEmpty()
        ));
    }

    @Test
    public void testFindByFilter() {
        SearchResponse response = new SearchResponse();
        response.setList(new ArrayList<>());
        when(searchService.search(any())).thenReturn(response);

        TestFilter filter = new TestFilter();
        filter.setName("SearchName");
        
        repository.findByFilter(filter);

        verify(searchService).search(argThat(request -> 
            request.getFilters().containsKey("name") && 
            request.getFilters().get("name").equals("SearchName")
        ));
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

        when(searchService.search(any())).thenReturn(response);

        ChenileRepositoryImpl<NestedEntity> nestedRepo = new ChenileRepositoryImpl<>(NestedEntity.class);
        nestedRepo.searchService = searchService;
        nestedRepo.queryStore = queryStore;

        List<NestedEntity> results = nestedRepo.findAll();
        assertFalse(results.isEmpty());
        NestedEntity entity = results.get(0);
        
        assertNotNull(entity.getPrice());
        // BeanWrapper should handle Double to BigDecimal conversion if price.amount is BigDecimal
        assertEquals(new java.math.BigDecimal("100.5"), entity.getPrice().getAmount());
        assertEquals("USD", entity.getPrice().getCurrency());
    }

    public static class NestedEntity {
        private Money price;
        public Money getPrice() { return price; }
        public void setPrice(Money price) { this.price = price; }
    }

    public static class Money {
        private java.math.BigDecimal amount;
        private String currency;
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }

    public static class TestFilter {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
