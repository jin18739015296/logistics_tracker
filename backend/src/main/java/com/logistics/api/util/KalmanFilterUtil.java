package com.logistics.api.util;

import java.math.BigDecimal;

/**
 * 简易卡尔曼滤波器，用于平滑GPS轨迹点，消除漂移噪点
 */
public class KalmanFilterUtil {

    // 默认的过程噪声协方差 (Process noise) - 越小表示越信任预测模型
    public static final double DEFAULT_Q = 0.00001;
    // 默认的测量噪声协方差 (Measurement noise) - 越大表示越不信任测量值(GPS)
    public static final double DEFAULT_R = 0.001;

    public static class KalmanState {
        public double latX; // 纬度估计值
        public double latP; // 纬度估计误差协方差
        public double lngX; // 经度估计值
        public double lngP; // 经度估计误差协方差

        public KalmanState() {}

        public KalmanState(double latX, double latP, double lngX, double lngP) {
            this.latX = latX;
            this.latP = latP;
            this.lngX = lngX;
            this.lngP = lngP;
        }
    }

    /**
     * 更新并获取新的平滑状态
     * @param prevState 上一次的卡尔曼状态（如果为null，则使用当前测量值初始化）
     * @param newLat 当前GPS纬度
     * @param newLng 当前GPS经度
     * @return 新的卡尔曼状态
     */
    public static KalmanState filter(KalmanState prevState, double newLat, double newLng) {
        if (prevState == null) {
            // 初始状态，误差协方差设为1.0
            return new KalmanState(newLat, 1.0, newLng, 1.0);
        }

        // 纬度滤波
        double latP = prevState.latP + DEFAULT_Q;
        double latK = latP / (latP + DEFAULT_R);
        double latX = prevState.latX + latK * (newLat - prevState.latX);
        latP = (1 - latK) * latP;

        // 经度滤波
        double lngP = prevState.lngP + DEFAULT_Q;
        double lngK = lngP / (lngP + DEFAULT_R);
        double lngX = prevState.lngX + lngK * (newLng - prevState.lngX);
        lngP = (1 - lngK) * lngP;

        return new KalmanState(latX, latP, lngX, lngP);
    }

    /**
     * 计算两点之间的距离（单位：米）
     */
    public static double calculateDistance(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return 0.0;
        }
        return calculateDistance(lat1.doubleValue(), lng1.doubleValue(), lat2.doubleValue(), lng2.doubleValue());
    }

    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);

        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        s = s * 6378137.0; // 地球半径
        return Math.round(s * 10000d) / 10000d;
    }
}
