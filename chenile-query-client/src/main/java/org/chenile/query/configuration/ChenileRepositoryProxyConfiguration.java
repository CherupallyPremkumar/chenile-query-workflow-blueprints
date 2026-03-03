package org.chenile.query.configuration;

import java.util.Map;
import org.chenile.proxy.builder.ProxyBuilder;
import org.chenile.proxy.builder.ProxyBuilder.ProxyMode;
import org.chenile.query.service.SearchService;
import org.chenile.query.service.QueryStore;
import org.chenile.query.repository.store.SyncableQueryStore;
import org.chenile.core.context.HeaderCopier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.chenile.query.repository.impl.ChenileRepositoryValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChenileRepositoryProxyConfiguration {

    @Configuration
    @ConditionalOnProperty(name = "chenile.query.proxy.enabled", havingValue = "true")
    public static class EnabledConfiguration {

        @Value("${chenile.query.remote.base-url}")
        private String baseUrl;

        @Autowired
        private ProxyBuilder proxyBuilder;
        @Autowired
        private HeaderCopier headerCopier;

        @Bean
        @ConditionalOnMissingBean(SearchService.class)
        public SearchService searchService() {
            return proxyBuilder.buildProxy(
                    SearchService.class,
                    "chenileMybatisQuery",
                    headerCopier,
                    ProxyMode.COMPUTE_DYNAMICALLY,
                    baseUrl);
        }

        @Bean
        @ConditionalOnMissingBean(name = "queryDefinitions")
        public QueryStore queryDefinitions() {
            return proxyBuilder.buildProxy(
                    QueryStore.class,
                    "chenileQueryDefinitions", // Corrected ID based on previous user hints
                    headerCopier,
                    ProxyMode.COMPUTE_DYNAMICALLY,
                    baseUrl);
        }

        @Bean
        public SyncableQueryStore syncableQueryStore(QueryStore queryDefinitions) {
            return new SyncableQueryStore(queryDefinitions);
        }

        @Bean
        public ChenileRepositoryValidator chenileRepositoryValidator() {
            return new ChenileRepositoryValidator();
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "chenile.query.proxy.enabled", havingValue = "false")
    public static class DisabledConfiguration {
        // No proxy beans - local repository will work directly
    }
}
