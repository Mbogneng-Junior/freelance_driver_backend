/*package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.model.Resource;
import com.freelance.driver_backend.service.resource.ResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
public class DriverSearchController {

    private final ResourceService resourceService;
    private static final String PLANNING_CATEGORY_ID = "ba75b2c0-30a8-11f0-a5b5-bb7d33c83c13";

    @GetMapping("/drivers")
    public Flux<Resource> findAvailableDrivers() {
        log.info("-> Requête PUBLIQUE reçue pour GET /api/search/drivers");
        return resourceService.getResourcesByCategory(PLANNING_CATEGORY_ID)
                .filter(resource -> "AVAILABLE".equalsIgnoreCase(resource.getState()))
                .doOnNext(resource -> log.info("--> Planning publié trouvé : {}", resource.getName()));
    }
}*/


// PATH: /home/mbogneng-junior/freelance-driver (Copie)/backend/src/main/java/com/freelance/driver_backend/controller/DriverSearchController.java

package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.model.Resource;
import com.freelance.driver_backend.service.resource.ResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
public class DriverSearchController {

    private final ResourceService resourceService;
    private static final String PLANNING_CATEGORY_ID = "ba75b2c0-30a8-11f0-a5b5-bb7d33c83c13";

    @GetMapping("/drivers")
    public Flux<Resource> findAvailableDrivers() {
        log.info("-> Requête PUBLIQUE reçue pour GET /api/search/drivers");
        return resourceService.getResourcesByCategory(PLANNING_CATEGORY_ID)
                .filter(resource -> "AVAILABLE".equalsIgnoreCase(resource.getState()))
                .doOnNext(resource -> log.info("--> Planning publié trouvé : {}", resource.getName()));
    }
}