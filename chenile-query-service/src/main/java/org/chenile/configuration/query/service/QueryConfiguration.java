package org.chenile.configuration.query.service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.chenile.core.context.ChenileExchange;
import org.chenile.core.context.ContextContainer;
import org.chenile.query.model.QueryMetadata;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.chenile.query.service.QueryStore;
import org.chenile.query.service.SearchService;
import org.chenile.query.service.impl.NamedQueryServiceSpringMybatisImpl;
import org.chenile.query.service.impl.QueryDefinitions;
import org.chenile.query.service.interceptor.QueryUserFilterInterceptor;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Registers query beans in Spring
 */
@Configuration
public class QueryConfiguration {

	@Value("${query.mapperFiles}")
	private Resource[] mapperFiles;
	
	@Value("${query.definitionFiles}")
	private Resource[] queryDefinitionFiles;
	
	@Bean("queryDefinitions")
	QueryDefinitions queryDefinitions() throws IOException{
		return new QueryDefinitions(queryDefinitionFiles);
	}

    @Bean
    @ConfigurationProperties(prefix = "query")
    QueryDatasourcesProperties queryDatasourcesProperties() {
		return new QueryDatasourcesProperties();
	}

    @Bean("queryTargetDataSources")
    Map<String, DataSource> queryTargetDataSources(@Autowired QueryDatasourcesProperties properties) {
		Map<String, DataSource> targetDataSources = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, String>> entry : properties.getDatasources().entrySet()) {
			java.util.Properties hikariProps = new java.util.Properties();
			for (Map.Entry<String, String> prop : entry.getValue().entrySet()) {
				if ("type".equals(prop.getKey())) {
					continue;
				}
				hikariProps.setProperty(prop.getKey(), prop.getValue());
			}
			HikariConfig hikariConfig = new HikariConfig(hikariProps);
			HikariDataSource dataSource = new HikariDataSource(hikariConfig);
			targetDataSources.put(entry.getKey(), dataSource);
		}
		return targetDataSources;
	}

    @Bean("queryDatasource")
    DataSource queryDataSource(@Autowired @Qualifier("queryTargetDataSources") Map<String, DataSource> targetDataSources,
							   @Autowired QueryDatasourcesProperties properties,
							   @Autowired ContextContainer contextContainer) {
		AbstractRoutingDataSource routingDataSource = new AbstractRoutingDataSource() {
			@Override
			protected Object determineCurrentLookupKey() {
				String tenantId = contextContainer.getTenant();
				if (tenantId == null) {
					return null;
				}
				tenantId = tenantId.trim();
				return tenantId.isEmpty() ? null : tenantId;
			}
		};
		Map<Object, Object> target = new LinkedHashMap<>();
		for (Map.Entry<String, DataSource> entry : targetDataSources.entrySet()) {
			target.put(entry.getKey(), entry.getValue());
		}
		routingDataSource.setTargetDataSources(target);
		String defaultTenantId = properties.getDefaultTenantId();
		DataSource defaultDataSource = defaultTenantId == null ? null : targetDataSources.get(defaultTenantId);
		if (defaultDataSource == null && !targetDataSources.isEmpty()) {
			defaultDataSource = targetDataSources.values().iterator().next();
		}
		routingDataSource.setDefaultTargetDataSource(defaultDataSource);
		return routingDataSource;
	}

    @Bean
    SqlSessionFactory sqlSessionFactory(@Autowired @Qualifier("queryDatasource") DataSource queryDataSource)
            throws Exception {
		SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
		factoryBean.setDataSource(queryDataSource);
		factoryBean.setMapperLocations(mapperFiles);
		return factoryBean.getObject();
	}

    @Bean
    SearchService<Map<String, Object>> searchService(@Autowired @Qualifier("queryDefinitions") 
       QueryStore queryStore) {
		return new NamedQueryServiceSpringMybatisImpl(queryStore);
	}

    @Bean
    SqlSessionTemplate sqlSessionTemplate(@Autowired SqlSessionFactory sqlSessionFactory) {
		return new SqlSessionTemplate(sqlSessionFactory);
	}

    @Bean
    QueryUserFilterInterceptor queryUserFilterInterceptor() {
		return new QueryUserFilterInterceptor();
	}

	@Bean
	Function<ChenileExchange,String[]> queryAuthorities(@Autowired QueryDefinitions queryDefinitions){
		return (exchange) -> {
			String queryName = exchange.getHeader("queryName",String.class);
			if (queryName == null) return null;
			QueryMetadata data = queryDefinitions.retrieve(queryName);
			if (data == null) return null;
			return data.getAcls();
		};
	}
}
