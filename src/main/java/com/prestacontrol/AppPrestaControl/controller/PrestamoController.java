package com.prestacontrol.AppPrestaControl.controller;

import com.prestacontrol.AppPrestaControl.dto.PrestamoRequest;
import com.prestacontrol.AppPrestaControl.dto.PrestamoResponse;
import com.prestacontrol.AppPrestaControl.dto.PrestamoUpdateRequest;
import com.prestacontrol.AppPrestaControl.dto.ResumenCarteraResponse;
import com.prestacontrol.AppPrestaControl.service.PrestamoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/prestamos")
public class PrestamoController {

    private final PrestamoService prestamoService;

    public PrestamoController(PrestamoService prestamoService) {
        this.prestamoService = prestamoService;
    }

    @GetMapping
    public ResponseEntity<List<PrestamoResponse>> listar(
            Authentication authentication,
            @RequestParam(required = false) Boolean pagado,
            @RequestParam(required = false) String buscar
    ) {
        return ResponseEntity.ok(prestamoService.listar(authentication.getName(), pagado, buscar));
    }

    @GetMapping("/resumen")
    public ResponseEntity<ResumenCarteraResponse> resumen(Authentication authentication) {
        return ResponseEntity.ok(prestamoService.obtenerResumen(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrestamoResponse> obtenerPorId(
            Authentication authentication,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(prestamoService.obtenerPorId(authentication.getName(), id));
    }

    @PostMapping
    public ResponseEntity<PrestamoResponse> crear(
            Authentication authentication,
            @Valid @RequestBody PrestamoRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(prestamoService.crear(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PrestamoResponse> actualizar(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody PrestamoUpdateRequest request
    ) {
        return ResponseEntity.ok(prestamoService.actualizar(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            Authentication authentication,
            @PathVariable Long id
    ) {
        prestamoService.eliminar(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
