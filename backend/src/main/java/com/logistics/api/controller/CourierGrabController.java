package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.dto.OrderDTO;
import com.logistics.api.model.User;
import com.logistics.api.service.CourierStatusService;
import com.logistics.api.service.GrabOrderService;
import com.logistics.api.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/courier/grab")
public class CourierGrabController {

    @Autowired
    private GrabOrderService grabOrderService;

    @Autowired
    private CourierStatusService courierStatusService;

    @Autowired
    private UserService userService;

    @GetMapping("/orders")
    public ResponseEntity<Result<List<OrderDTO>>> getGrabableOrders(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {

        User courier = userService.getUserByUsername(userDetails.getUsername());
        if (courier == null) {
            return ResponseEntity.status(401).body(Result.error(401, "未登录"));
        }

        List<OrderDTO> orders = grabOrderService.getGrabableOrders(city, lat, lng, limit);

        return ResponseEntity.ok(Result.success(orders));
    }

    @PostMapping("/{orderId}")
    public ResponseEntity<Result<Map<String, Object>>> grabOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User courier = userService.getUserByUsername(userDetails.getUsername());
        if (courier == null) {
            return ResponseEntity.status(401).body(Result.error(401, "未登录"));
        }

        String errorMessage = grabOrderService.tryGrabOrder(orderId, courier.getId());

        if (errorMessage == null) {
            return ResponseEntity.ok(Result.success("抢单成功", Map.of(
                "orderId", orderId,
                "courierId", courier.getId(),
                "courierName", courier.getName() != null ? courier.getName() : ""
            )));
        } else {
            return ResponseEntity.badRequest().body(Result.error(40001, errorMessage));
        }
    }

    @GetMapping("/pool-size")
    public ResponseEntity<Result<Integer>> getGrabPoolSize() {
        int size = grabOrderService.getGrabPoolSize();
        return ResponseEntity.ok(Result.success(size));
    }

    @GetMapping("/check/{orderId}")
    public ResponseEntity<Result<Boolean>> isInGrabPool(@PathVariable Long orderId) {
        boolean inPool = grabOrderService.isInGrabPool(orderId);
        return ResponseEntity.ok(Result.success(inPool));
    }
}
