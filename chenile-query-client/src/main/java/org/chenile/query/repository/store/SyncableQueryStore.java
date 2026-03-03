package org.chenile.query.repository.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.chenile.query.model.QueryMetadata;
import org.chenile.query.service.QueryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A QueryStore implementation that syncs all metadata from a remote store on
 * first use.
 * If sync fails, it falls back to lazy loading from the remote store on each
 * request.
 * 
 * <p>
 * Behavior:
 * <ul>
 * <li>On first retrieve() call, attempts to sync all metadata from remote</li>
 * <li>If sync succeeds, serves from local cache for performance</li>
 * <li>If sync fails or returns empty, falls back to lazy loading from
 * remote</li>
 * <li>If cache miss during lazy loading, queries remote and caches the
 * result</li>
 * </ul>
 */
@Order(60000)
public class SyncableQueryStore implements QueryStore, ApplicationListener<ApplicationReadyEvent> {
    private static final Logger logger = LoggerFactory.getLogger(SyncableQueryStore.class);

    private final QueryStore queryDefinitions;
    private final Map<String, QueryMetadata> cache = new ConcurrentHashMap<>();
    private volatile boolean syncAttempted = false;

    @Autowired(required = false)
    private ObjectMapper objectMapper = new ObjectMapper();

    public SyncableQueryStore(QueryStore queryDefinitions) {
        this.queryDefinitions = queryDefinitions;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!syncAttempted) {
            performSync();
        }
    }

    /**
     * Ensures sync is attempted at least once before lazy loading.
     */
    private void ensureSyncAttempted() {
        if (!syncAttempted) {
            synchronized (this) {
                if (!syncAttempted) {
                    performSync();
                    syncAttempted = true;
                }
            }
        }
    }

    @Override
    public QueryMetadata retrieve(String queryId) {
        ensureSyncAttempted();
        QueryMetadata metadata = cache.get(queryId);
        if (metadata == null) {
            metadata = fetchAndCacheFromRemote(queryId);
        }
        return metadata;
    }

    @Override
    public Map<String, QueryMetadata> retrieveAll() {
        ensureSyncAttempted();
        return cache;
    }

    /**
     * Fetches a single query metadata from remote and caches it.
     * 
     * @param queryId the query identifier
     * @return the QueryMetadata or null if not found
     */
    private QueryMetadata fetchAndCacheFromRemote(String queryId) {
        try {
            Object result = queryDefinitions.retrieve(queryId);
            if (result == null)
                return null;

            QueryMetadata metadata;
            if (result instanceof Map && !(result instanceof QueryMetadata)) {
                metadata = objectMapper.convertValue(result, QueryMetadata.class);
            } else {
                metadata = (QueryMetadata) result;
            }

            if (metadata != null) {
                cache.put(queryId, metadata);
                logger.debug("Fetched and cached metadata for query: {}", queryId);
            }
            return metadata;
        } catch (Exception e) {
            logger.warn("Failed to fetch metadata for query '{}' from remote: {}. Returning null.",
                    queryId, e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to sync all metadata from the remote store.
     * If this fails, the store will fall back to lazy loading.
     */
    private void performSync() {
        logger.info("Attempting initial metadata synchronization from remote QueryStore...");
        try {
            Map<String, Object> allEntriesRaw = (Map<String, Object>) (Object) queryDefinitions.retrieveAll();
            if (allEntriesRaw != null && !allEntriesRaw.isEmpty()) {
                for (Map.Entry<String, Object> entry : allEntriesRaw.entrySet()) {
                    Object val = entry.getValue();
                    QueryMetadata metadata;
                    if (val instanceof Map && !(val instanceof QueryMetadata)) {
                        metadata = objectMapper.convertValue(val, QueryMetadata.class);
                    } else {
                        metadata = (QueryMetadata) val;
                    }
                    cache.put(entry.getKey(), metadata);
                }
                logger.info("Successfully synchronized {} query definitions from remote store.",
                        allEntriesRaw.size());
            } else if (allEntriesRaw != null) {
                logger.warn("Remote QueryStore returned empty. Will use lazy loading.");
            }
        } catch (Exception e) {
            logger.warn("Initial metadata synchronization failed: {}. Will use lazy loading from remote. Error: {}",
                    e.getMessage(), e.getClass().getSimpleName());
            // Log full stack trace for ClassCastException to help debug if it still occurs
            if (e instanceof ClassCastException) {
                logger.error("ClassCastException during sync", e);
            }
        }
    }
}
