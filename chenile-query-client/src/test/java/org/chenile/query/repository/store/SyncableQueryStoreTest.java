package org.chenile.query.repository.store;

import org.chenile.query.model.QueryMetadata;
import org.chenile.query.service.QueryStore;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class SyncableQueryStoreTest {

    private int retrieveAllCount = 0;
    private int retrieveCount = 0;
    private Map<String, QueryMetadata> allMap = new HashMap<>();
    private Map<String, QueryMetadata> singleMap = new HashMap<>();
    private RuntimeException retrieveAllException = null;

    private QueryStore remoteQueryStore = new QueryStore() {
        @Override
        public QueryMetadata retrieve(String queryId) {
            retrieveCount++;
            return singleMap.get(queryId);
        }

        @Override
        public Map<String, QueryMetadata> retrieveAll() {
            retrieveAllCount++;
            if (retrieveAllException != null)
                throw retrieveAllException;
            return allMap;
        }
    };

    private SyncableQueryStore syncableQueryStore;

    @Before
    public void setUp() {
        retrieveAllCount = 0;
        retrieveCount = 0;
        allMap.clear();
        singleMap.clear();
        retrieveAllException = null;
        syncableQueryStore = new SyncableQueryStore(remoteQueryStore);
    }

    @Test
    public void testFirstUseSync() {
        Map<String, QueryMetadata> allMetadata = new HashMap<>();
        QueryMetadata q1 = new QueryMetadata();
        q1.setName("query1");
        allMetadata.put("query1", q1);

        allMap.putAll(allMetadata);

        // First call should trigger sync
        QueryMetadata result = syncableQueryStore.retrieve("query1");

        assertNotNull(result);
        assertEquals("query1", result.getName());
        assertEquals(1, retrieveAllCount);
        assertEquals(0, retrieveCount);
    }

    @Test
    public void testLazyLoadFallback() {
        QueryMetadata q2 = new QueryMetadata();
        q2.setName("query2");
        singleMap.put("query2", q2);

        // First call triggers sync (which returns empty)
        // Then it should fallback to lazy load
        QueryMetadata result = syncableQueryStore.retrieve("query2");

        assertNotNull(result);
        assertEquals("query2", result.getName());
        assertEquals(1, retrieveAllCount);
        assertEquals(1, retrieveCount);
    }

    @Test
    public void testSyncFailureFallbackToLazy() {
        // Mock sync failure
        retrieveAllException = new RuntimeException("Remote Down");

        QueryMetadata q3 = new QueryMetadata();
        q3.setName("query3");
        singleMap.put("query3", q3);

        // First call triggers sync (which fails)
        // Then it should fallback to lazy load
        QueryMetadata result = syncableQueryStore.retrieve("query3");

        assertNotNull(result);
        assertEquals("query3", result.getName());
        assertEquals(1, retrieveAllCount);
        assertEquals(1, retrieveCount);
    }
}
