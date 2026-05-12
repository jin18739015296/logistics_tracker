package com.logistics.api.service.support;

import com.logistics.api.common.BusinessException;
import com.logistics.api.common.ErrorCode;
import com.logistics.api.enums.OrderStatus;
import org.springframework.stereotype.Component;

/**
 * 集中校验订单主状态是否允许流转，与 {@link OrderStatus#canTransitionTo} 规则一致。
 */
@Component
public class OrderStatusValidator {

    public void requireTransition(String fromCode, String toCode) {
        if (!OrderStatus.isValidTransition(fromCode, toCode)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR,
                    String.format("当前状态为「%s」，不允许变为「%s」",
                            OrderStatus.getDescByCode(fromCode),
                            OrderStatus.getDescByCode(toCode)));
        }
    }
}
