package com.logistics.api.service;

import com.logistics.api.model.User;

import java.util.List;
import java.util.Map;

public interface DispatchService {

    User autoAssignCourier(Long orderId);

    List<User> getAvailableCouriers(String city);

    void dispatchOrder(Long orderId, Long courierId);

    void confirmOrderBy(Long orderId, Long courierId);

    void rejectOrder(Long orderId, Long courierId, String reason);
}
