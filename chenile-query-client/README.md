# Chenile Query Data JPA

## Overview
This module provides a JPA-style generic repository implementation for Chenile Query Service. It's part of the Chenile framework (https://chenile.org) and is used to implement query/read endpoints in microservices.

## Purpose
- **Production**: This is a production-ready library that provides generic query functionality using JPA
- **Usage**: Used by microservices to expose query/search endpoints without writing boilerplate code
- **Integration**: Works with MyBatis mappers to execute dynamic queries against databases

## Key Components

### Main Production Classes
1. **ChenileRepositoryImpl** - Main repository implementation that executes queries
2. **ChenileRepositoryProxyFactory** - Creates proxy beans for repository interfaces
3. **ChenileMapper** - Spring BeanWrapper-based mapper for mapping query results to entities
4. **ChenileQueryResultHelper** - Helper for processing query results

### Annotation Classes
1. **@EnableChenileRepositories** - Spring annotation to enable Chenile repositories
2. **@ChenileRepositoriesRegistrar** - Registers repository beans

## Test Coverage
The module includes unit tests:
- `ChenileRepositoryTest.java` - Tests for repository functionality
- `ChenileRepositoryProxyFactoryTest.java` - Tests for proxy factory

## Dependencies
- `query-api` - Query service API definitions
- `chenile-core` - Chenile core framework
- `spring-context` - Spring context support
- `spring-boot-starter-test` - Test dependencies

## Usage in HomeBase
In the HomeBase project, this module is used via:
- `user-service/pom.xml` - depends on `chenile-query-data-jpa`
- Configuration in `ChenileRepositoryConfig.java`

## Version
This is part of `chenile-query-workflow-blueprints` parent project.
