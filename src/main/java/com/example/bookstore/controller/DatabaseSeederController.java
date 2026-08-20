package com.example.bookstore.controller;

import com.example.bookstore.dto.SeedRequest;
import com.example.bookstore.dto.SeedResult;
import com.example.bookstore.service.DatabaseSeederService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/seeder")
@RequiredArgsConstructor
public class DatabaseSeederController {

    private final DatabaseSeederService databaseSeederService;

    @PostMapping("/run")
    public SeedResult runSeeder(@RequestBody(required = false) SeedRequest request) {
        return databaseSeederService.seedData(request);
    }
}
