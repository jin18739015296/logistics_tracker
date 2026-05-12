package com.logistics.api.util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 图形验证码工具类
 * 生成干扰线、噪点的图形验证码
 */
@Slf4j
public class CaptchaUtil {

    // 验证码字符集
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    // 验证码长度
    private static final int CODE_LENGTH = 4;
    // 图片宽度
    private static final int WIDTH = 120;
    // 图片高度
    private static final int HEIGHT = 40;
    // 干扰线数量
    private static final int LINE_COUNT = 15;
    // 噪点数量
    private static final int NOISE_COUNT = 30;

    private static final Random random = new SecureRandom();

    /**
     * 生成图形验证码
     *
     * @return 包含验证码和Base64图片的Map
     */
    public static Map<String, String> generateCaptcha() {
        // 生成随机验证码
        String code = generateRandomCode();
        
        // 创建图片
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 设置背景色
        g.setColor(getRandomColor(200, 250));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 绘制干扰线
        drawLines(g);

        // 绘制噪点
        drawNoise(g);

        // 绘制验证码
        drawCode(g, code);

        // 扭曲效果
        shear(g, WIDTH, HEIGHT);

        g.dispose();

        // 转换为Base64
        String base64Image = imageToBase64(image);

        Map<String, String> result = new HashMap<>();
        result.put("code", code);
        result.put("image", base64Image);
        
        return result;
    }

    /**
     * 生成随机验证码
     */
    private static String generateRandomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    /**
     * 绘制干扰线
     */
    private static void drawLines(Graphics2D g) {
        for (int i = 0; i < LINE_COUNT; i++) {
            g.setColor(getRandomColor(100, 200));
            int x1 = random.nextInt(WIDTH);
            int y1 = random.nextInt(HEIGHT);
            int x2 = random.nextInt(WIDTH);
            int y2 = random.nextInt(HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }
    }

    /**
     * 绘制噪点
     */
    private static void drawNoise(Graphics2D g) {
        for (int i = 0; i < NOISE_COUNT; i++) {
            g.setColor(getRandomColor(100, 255));
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);
            g.fillOval(x, y, 2, 2);
        }
    }

    /**
     * 绘制验证码文字
     */
    private static void drawCode(Graphics2D g, String code) {
        // 设置字体
        Font[] fonts = {
            new Font("Arial", Font.BOLD, 24),
            new Font("Courier New", Font.BOLD, 24),
            new Font("Verdana", Font.BOLD, 24)
        };

        int x = 15;
        for (int i = 0; i < code.length(); i++) {
            // 随机字体
            g.setFont(fonts[random.nextInt(fonts.length)]);
            // 随机颜色
            g.setColor(getRandomColor(20, 100));
            // 随机旋转
            int rotate = random.nextInt(20) - 10;
            
            // 保存当前变换
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.rotate(Math.toRadians(rotate), x + 10, HEIGHT / 2 + 5);
            g2d.drawString(String.valueOf(code.charAt(i)), x, HEIGHT / 2 + 8);
            g2d.dispose();
            
            x += 25;
        }
    }

    /**
     * 添加扭曲效果
     */
    private static void shear(Graphics2D g, int w, int h) {
        shearX(g, w, h);
        shearY(g, w, h);
    }

    private static void shearX(Graphics2D g, int w, int h) {
        int period = random.nextInt(2) + 1;
        int frames = 15;
        int phase = random.nextInt(2);
        
        for (int i = 0; i < h; i++) {
            double d = (period >> 1) * Math.sin((double) i / (double) period 
                + (2 * Math.PI * phase) / frames);
            g.copyArea(0, i, w, 1, (int) d, 0);
        }
    }

    private static void shearY(Graphics2D g, int w, int h) {
        int period = random.nextInt(10) + 5;
        int frames = 15;
        int phase = random.nextInt(2);
        
        for (int i = 0; i < w; i++) {
            double d = (period >> 1) * Math.sin((double) i / (double) period 
                + (2 * Math.PI * phase) / frames);
            g.copyArea(i, 0, 1, h, 0, (int) d);
        }
    }

    /**
     * 获取随机颜色
     */
    private static Color getRandomColor(int min, int max) {
        int r = min + random.nextInt(max - min);
        int g = min + random.nextInt(max - min);
        int b = min + random.nextInt(max - min);
        return new Color(r, g, b);
    }

    /**
     * 图片转Base64
     */
    private static String imageToBase64(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (IOException e) {
            log.error("图片转Base64失败", e);
            return null;
        }
    }
}
