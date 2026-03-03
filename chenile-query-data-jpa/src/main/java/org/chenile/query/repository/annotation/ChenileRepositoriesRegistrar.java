package org.chenile.query.repository.annotation;

import org.chenile.query.annotation.ChenileRepositoryDefinition;
import org.chenile.query.repository.impl.ChenileRepositoryProxyFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registers ChenileRepository interfaces found in the specified packages.
 */
public class ChenileRepositoriesRegistrar implements ImportBeanDefinitionRegistrar, BeanClassLoaderAware {

    private ClassLoader classLoader;

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        Set<String> basePackages = getBasePackages(importingClassMetadata);
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(ChenileRepositoryDefinition.class));

        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                registerRepositoryProxy(candidate, registry);
            }
        }
    }

    private void registerRepositoryProxy(BeanDefinition candidate, BeanDefinitionRegistry registry) {
        try {
            Class<?> repositoryInterface = ClassUtils.forName(candidate.getBeanClassName(), classLoader);
            ChenileRepositoryDefinition annotation = repositoryInterface.getAnnotation(ChenileRepositoryDefinition.class);
            if (annotation == null) return;

            Class<?> entityClass = annotation.entityClass();

            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(ChenileRepositoryProxyFactory.class);
            builder.addConstructorArgValue(repositoryInterface);
            builder.addConstructorArgValue(entityClass);

            String beanName = getBeanName(repositoryInterface);
            registry.registerBeanDefinition(beanName, builder.getBeanDefinition());

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to register Chenile repository", e);
        }
    }

    private String getBeanName(Class<?> repositoryInterface) {
        String name = repositoryInterface.getSimpleName();
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }

    private Set<String> getBasePackages(AnnotationMetadata importingClassMetadata) {
        Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes(EnableChenileRepositories.class.getCanonicalName());
        Set<String> basePackages = new HashSet<>();
        for (String pkg : (String[]) attributes.get("value")) {
            if (pkg != null && !pkg.isEmpty()) basePackages.add(pkg);
        }
        for (String pkg : (String[]) attributes.get("basePackages")) {
            if (pkg != null && !pkg.isEmpty()) basePackages.add(pkg);
        }
        if (basePackages.isEmpty()) {
            basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }
        return basePackages;
    }
}
