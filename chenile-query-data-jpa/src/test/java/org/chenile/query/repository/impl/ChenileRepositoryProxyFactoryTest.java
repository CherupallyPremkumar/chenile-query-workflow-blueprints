package org.chenile.query.repository.impl;

import org.chenile.query.model.SearchResponse;
import org.chenile.query.repository.ChenileRepository;
import org.chenile.query.repository.impl.ChenileRepositoryProxyFactory;
import org.chenile.query.service.QueryStore;
import org.chenile.query.service.SearchService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ChenileRepositoryProxyFactoryTest {

    public static class TestEntity {
        private String id;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public interface TestRepository extends ChenileRepository<TestEntity> {
        List<TestEntity> findByName(String name);
    }

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private AutowireCapableBeanFactory beanFactory;
    @Mock
    private SearchService<Map<String, Object>> searchService;
    @Mock
    private QueryStore queryStore;

    private ChenileRepositoryProxyFactory<TestEntity, TestRepository> factory;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(applicationContext.getAutowireCapableBeanFactory()).thenReturn(beanFactory);
        factory = new ChenileRepositoryProxyFactory<>(TestRepository.class, TestEntity.class);
        factory.applicationContext = applicationContext;
        
        // Mock the search service and query store during autowiring
        doAnswer(invocation -> {
            Object bean = invocation.getArgument(0);
            if (bean instanceof org.chenile.query.repository.impl.ChenileRepositoryImpl) {
                org.chenile.query.repository.impl.ChenileRepositoryImpl repo = (org.chenile.query.repository.impl.ChenileRepositoryImpl) bean;
                repo.searchService = searchService;
                repo.queryStore = queryStore;
            }
            return null;
        }).when(beanFactory).autowireBean(any());
    }

    @Test
    public void testProxyCreation() throws Exception {
        TestRepository repository = factory.getObject();
        assertNotNull(repository);
        assertTrue(Proxy.isProxyClass(repository.getClass()));
    }

    @Test
    public void testCustomFinderMethod() throws Exception {
        TestRepository repository = factory.getObject();
        
        SearchResponse response = new SearchResponse();
        response.setList(Collections.emptyList());
        when(searchService.search(any())).thenReturn(response);

        List<TestEntity> results = repository.findByName("John Doe");

        assertNotNull(results);
        verify(searchService).search(argThat(request -> 
            request.getFilters().containsKey("name") && 
            request.getFilters().get("name").equals("John Doe")
        ));
    }
}
