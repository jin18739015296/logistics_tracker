package com.logistics.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dispatch")
public class DispatchConfig {
    
    private RulesConfig rules = new RulesConfig();

    @Data
    public static class RulesConfig {
        private int maxOrdersPerCourier = 5;
        private double searchRadiusKm = 5.0;
        private double expandRadiusKm = 15.0;
        private boolean autoExpand = true;

        /**
         * 顺路性：相对「直接开往当前在送关键节点」多走的里程惩罚系数（占比应最大，默认 10）。
         * 综合分 = routePenaltyWeight * detourKm + directDistanceWeight * directKm + loadPenaltyKmPerOrder * 在送单数
         */
        private double routePenaltyWeight = 10.0;
        /** 到本单寄件点的直线距离权重（次要） */
        private double directDistanceWeight = 1.0;
        /** 每多一单在送，折合增加的公里惩罚（避免总派给同一人） */
        private double loadPenaltyKmPerOrder = 1.5;

        /**
         * 顺路精算只对「直线距离最近」的前 K 人执行，减少 DB 与 CPU；0 表示不限制（全员精算）。
         * 可能漏掉「略远但极顺路」的人，可用权重与 K 折中。
         */
        private int maxCandidatesForDetourScoring = 32;
    }

    public int getMaxOrdersPerCourier() {
        return rules.getMaxOrdersPerCourier();
    }

    public double getSearchRadiusKm() {
        return rules.getSearchRadiusKm();
    }

    public double getExpandRadiusKm() {
        return rules.getExpandRadiusKm();
    }

    public boolean isAutoExpand() {
        return rules.isAutoExpand();
    }
}
