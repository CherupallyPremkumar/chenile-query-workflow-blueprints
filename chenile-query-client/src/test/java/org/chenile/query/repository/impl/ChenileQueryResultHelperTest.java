package org.chenile.query.repository.impl;

import org.chenile.query.model.ResponseRow;
import org.chenile.query.model.SearchResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ChenileQueryResultHelperTest {

    @Test
    public void testExtractFieldSuccess() {
        SearchResponse response = new SearchResponse();
        List<ResponseRow> list = new ArrayList<>();
        
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", 1L);
        row1.put("name", "Test 1");
        ResponseRow r1 = new ResponseRow();
        r1.setRow(row1);
        list.add(r1);

        Map<String, Object> row2 = new HashMap<>();
        row2.put("id", 2L);
        row2.put("name", "Test 2");
        ResponseRow r2 = new ResponseRow();
        r2.setRow(row2);
        list.add(r2);

        response.setList(list);

        List<Long> ids = ChenileQueryResultHelper.extractField(response, "id", Long.class);
        assertEquals(2, ids.size());
        assertEquals(1L, ids.get(0));
        assertEquals(2L, ids.get(1));
    }

    @Test
    public void testExtractFieldTypeMismatch() {
        SearchResponse response = new SearchResponse();
        List<ResponseRow> list = new ArrayList<>();
        
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", "not-a-long");
        ResponseRow r1 = new ResponseRow();
        r1.setRow(row1);
        list.add(r1);
        response.setList(list);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ChenileQueryResultHelper.extractField(response, "id", Long.class);
        });

        assertTrue(exception.getMessage().contains("Expected java.lang.Long but got java.lang.String"));
    }
}
