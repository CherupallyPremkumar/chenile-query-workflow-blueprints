package org.chenile.query.repository.impl;

import org.chenile.query.model.QueryMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility to extract filters from method names like findByXAndY
 */
public class MethodFilterExtractor {

    private static final Logger logger = LoggerFactory.getLogger(MethodFilterExtractor.class);

    /**
     * Extracts a map of filters from a repository method and its arguments.
     * Supports method names like findByStatusAndIdAndType.
     *
     * @param method        Repository method (e.g., findByStatusAndId)
     * @param args          Arguments passed to the method
     * @param queryMetadata Metadata of the query to validate fields
     * @return Map of filters
     */
    public static Map<String, Object> extractFiltersFromMethod(Method method, Object[] args,
            QueryMetadata queryMetadata) {
        Map<String, Object> filters = new HashMap<>();
        String methodName = method.getName();

        String propertiesPart = null;
        if (methodName.startsWith("findBy")) {
            propertiesPart = methodName.substring(6);
        } else if (methodName.startsWith("findAllBy")) {
            propertiesPart = methodName.substring(9);
        } else if (methodName.startsWith("countBy")) {
            propertiesPart = methodName.substring(7);
        }

        if (propertiesPart == null || propertiesPart.isEmpty()) {
            logger.warn(
                    "Method '{}' does not have a recognized prefix (findBy, findAllBy, countBy). Returning empty filters.",
                    methodName);
            return filters;
        }

        // Split by "And" or "Or" (case-insensitive) using positive lookahead for a
        // capital letter
        // to avoid splitting inside property names like "Order" or "Andover".
        String[] propertyNames = propertiesPart.split("(?i)And(?=[A-Z])|(?i)Or(?=[A-Z])");

        if (args == null || args.length < propertyNames.length) {
            logger.error(
                    "Number of arguments {} is less than number of fields {} inferred from method {}. Expected at least {}.",
                    args == null ? 0 : args.length, propertyNames.length, methodName, propertyNames.length);
            return filters;
        }

        // Map each argument to the property name
        for (int i = 0; i < propertyNames.length; i++) {
            // Trim any accidental whitespace just in case
            String prop = propertyNames[i].trim();
            String fieldName = prop.substring(0, 1).toLowerCase() + prop.substring(1);

            // Validate against QueryMetadata columns
            if (queryMetadata != null && queryMetadata.getColumnMetadata() != null) {
                if (!queryMetadata.getColumnMetadata().containsKey(fieldName)) {
                    logger.warn("Field '{}' does not exist in QueryMetadata for query '{}'",
                            fieldName, queryMetadata.getName());
                }
            }

            filters.put(fieldName, args[i]);
        }

        return filters;
    }
}