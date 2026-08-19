package com.figma.design.controller;

import com.figma.design.model.Page;
import com.figma.design.service.PageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pages")
@RequiredArgsConstructor
public class PageController {

    private final PageService pageService;

    @GetMapping
    public List<Page> findAll() {
        return pageService.findAll();
    }

    @GetMapping("/{id}")
    public Page findById(@PathVariable Long id) {
        return pageService.findById(id);
    }

    @PostMapping
    public ResponseEntity<Page> create(@RequestBody Page page, @RequestParam(required = false) Long projectId) {
        return ResponseEntity.ok(pageService.create(page, projectId));
    }

    @PutMapping("/{id}")
    public Page update(@PathVariable Long id, @RequestBody Page page, @RequestParam(required = false) Long projectId) {
        return pageService.update(id, page, projectId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        pageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}