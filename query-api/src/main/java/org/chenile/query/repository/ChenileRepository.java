package org.chenile.query.repository;

import org.chenile.query.model.SearchRequest;
import org.chenile.query.model.SearchResponse;
import java.util.List;
import java.util.Map;

/**
 * Generic repository interface for Chenile Queries.
 * Developers can extend this interface to create domain-specific repositories.
 * 
 * @param <T> The entity type
 */
public interface ChenileRepository<T> {

    /**
     * Find an entity by its ID.
     * 
     * @param id The ID to search for
     * @return The entity, or null if not found
     */
    T findById(String id);

    /**
     * Find all entities of this type.
     * 
     * @return A list of all entities
     */
    List<T> findAll();

    /**
     * Perform a search with a POJO filter.
     * 
     * @param filter The POJO filter
     * @return A list of matching entities
     */
    List<T> findByFilter(Object filter);

    /**
     * Perform a search with a set of filters.
     * 
     * @param filters Map of filter names to values
     * @return A list of matching entities
     */
    List<T> findByFilters(Map<String, Object> filters);

    /**
     * Execute a search with a full SearchRequest.
     * 
     * @param searchRequest The search request
     * @return The search response
     */
    SearchResponse search(SearchRequest<Map<String, Object>> searchRequest);
}
