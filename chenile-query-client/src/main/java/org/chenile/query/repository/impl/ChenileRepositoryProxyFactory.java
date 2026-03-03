package org.chenile.query.repository.impl;

import org.chenile.query.annotation.QueryName;
import org.chenile.query.model.QueryMetadata;
import org.chenile.query.model.SearchRequest;
import org.chenile.query.model.SearchResponse;
import org.chenile.query.repository.ChenileRepository;
import org.chenile.query.repository.store.SyncableQueryStore;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import org.chenile.query.annotation.ChenileParam;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Factory for creating dynamic proxies of ChenileRepository interfaces.
 * This allows developers to define custom repository interfaces and have them
 * backed by ChenileRepositoryImpl.
 */
public class ChenileRepositoryProxyFactory<T, I extends ChenileRepository<T>> implements FactoryBean<I> {

    protected final Class<I> repositoryInterface;
    protected final Class<T> entityClass;
    private final Logger logger = LoggerFactory.getLogger(ChenileRepositoryProxyFactory.class);

    @Autowired
    public SyncableQueryStore syncableQueryStore;
    @Autowired
    protected ApplicationContext applicationContext;

    public ChenileRepositoryProxyFactory(Class<I> repositoryInterface, Class<T> entityClass) {
        this.repositoryInterface = repositoryInterface;
        this.entityClass = entityClass;
    }

    // Precomputed dispatch map for each method
    private final Map<Method, QueryInvoker> methodInvokers = new HashMap<>();
    private I proxy;

    @Override
    public I getObject() throws Exception {
        if (proxy == null) {
            this.proxy = createProxy();
        }
        return proxy;
    }

    private I createProxy() {
        ChenileRepositoryImpl<T> repositoryImpl = new ChenileRepositoryImpl<>(entityClass);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(repositoryImpl);

        @SuppressWarnings("unchecked")
        I proxyInstance = (I) Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class[] { repositoryInterface },
                new ChenileRepositoryInvocationHandler(repositoryImpl));
        return proxyInstance;
    }

    /**
     * Validates all methods in the repository interface against the available
     * QueryMetadata.
     * Pre-calculates the invokers for each method.
     */
    public void validate() {
        logger.info("Validating Chenile Repository: {}", repositoryInterface.getName());
        ChenileRepositoryImpl<T> repositoryImpl = new ChenileRepositoryImpl<>(entityClass);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(repositoryImpl);

        Map<String, QueryMetadata> allDefinitions = syncableQueryStore.retrieveAll();
        if (allDefinitions == null || allDefinitions.isEmpty()) {
            throw new IllegalStateException("Cannot validate repository " + repositoryInterface.getSimpleName() +
                    " because QueryStore metadata is not available.");
        }
        for (Method method : repositoryInterface.getDeclaredMethods()) {
            if (method.getDeclaringClass().equals(ChenileRepository.class)
                    || method.getDeclaringClass().equals(Object.class)) {
                continue;
            }

            QueryMetadata methodMetadata;
            if (method.isAnnotationPresent(QueryName.class)) {
                String queryName = method.getAnnotation(QueryName.class).value();
                methodMetadata = syncableQueryStore.retrieve(queryName);
                if (methodMetadata == null) {
                    throw new IllegalStateException(
                            "Method '" + method.getName() + "' in interface '" + repositoryInterface.getSimpleName() +
                                    "' specifies @QueryName(\"" + queryName
                                    + "\"), but this query was not found in QueryStore.");
                }
            } else {
                methodMetadata = syncableQueryStore.retrieve(repositoryImpl.defaultQueryName);
                if (methodMetadata == null) {
                    throw new IllegalStateException(
                            "Method '" + method.getName() + "' in interface '" + repositoryInterface.getSimpleName() +
                                    "' requires default QueryMetadata for entity '"
                                    + repositoryImpl.entityClass.getSimpleName() +
                                    "', but it was not found in QueryStore. Either define a query named '" +
                                    repositoryImpl.defaultQueryName + "' or use @QueryName on the method.");
                }
            }

            if (methodMetadata.getColumnMetadata() == null) {
                logger.warn("QueryMetadata for {} has no column metadata. Skipping column validation for method {}.",
                        methodMetadata.getName(), method.getName());
                continue;
            }

            // All valid column names in lower case for easy comparison
            Set<String> validColumns = methodMetadata.getColumnMetadata().keySet().stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            // Validate @ChenileParam annotations
            Parameter[] parameters = method.getParameters();
            for (Parameter p : parameters) {
                if (p.isAnnotationPresent(ChenileParam.class)) {
                    String paramName = p.getAnnotation(ChenileParam.class).value().toLowerCase();
                    if (!validColumns.contains(paramName)) {
                        throw new IllegalStateException(
                                "Method " + method.getName() + " has @ChenileParam('" + paramName +
                                        "') which does not exist in QueryMetadata for query "
                                        + methodMetadata.getName());
                    }
                }
            }

            // Validate responseClass if defined in QueryMetadata
            if (methodMetadata.getResponseClass() != null && !methodMetadata.getResponseClass().isBlank()) {
                try {
                    Class<?> expectedClass = Class.forName(methodMetadata.getResponseClass());
                    // Inline extract target class from method return type
                    Class<?> actualReturnClass = method.getReturnType();
                    if (java.util.List.class.isAssignableFrom(actualReturnClass)) {
                        java.lang.reflect.Type generic = method.getGenericReturnType();
                        if (generic instanceof java.lang.reflect.ParameterizedType) {
                            java.lang.reflect.Type[] args = ((java.lang.reflect.ParameterizedType) generic)
                                    .getActualTypeArguments();
                            if (args.length > 0 && args[0] instanceof Class) {
                                actualReturnClass = (Class<?>) args[0];
                            }
                        }
                    }
                    // Allow Map and Object as wildcard — they accept any result
                    if (!java.util.Map.class.isAssignableFrom(actualReturnClass)
                            && !Object.class.equals(actualReturnClass)
                            && !expectedClass.isAssignableFrom(actualReturnClass)) {
                        throw new IllegalStateException(
                                "Method '" + method.getName() + "' in '" + repositoryInterface.getSimpleName() +
                                        "' returns " + actualReturnClass.getSimpleName() +
                                        " but query '" + methodMetadata.getName() +
                                        "' declares responseClass=" + expectedClass.getSimpleName() +
                                        ". Return type must be " + expectedClass.getSimpleName() +
                                        " (or a subtype), Map, or Object.");
                    }
                } catch (ClassNotFoundException e) {
                    logger.warn("QueryMetadata for '{}' declares responseClass='{}' which could not be loaded. " +
                            "Skipping return type validation for method '{}'.",
                            methodMetadata.getName(), methodMetadata.getResponseClass(), method.getName());
                }
            }

            // Validate dynamic finder method names: findByX, findAllByX, countByX
            String methodName = method.getName();
            String dynamicPart = null;
            if (methodName.startsWith("findBy") && !method.isAnnotationPresent(QueryName.class)) {
                dynamicPart = methodName.substring(6);
            } else if (methodName.startsWith("findAllBy") && !method.isAnnotationPresent(QueryName.class)) {
                dynamicPart = methodName.substring(9);
            } else if (methodName.startsWith("countBy") && !method.isAnnotationPresent(QueryName.class)) {
                dynamicPart = methodName.substring(7);
            }

            if (dynamicPart != null) {
                // Split by "And" or "Or" (case-insensitive) using positive lookahead for a
                // capital letter
                String[] properties = dynamicPart.split("(?i)And(?=[A-Z])|(?i)Or(?=[A-Z])");
                for (int i = 0; i < properties.length; i++) {
                    String prop = properties[i].trim();
                    if (prop.isEmpty())
                        continue;
                    String fieldName = prop.substring(0, 1).toLowerCase() + prop.substring(1);
                    if (!validColumns.contains(fieldName.toLowerCase())) {
                        throw new IllegalStateException(
                                "Method " + method.getName() + " refers to property '" + fieldName +
                                        "' which does not exist in QueryMetadata valid columns: " + validColumns);
                    }
                }
            }
            // Validation successful! Now pre-build the execution strategy for this method
            methodInvokers.put(method, buildInvoker(method, methodMetadata, repositoryImpl));
        }
    }

    private QueryInvoker buildInvoker(Method method, QueryMetadata methodMetadata,
            ChenileRepositoryImpl<T> repositoryImpl) {
        final ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
        final String[] paramNames = discoverer.getParameterNames(method);
        final Parameter[] parameters = method.getParameters();
        final Class<?> targetClass = extractTargetClass(method);
        final boolean isListReturn = List.class.isAssignableFrom(method.getReturnType());
        final String queryName = methodMetadata.getName();
        final List<String> paramKeys = new ArrayList<>();

        String dynamicPart = null;
        if (method.getName().startsWith("findBy"))
            dynamicPart = method.getName().substring(6);
        else if (method.getName().startsWith("findAllBy"))
            dynamicPart = method.getName().substring(9);
        else if (method.getName().startsWith("countBy"))
            dynamicPart = method.getName().substring(7);

        String[] inferredPropNames = (dynamicPart != null) ? dynamicPart.split("(?i)And(?=[A-Z])|(?i)Or(?=[A-Z])")
                : new String[0];

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(ChenileParam.class)) {
                paramKeys.add(parameters[i].getAnnotation(ChenileParam.class).value());
            } else if (paramNames != null && paramNames[i] != null) {
                paramKeys.add(paramNames[i]);
            } else if (i < inferredPropNames.length) {
                // Fallback to property name from method name if param names are missing
                String prop = inferredPropNames[i].trim();
                if (!prop.isEmpty()) {
                    String fieldName = prop.substring(0, 1).toLowerCase() + prop.substring(1);
                    paramKeys.add(fieldName);
                } else {
                    paramKeys.add("arg" + i);
                }
            } else {
                paramKeys.add("arg" + i);
            }
        }

        final boolean isDynamicFinder = dynamicPart != null && !method.isAnnotationPresent(QueryName.class);

        return args -> {
            Map<String, Object> filters = new HashMap<>();

            if (args != null) {
                for (int i = 0; i < Math.min(args.length, paramKeys.size()); i++) {
                    filters.put(paramKeys.get(i), args[i]);
                }
            }

            if (filters.isEmpty() || (isDynamicFinder && args != null && args.length > 0)) {
                if ((method.getName().contains("By") || method.getName().contains("countBy"))
                        && !hasChenileParam(method)) {
                    Map<String, Object> extractedFilters = MethodFilterExtractor.extractFiltersFromMethod(method, args,
                            methodMetadata);
                    filters.putAll(extractedFilters);
                }
            }

            SearchRequest<Map<String, Object>> request = new SearchRequest<>();
            request.setQueryName(queryName);
            request.setFilters(filters);
            SearchResponse response = repositoryImpl.search(request);

            if (response.getList() == null || response.getList().isEmpty()) {
                return isListReturn ? Collections.emptyList() : null;
            }

            if (!isListReturn && response.getList().size() > 1) {
                logger.warn("Query '{}' returned {} results for single-result method '{}'. Returning the first one.",
                        queryName, response.getList().size(), method.getName());
            }

            if (Map.class.isAssignableFrom(targetClass)) {
                List<Map<String, Object>> results = response.getList().stream()
                        .map(r -> (Map<String, Object>) r.getRow())
                        .collect(Collectors.toList());
                return isListReturn ? results : results.get(0);
            }

            List<Object> results = response.getList().stream()
                    .map(r -> {
                        Object entity = ChenileMapper.mapRowToEntity((Map<String, Object>) r.getRow(), targetClass);
                        try {
                            Field actionsField = targetClass.getDeclaredField("allowedActions");
                            actionsField.setAccessible(true);
                            actionsField.set(entity, r.getAllowedActions());
                        } catch (Exception ignored) {
                        }
                        return entity;
                    })
                    .collect(Collectors.toList());

            return isListReturn ? results : results.get(0);
        };
    }

    private Class<?> extractTargetClass(Method method) {
        Class<?> returnType = method.getReturnType();
        if (List.class.isAssignableFrom(returnType)) {
            java.lang.reflect.Type genericType = method.getGenericReturnType();
            if (genericType instanceof java.lang.reflect.ParameterizedType) {
                java.lang.reflect.Type[] typeArguments = ((java.lang.reflect.ParameterizedType) genericType)
                        .getActualTypeArguments();
                if (typeArguments.length > 0 && typeArguments[0] instanceof Class) {
                    return (Class<?>) typeArguments[0];
                }
            }
            return Map.class;
        }
        return returnType;
    }

    private boolean hasChenileParam(Method method) {
        for (Parameter p : method.getParameters()) {
            if (p.isAnnotationPresent(ChenileParam.class))
                return true;
        }
        return false;
    }

    @FunctionalInterface
    private interface QueryInvoker {
        Object invoke(Object[] args) throws Exception;
    }

    @Override
    public Class<?> getObjectType() {
        return repositoryInterface;
    }

    private class ChenileRepositoryInvocationHandler implements InvocationHandler {
        private final ChenileRepositoryImpl<T> repositoryImpl;

        public ChenileRepositoryInvocationHandler(ChenileRepositoryImpl<T> repositoryImpl) {
            this.repositoryImpl = repositoryImpl;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass().equals(ChenileRepository.class) ||
                    method.getDeclaringClass().equals(Object.class)) {
                return method.invoke(repositoryImpl, args);
            }

            QueryInvoker invoker = methodInvokers.get(method);
            if (invoker == null) {
                throw new IllegalStateException(
                        "Method " + method.getName() + " was not validated or mapped during repository startup.");
            }

            return invoker.invoke(args);
        }
    }
}
