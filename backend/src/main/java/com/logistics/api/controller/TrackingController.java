package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.dto.LocationUploadRequest;
import com.logistics.api.dto.RealTimeLocationDTO;
import com.logistics.api.dto.TrackPointDTO;
import com.logistics.api.model.OrderTrack;
import com.logistics.api.model.User;
import com.logistics.api.service.OrderService;
import com.logistics.api.service.TrackingService;
import com.logistics.api.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/tracking")
public class TrackingController {

    @Autowired
    private TrackingService trackingService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderService orderService;

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('COURIER', 'courier', 'DELIVERY', 'delivery')")
    public ResponseEntity<Result<RealTimeLocationDTO>> uploadLocation(
            @RequestBody LocationUploadRequest request, Principal principal) {
        
        User user = userService.getUserByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Result.error(10002, "未授权"));
        }

        RealTimeLocationDTO dto = trackingService.uploadRealTimeLocation(
                request.getOrderId(),
                user.getId(),
                request.getLatitude(),
                request.getLongitude(),
                request.getSpeed(),
                request.getDirection()
        );

        return ResponseEntity.ok(Result.success(dto));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('COURIER', 'courier', 'DELIVERY', 'delivery')")
    public ResponseEntity<Result<String>> batchUploadLocations(
            @RequestBody List<LocationUploadRequest> requests, Principal principal) {
        
        User user = userService.getUserByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Result.error(10002, "未授权"));
        }

        for (LocationUploadRequest request : requests) {
            trackingService.uploadRealTimeLocation(
                    request.getOrderId(),
                    user.getId(),
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getSpeed(),
                    request.getDirection()
            );
        }

        return ResponseEntity.ok(Result.success("批量上传成功"));
    }

    @GetMapping("/order/{orderId}/tracks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<List<TrackPointDTO>>> getOrderTracks(
            @PathVariable Long orderId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
        }
        orderService.checkOrderViewPermission(principal.getName(), orderId);
        List<TrackPointDTO> tracks = trackingService.getTrackPoints(orderId);
        return ResponseEntity.ok(Result.success(tracks));
    }

    @GetMapping("/order/{orderId}/latest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<RealTimeLocationDTO>> getLatestLocation(
            @PathVariable Long orderId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
        }
        orderService.checkOrderViewPermission(principal.getName(), orderId);
        RealTimeLocationDTO latest = trackingService.getLatestLocation(orderId);
        if (latest == null) {
            return ResponseEntity.ok(Result.success("暂无位置信息", null));
        }
        return ResponseEntity.ok(Result.success(latest));
    }

    @GetMapping("/records/order/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<List<OrderTrack>>> getTrackingHistory(
            @PathVariable Long orderId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
        }
        orderService.checkOrderViewPermission(principal.getName(), orderId);
        List<OrderTrack> records = trackingService.getOrderTracks(orderId);
        return ResponseEntity.ok(Result.success(records));
    }
}
