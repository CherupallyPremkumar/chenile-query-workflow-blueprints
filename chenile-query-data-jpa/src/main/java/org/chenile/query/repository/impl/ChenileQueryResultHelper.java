package org.chenile.query.repository.impl;

import org.chenile.query.model.SearchResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility for extracting typed results from a SearchResponse.
 */
public class ChenileQueryResultHelper {

    /**
     * Extracts a list of values for a specific field from the rows in a SearchResponse.
     * Useful for extracting lists of IDs.
     * @param response The search response
     * @param fieldName The name of the field to extract
     * @param <R> The type of the field
     * @return A list of field values
     */
    @SuppressWarnings("unchecked")
    public static <R> List<R> extractField(SearchResponse response, String fieldName) {
        if (response.getList() == null) return Collections.emptyList();

        return response.getList().stream()
                .map(item -> {
                    Map<String, Object> row = (Map<String, Object>) item.getRow();
                    return (R) row.get(fieldName);
                })
                .collect(Collectors.toList());
    }
}
