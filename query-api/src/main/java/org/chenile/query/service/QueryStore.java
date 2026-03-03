package org.chenile.query.service;

import org.chenile.query.model.QueryMetadata;

import java.util.List;
import java.util.Map;

public interface QueryStore {
	public QueryMetadata retrieve(String queryId);
	public Map<String,QueryMetadata> retrieveAll();
}
