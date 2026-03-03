package org.chenile.query.repository.impl;

import org.chenile.query.annotation.QueryName;
import org.chenile.query.model.QueryMetadata;
import org.chenile.query.model.SearchRequest;
import org.chenile.query.model.SearchResponse;
import org.chenile.query.repository.ChenileRepository;
import org.chenile.query.service.SearchService;
import org.chenile.query.service.QueryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic implementation of ChenileRepository that interacts with SearchService.
 * @param <T> The entity type
 */
public class ChenileRepositoryImpl<T> implements ChenileRepository<T> {

    private final Logger logger = LoggerFactory.getLogger(ChenileRepositoryImpl.class);

    protected final Class<T> entityClass;
    protected final String defaultQueryName;
    @Autowired
    protected SearchService<Map<String, Object>> searchService;
    @Autowired
    protected QueryStore queryStore;

    public ChenileRepositoryImpl(Class<T> entityClass) {
        this.entityClass = entityClass;
        this.defaultQueryName = resolveQueryName(entityClass);
    }

    public QueryMetadata getQueryMetadata() {
        if (queryStore == null) return null;
        return queryStore.retrieve(defaultQueryName);
    }

    private String resolveQueryName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(QueryName.class)) {
            return clazz.getAnnotation(QueryName.class).value();
        }
        // Default convention: EntityName (decapitalized)
        String name = clazz.getSimpleName();
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }

    @Override
    public T findById(String id) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("id", id);
        List<T> results = findByFilters(filters);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<T> findAll() {
        return findByFilters(Collections.emptyMap());
    }

    @Override
    public List<T> findByFilters(Map<String, Object> filters) {
        SearchRequest<Map<String, Object>> request = new SearchRequest<>();
        request.setQueryName(defaultQueryName);
        request.setFilters(filters);
        
        SearchResponse response = search(request);
        return mapResponseToEntities(response);
    }

    @Override
    public List<T> findByFilter(Object filter) {
        return findByFilters(pojoToMap(filter));
    }

    protected Map<String, Object> pojoToMap(Object obj) {
        if (obj == null) return Collections.emptyMap();
        if (obj instanceof Map) return (Map<String, Object>) obj;

        Map<String, Object> map = new HashMap<>();
        for (Field field : obj.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                if (value != null) {
                    map.put(field.getName(), value);
                }
            } catch (IllegalAccessException e) {
                logger.error("Error converting POJO to map for field {}: {}", field.getName(), e.getMessage());
            }
        }
        return map;
    }

    @Override
    public SearchResponse search(SearchRequest<Map<String, Object>> searchRequest) {
        validateFilters(searchRequest);
        return searchService.search(searchRequest);
    }

    protected void validateFilters(SearchRequest<Map<String, Object>> request) {
        QueryMetadata metadata = queryStore.retrieve(request.getQueryName());
        if (metadata == null || metadata.getColumnMetadata() == null) return;

        Map<String, Object> filters = request.getFilters();
        if (filters == null) return;

        for (String filterName : filters.keySet()) {
            if (!metadata.getColumnMetadata().containsKey(filterName)) {
                logger.warn("Filter '{}' not found in QueryMetadata for query '{}'.", 
                    filterName, request.getQueryName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected List<T> mapResponseToEntities(SearchResponse response) {
        if (response.getList() == null) return Collections.emptyList();

        return response.getList().stream()
                .map(row -> mapRowToEntity((Map<String, Object>) row.getRow()))
                .collect(Collectors.toList());
    }

    protected T mapRowToEntity(Map<String, Object> row) {
        return ChenileMapper.mapRowToEntity(row, entityClass);
    }
}
