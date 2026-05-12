package com.logistics.api.dto;

import com.logistics.api.model.Order;
import com.logistics.api.model.OrderAddress;
import com.logistics.api.model.DeliveryTask;
import lombok.Data;

@Data
public class OrderDetailDTO {
    private Order order;
    private OrderAddress senderAddress;
    private OrderAddress receiverAddress;
    private DeliveryTask deliveryTask;
}
