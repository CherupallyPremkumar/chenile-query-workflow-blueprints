package org.chenile.query.repository.impl;

import org.chenile.query.model.SearchResponse;
import org.chenile.query.repository.ChenileRepository;
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

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
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

    private org.chenile.query.service.QueryStore queryStore = new org.chenile.query.service.QueryStore() {
        @Override
        public org.chenile.query.model.QueryMetadata retrieve(String queryId) {
            return singleEntityMap.get(queryId);
        }

        @Override
        public Map<String, org.chenile.query.model.QueryMetadata> retrieveAll() {
            return retrieveAllMap;
        }
    };

    private Map<String, org.chenile.query.model.QueryMetadata> singleEntityMap = new java.util.HashMap<>();
    private Map<String, org.chenile.query.model.QueryMetadata> retrieveAllMap = new java.util.HashMap<>();
    private org.chenile.query.repository.store.SyncableQueryStore syncableQueryStore;

    private ChenileRepositoryProxyFactory<TestEntity, TestRepository> factory;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(applicationContext.getAutowireCapableBeanFactory()).thenReturn(beanFactory);
        factory = new ChenileRepositoryProxyFactory<>(TestRepository.class, TestEntity.class);
        factory.applicationContext = applicationContext;
        singleEntityMap.clear();
        retrieveAllMap.clear();
        syncableQueryStore = new org.chenile.query.repository.store.SyncableQueryStore(queryStore);
        factory.syncableQueryStore = syncableQueryStore;

        // Mock the search service during autowiring
        doAnswer(invocation -> {
            Object bean = invocation.getArgument(0);
            if (bean instanceof org.chenile.query.repository.impl.ChenileRepositoryImpl) {
                ChenileRepositoryImpl repo = (org.chenile.query.repository.impl.ChenileRepositoryImpl) bean;
                repo.searchService = searchService;
            }
            return null;
        }).when(beanFactory).autowireBean(any());

        // Default metadata stubbing (can be overridden in tests)
        org.chenile.query.model.QueryMetadata metadata = new org.chenile.query.model.QueryMetadata();
        metadata.setName("testEntity");
        Map<String, org.chenile.query.model.ColumnMetadata> colMap = new java.util.HashMap<>();
        org.chenile.query.model.ColumnMetadata col = new org.chenile.query.model.ColumnMetadata();
        col.setName("name");
        colMap.put("name", col);
        metadata.setColumnMetadata(colMap);

        singleEntityMap.put("testEntity", metadata);

        // We also need to mock retrieveAll for the startup validation
        Map<String, org.chenile.query.model.QueryMetadata> map = new java.util.HashMap<>();
        map.put("testEntity", metadata);
        retrieveAllMap.put("testEntity", metadata);
        factory.validate();
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
        when(searchService.doSearch(any())).thenReturn(response);

        List<TestEntity> results = repository.findByName("John Doe");

        assertNotNull(results);
        verify(searchService).doSearch(any());
    }

    @Test(expected = IllegalStateException.class)
    public void testMissingQueryNameMetadata() throws Exception {
        // Redefine interface with a missing @QueryName
        interface RepositoryWithMissingQuery extends ChenileRepository<TestEntity> {
            @org.chenile.query.annotation.QueryName("non-existent")
            List<TestEntity> findSomething();
        }

        ChenileRepositoryProxyFactory<TestEntity, RepositoryWithMissingQuery> myFactory = new ChenileRepositoryProxyFactory<>(
                RepositoryWithMissingQuery.class, TestEntity.class);
        myFactory.applicationContext = applicationContext;
        myFactory.syncableQueryStore = syncableQueryStore;

        // Mock the query store just for this factory to return null for "non-existent"
        singleEntityMap.put("non-existent", null);
        retrieveAllMap.clear();

        // This should throw IllegalStateException during validate()
        myFactory.getObject();
        myFactory.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testMissingDefaultMetadataMessage() throws Exception {
        // Override the default stub to return null
        singleEntityMap.put("testEntity", null);
        retrieveAllMap.clear();

        ChenileRepositoryProxyFactory<TestEntity, TestRepository> myFactory = new ChenileRepositoryProxyFactory<>(
                TestRepository.class, TestEntity.class);
        myFactory.applicationContext = applicationContext;
        myFactory.syncableQueryStore = new org.chenile.query.repository.store.SyncableQueryStore(queryStore);

        // This should throw IllegalStateException during validate()
        myFactory.getObject();
        myFactory.validate();
    }
}
