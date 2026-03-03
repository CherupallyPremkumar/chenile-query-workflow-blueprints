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
     * @param method          Repository method (e.g., findByStatusAndId)
     * @param args            Arguments passed to the method
     * @param queryMetadata   Metadata of the query to validate fields
     * @return Map of filters
     */
    public static Map<String, Object> extractFiltersFromMethod(Method method, Object[] args, QueryMetadata queryMetadata) {
        Map<String, Object> filters = new HashMap<>();
        String methodName = method.getName();

        if (!methodName.startsWith("findBy")) {
            logger.warn("Method '{}' does not start with 'findBy'. Returning empty filters.", methodName);
            return filters;
        }

        // Extract property part after 'findBy'
        String propertiesPart = methodName.substring("findBy".length()); // e.g., "StatusAndIdAndType"
        String[] propertyNames = propertiesPart.split("And");          // ["Status", "Id", "Type"]

        if (args == null || args.length != propertyNames.length) {
            logger.error("Number of arguments {} does not match number of fields {} in method {}",
                    args == null ? 0 : args.length, propertyNames.length, methodName);
            return filters;
        }

        // Map each argument to the property name
        for (int i = 0; i < propertyNames.length; i++) {
            String fieldName = propertyNames[i].substring(0, 1).toLowerCase() + propertyNames[i].substring(1);

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