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
     * @param expectedType The expected class type of the field for safe casting
     * @param <R> The type of the field
     * @return A list of field values
     * @throws IllegalArgumentException if a value is found but does not match the expectedType
     */
    @SuppressWarnings("unchecked")
    public static <R> List<R> extractField(SearchResponse response, String fieldName, Class<R> expectedType) {
        if (response.getList() == null) return Collections.emptyList();

        return response.getList().stream()
                .map(item -> {
                    Map<String, Object> row = (Map<String, Object>) item.getRow();
                    Object value = row.get(fieldName);
                    if (value != null && !expectedType.isAssignableFrom(value.getClass())) {
                        throw new IllegalArgumentException(String.format(
                                "Value for field '%s' has unexpected type. Expected %s but got %s",
                                fieldName, expectedType.getName(), value.getClass().getName()));
                    }
                    return (R) value;
                })
                .collect(Collectors.toList());
    }
}
