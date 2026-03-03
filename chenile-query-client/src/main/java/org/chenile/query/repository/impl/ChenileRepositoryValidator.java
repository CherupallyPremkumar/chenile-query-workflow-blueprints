package org.chenile.query.repository.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import java.util.List;

/**
 * Ensures that all Chenile Repositories are strictly validated after the
 * application is ready
 * and metadata has been synchronized.
 */
@Order(60010)
public class ChenileRepositoryValidator implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger logger = LoggerFactory.getLogger(ChenileRepositoryValidator.class);

    @Autowired(required = false)
    private List<ChenileRepositoryProxyFactory<?, ?>> factories;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (factories == null || factories.isEmpty()) {
            logger.info("No Chenile repositories found for validation.");
            return;
        }

        logger.info("Starting strict validation for {} Chenile repositories...", factories.size());
        for (ChenileRepositoryProxyFactory<?, ?> factory : factories) {
            try {
                factory.validate();
            } catch (Exception e) {
                logger.error("STRICT VALIDATION FAILED for repository: {}", e.getMessage());
                throw new RuntimeException("Chenile Repository Validation Failed", e);
            }
        }
        logger.info("All Chenile repositories validated successfully.");
    }
}
