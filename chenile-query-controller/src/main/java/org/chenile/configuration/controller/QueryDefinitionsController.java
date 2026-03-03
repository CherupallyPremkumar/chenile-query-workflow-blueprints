package org.chenile.configuration.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.chenile.base.response.GenericResponse;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.handler.ControllerSupport;
import org.chenile.query.model.QueryMetadata;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@ChenileController(value = "chenileQueryDefinitions", serviceName = "queryDefinitions")
public class QueryDefinitionsController extends ControllerSupport {

    @GetMapping("/queryDefinition/{queryName}")
    public ResponseEntity<GenericResponse<QueryMetadata>> retrieve(
            HttpServletRequest request,
            @PathVariable String queryName) {
        return process("retrieve",request,queryName);
    }

    @GetMapping("/queryDefinitions")
    public ResponseEntity<GenericResponse<Map<String, QueryMetadata>>> retrieveAll(
            HttpServletRequest request) {
        return process("retrieveAll",request);
    }
}
