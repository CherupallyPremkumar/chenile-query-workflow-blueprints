package org.chenile.query.repository.impl;

import org.chenile.query.annotation.QueryName;
import org.chenile.query.model.QueryMetadata;
import org.chenile.query.model.ResponseRow;
import org.chenile.query.model.SearchRequest;
import org.chenile.query.model.SearchResponse;
import org.chenile.query.repository.ChenileRepository;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

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

    @Autowired
    protected ApplicationContext applicationContext;

    public ChenileRepositoryProxyFactory(Class<I> repositoryInterface, Class<T> entityClass) {
        this.repositoryInterface = repositoryInterface;
        this.entityClass = entityClass;
    }

    @Override
    @SuppressWarnings("unchecked")
    public I getObject() throws Exception {
        ChenileRepositoryImpl<T> repositoryImpl = new ChenileRepositoryImpl<>(entityClass);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(repositoryImpl);

        validateRepositoryMethods(repositoryImpl);

        return (I) Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class[]{repositoryInterface},
                new ChenileRepositoryInvocationHandler(repositoryImpl)
        );
    }

    private void validateRepositoryMethods(ChenileRepositoryImpl<T> repositoryImpl) {
        QueryMetadata defaultMetadata = repositoryImpl.getQueryMetadata();

        for (Method method : repositoryInterface.getDeclaredMethods()) {
            // Ignore base methods
            if (method.getDeclaringClass().equals(ChenileRepository.class)
                    || method.getDeclaringClass().equals(Object.class)) {
                continue;
            }

            // Determine which metadata to validate against
            QueryMetadata methodMetadata = defaultMetadata;
            if (method.isAnnotationPresent(QueryName.class)) {
                String queryName = method.getAnnotation(QueryName.class).value();
                methodMetadata = repositoryImpl.queryStore.retrieve(queryName);
                if (methodMetadata == null) {
                    throw new IllegalStateException(
                            "Invalid @QueryName '" + queryName + "' in method " + method.getName()
                                    + ". Query not found in QueryStore."
                    );
                }
            }

            if (methodMetadata == null || methodMetadata.getColumnMetadata() == null) {
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
                                        "') which does not exist in QueryMetadata for query " + methodMetadata.getName()
                        );
                    }
                }
            }

            // Validate dynamic finder method names: findByX, findByXOrY
            if (method.getName().startsWith("findBy") && !method.isAnnotationPresent(QueryName.class)) {
                String dynamicPart = method.getName().substring(6); // Remove 'findBy'
                // Split by "Or" and "And"
                String[] properties = dynamicPart.split("(?=Or)|(?=And)");
                for (int i = 0; i < properties.length; i++) {
                    properties[i] = properties[i].replaceAll("Or|And", ""); // remove prefix
                    String prop = properties[i].substring(0, 1).toLowerCase() + properties[i].substring(1);
                    if (!validColumns.contains(prop.toLowerCase())) {
                        throw new IllegalStateException(
                                "Method " + method.getName() + " refers to property '" + prop +
                                        "' which does not exist in QueryMetadata"
                        );
                    }
                }
            }
        }
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

            // If it's a method declared in ChenileRepository, delegate directly
            if (method.getDeclaringClass().equals(ChenileRepository.class) ||
                    method.getDeclaringClass().equals(Object.class)) {
                return method.invoke(repositoryImpl, args);
            }

            Object result = handleCustomMethod(method, args);
            
            // Handle single return value if return type is not a List
            if (result instanceof List && !List.class.isAssignableFrom(method.getReturnType())) {
                List<?> list = (List<?>) result;
                return list.isEmpty() ? null : list.get(0);
            }
            return result;
        }

        private Object handleCustomMethod(Method method, Object[] args) {
            Map<String, Object> filters = new HashMap<>();
            ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
            String[] paramNames = discoverer.getParameterNames(method);
            Parameter[] parameters = method.getParameters();

            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    String key = null;
                    if (parameters[i].isAnnotationPresent(ChenileParam.class)) {
                        key = parameters[i].getAnnotation(ChenileParam.class).value();
                    } else if (paramNames != null && paramNames[i] != null) {
                        key = paramNames[i];
                    } else {
                        key = "arg" + i;
                    }
                    filters.put(key, args[i]);
                }
            }

            // Fallback to name-based extraction if filters are still empty or specific findBy pattern is used
            if (filters.isEmpty() || (method.getName().startsWith("findBy") && args != null && args.length > 0)) {
                 if (method.getName().contains("By") && !hasChenileParam(method)) {
                      String name = method.getName();
                      String dynamicPart = name.substring(name.indexOf("By") + 2);
                      String[] propertyNames = dynamicPart.split("(?=Or)|(?=And)");
                      for (int i = 0; i < propertyNames.length && i < args.length; i++) {
                          String prop = propertyNames[i].replaceAll("Or|And", "");
                          prop = prop.substring(0, 1).toLowerCase() + prop.substring(1);
                          filters.put(prop, args[i]);
                      }
                 }
            }

            SearchResponse response;
            if (method.isAnnotationPresent(QueryName.class)) {
                String queryName = method.getAnnotation(QueryName.class).value();
                SearchRequest<Map<String, Object>> request = new SearchRequest<>();
                request.setQueryName(queryName);
                request.setFilters(filters);
                response = repositoryImpl.search(request);
            } else {
                SearchRequest<Map<String, Object>> request = new SearchRequest<>();
                request.setQueryName(repositoryImpl.defaultQueryName);
                request.setFilters(filters);
                response = repositoryImpl.search(request);
            }

            if (response.getList() == null) return Collections.emptyList();

            // Detect target class
            Class<?> targetClass = extractTargetClass(method);
            
            // If target is Map, return raw rows
            if (Map.class.isAssignableFrom(targetClass)) {
                return response.getList().stream()
                        .map(r -> (Map<String, Object>) r.getRow())
                        .collect(Collectors.toList());
            }

            // Map rows to target class
            return response.getList().stream()
                    .map(r -> {
                        Object entity = ChenileMapper.mapRowToEntity((Map<String, Object>) r.getRow(), targetClass);
                        try {
                            // Check if the target class has an allowedActions field
                            Field actionsField = targetClass.getDeclaredField("allowedActions");
                            actionsField.setAccessible(true);
                            actionsField.set(entity, r.getAllowedActions());
                        } catch (NoSuchFieldException e) {
                            // Target class doesn't want allowedActions, that's fine
                        } catch (Exception e) {
                            // Log warning but don't fail the mapping
                            // logger.warn("Could not set allowedActions for {}: {}", targetClass.getName(), e.getMessage());
                        }
                        return entity;
                    })
                    .collect(Collectors.toList());
        }

        private Class<?> extractTargetClass(Method method) {
            Class<?> returnType = method.getReturnType();
            if (List.class.isAssignableFrom(returnType)) {
                java.lang.reflect.Type genericType = method.getGenericReturnType();
                if (genericType instanceof java.lang.reflect.ParameterizedType) {
                    java.lang.reflect.Type[] typeArguments = ((java.lang.reflect.ParameterizedType) genericType).getActualTypeArguments();
                    if (typeArguments.length > 0 && typeArguments[0] instanceof Class) {
                        return (Class<?>) typeArguments[0];
                    }
                }
                return Map.class; // Default if generic type info is missing
            }
            return returnType;
        }

        private boolean hasChenileParam(Method method) {
            for (Parameter p : method.getParameters()) {
                if (p.isAnnotationPresent(ChenileParam.class)) return true;
            }
            return false;
        }
    }
}
