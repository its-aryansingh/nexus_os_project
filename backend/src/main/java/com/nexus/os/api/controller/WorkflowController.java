package com.nexus.os.api.controller;

import com.nexus.os.domain.Workflow;
import com.nexus.os.domain.WorkflowRepository;
import com.nexus.os.domain.WorkflowRun;
import com.nexus.os.domain.WorkflowRunRepository;
import com.nexus.os.tenancy.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowRepository workflows;
    private final WorkflowRunRepository runs;

    public WorkflowController(WorkflowRepository workflows, WorkflowRunRepository runs) {
        this.workflows = workflows;
        this.runs = runs;
    }

    @GetMapping
    public List<Workflow> list() {
        return workflows.findByTenantIdAndActive(TenantContext.require(), true);
    }

    @GetMapping("/{id}")
    public Workflow get(@PathVariable UUID id) {
        return workflows.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
    }

    @PostMapping
    public Workflow create(@RequestBody Workflow workflow) {
        workflow.setTenantId(TenantContext.require());
        return workflows.save(workflow);
    }

    @GetMapping("/runs")
    public Page<WorkflowRun> runs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return runs.findByTenantIdOrderByStartedAtDesc(TenantContext.require(), PageRequest.of(page, size));
    }
}
