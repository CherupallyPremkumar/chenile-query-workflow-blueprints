package org.chenile.query.repository.impl;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.Map;

/**
 * Utility class to map query result rows (Maps) to POJOs/DTOs.
 */
public class ChenileMapper {

    private static final Logger logger = LoggerFactory.getLogger(ChenileMapper.class);

    public static <T> T mapRowToEntity(Map<String, Object> row, Class<T> entityClass) {
        if (row == null) return null;
        
        // Normalize keys to lowercase for case-insensitive lookup
        Map<String, Object> normalizedRow = row.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        e -> e.getKey().toLowerCase(),
                        Map.Entry::getValue,
                        (v1, v2) -> v1 // Keep first value if duplicates exist
                ));

        try {
            T entity = entityClass.getDeclaredConstructor().newInstance();
            BeanWrapper wrapper = new BeanWrapperImpl(entity);
            wrapper.setAutoGrowNestedPaths(true);

            Class<?> currentClass = entityClass;
            while (currentClass != null && currentClass != Object.class) {
                for (Field field : currentClass.getDeclaredFields()) {
                    String fieldName = field.getName();
                    mapProperty(wrapper, fieldName, field.getType(), normalizedRow, "");
                }
                currentClass = currentClass.getSuperclass();
            }
            return entity;
        } catch (Exception e) {
            logger.error("Error mapping row to entity {}: {}", entityClass.getName(), e.getMessage());
            throw new RuntimeException("Mapping error for " + entityClass.getName(), e);
        }
    }

    private static void mapProperty(BeanWrapper wrapper, String propertyName, Class<?> propertyType,
                                     Map<String, Object> row, String prefix) {
        String fullPath = prefix.isEmpty() ? propertyName : prefix + "." + propertyName;

        // Exact match
        String lowerProp = propertyName.toLowerCase();
        if (row.containsKey(lowerProp)) {
            setProperty(wrapper, fullPath, row.get(lowerProp));
            return;
        }

        // Check for common naming patterns (camelCase, snake_case)
        String snakeName = toSnakeCase(propertyName).toLowerCase();
        if (row.containsKey(snakeName)) {
            setProperty(wrapper, fullPath, row.get(snakeName));
            return;
        }

        // Nested mapping for complex types (like Money)
        if (!isSimpleType(propertyType)) {
            for (Field nestedField : propertyType.getDeclaredFields()) {
                String nestedFieldName = nestedField.getName();

                // Try flattened patterns: propertyName + CamelCaseNestedField (e.g., priceAmount)
                String flattenedCamel = (propertyName + capitalize(nestedFieldName)).toLowerCase();
                if (row.containsKey(flattenedCamel)) {
                    setProperty(wrapper, fullPath + "." + nestedFieldName, row.get(flattenedCamel));
                }

                // Try flattened snake pattern: property_name + _ + nested_field_name (e.g., price_amount)
                String flattenedSnake = (snakeName + "_" + toSnakeCase(nestedFieldName)).toLowerCase();
                if (row.containsKey(flattenedSnake)) {
                    setProperty(wrapper, fullPath + "." + nestedFieldName, row.get(flattenedSnake));
                }

                // Try nested field name directly (e.g., 'currency' column for 'price.currency')
                String lowerNested = nestedFieldName.toLowerCase();
                if (row.containsKey(lowerNested)) {
                    setProperty(wrapper, fullPath + "." + nestedFieldName, row.get(lowerNested));
                }
            }
        }
    }

    private static void setProperty(BeanWrapper wrapper, String path, Object value) {
        try {
            wrapper.setPropertyValue(path, value);
        } catch (BeansException e) {
            logger.warn("Could not set property {} to value {}: {}", path, value, e.getMessage());
        }
    }

    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
                type.equals(String.class) ||
                Number.class.isAssignableFrom(type) ||
                type.equals(Boolean.class) ||
                type.equals(Date.class) ||
                type.isEnum();
    }

    private static String toSnakeCase(String str) {
        return str.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
