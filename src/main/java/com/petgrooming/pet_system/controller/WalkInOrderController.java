package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.dto.*;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.WalkInOrderService;
import com.petgrooming.pet_system.service.WalkInOrderOperatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 現場開單 API（店家後台）
 *
 * POST /api/admin/walk-in-orders 現場開單
 * GET /api/admin/walk-in-orders 所有現場單（交易紀錄）
 * GET /api/admin/walk-in-orders/pending-operator 待補經手人的項目
 * PUT /api/admin/walk-in-orders/items/{itemId}/operator 補填經手人
 * GET /api/admin/walk-in-orders/points-report 經手人積分結算
 */
@RestController
@RequestMapping("/api/admin/walk-in-orders")
@RequiredArgsConstructor
public class WalkInOrderController {

    private final WalkInOrderService walkInOrderService;
    private final WalkInOrderOperatorService walkInOrderOperatorService;

    @RequireRole({ UserRole.ADMIN, UserRole.STAFF })
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody WalkInOrderCreateRequest req, @CurrentUser String username) {
        return ResponseEntity.ok(walkInOrderService.create(req, username));
    }

    @RequireRole({ UserRole.ADMIN, UserRole.STAFF })
    @GetMapping
    public ResponseEntity<List<WalkInOrderResponse>> listAll() {
        return ResponseEntity.ok(walkInOrderService.listAll());
    }

    @RequireRole({ UserRole.ADMIN, UserRole.STAFF })
    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(walkInOrderService.getById(id));
    }

    @RequireRole({ UserRole.ADMIN, UserRole.STAFF })
    @GetMapping("/pending-operator")
    public ResponseEntity<List<WalkInOrderResponse.ItemLine>> pendingOperator() {
        return ResponseEntity.ok(walkInOrderOperatorService.pendingOperatorItems());
    }

    @RequireRole({ UserRole.ADMIN, UserRole.STAFF })
    @PutMapping("/items/{itemId}/operator")
    public ResponseEntity<?> fillOperator(@PathVariable Long itemId,
            @Valid @RequestBody FillOperatorRequest req) {
        walkInOrderOperatorService.fillOperator(itemId, req.getStaffId());
        return ResponseEntity.ok().build();
    }

    @RequireRole({ UserRole.ADMIN, UserRole.STAFF })
    @GetMapping("/points-report")
    public ResponseEntity<List<OperatorPointsResponse>> pointsReport() {
        return ResponseEntity.ok(walkInOrderOperatorService.operatorPointsReport());
    }

}
