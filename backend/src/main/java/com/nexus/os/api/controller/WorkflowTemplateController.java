package com.nexus.os.api.controller;

import com.nexus.os.temporal.workflows.templates.WorkflowTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only catalog of pre-built workflow templates. Frontend Studio
 * fetches this list to render "start from a template" tiles.
 */
@RestController
@RequestMapping("/api/workflow-templates")
public class WorkflowTemplateController {

    @GetMapping
    public List<WorkflowTemplate> list() {
        return WorkflowTemplate.catalog();
    }

    @GetMapping("/{slug}")
    public WorkflowTemplate get(@PathVariable String slug) {
        return WorkflowTemplate.catalog().stream()
                .filter(t -> t.slug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such template: " + slug));
    }
}
