package com.logistics.api.service;

import com.logistics.api.dto.RealTimeLocationDTO;
import com.logistics.api.dto.TrackPointDTO;
import com.logistics.api.model.OrderTrack;
import com.logistics.api.model.TrackingRecord;

import java.math.BigDecimal;
import java.util.List;

public interface TrackingService {

    List<OrderTrack> getOrderTracks(Long orderId);

    TrackingRecord updateRealTimeLocation(Long orderId, Double latitude, Double longitude, String location);

    TrackingRecord createTrackingRecordFromIoT(Long orderId, String deviceId, Double latitude, Double longitude, Double temperature, Double humidity);

    TrackingRecord getLatestTrackingRecord(Long orderId);

    RealTimeLocationDTO uploadRealTimeLocation(Long orderId, Long courierId, BigDecimal latitude, BigDecimal longitude, Double speed, Double direction);

    RealTimeLocationDTO getLatestLocation(Long orderId);

    List<TrackPointDTO> getTrackPoints(Long orderId);
}
