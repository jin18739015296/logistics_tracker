package com.logistics.api.security;

import com.logistics.api.service.TokenRedisService;
import com.logistics.api.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final TokenRedisService tokenRedisService;
    
    public JwtAuthenticationFilter(JwtUtils jwtUtils, UserDetailsService userDetailsService, TokenRedisService tokenRedisService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
        this.tokenRedisService = tokenRedisService;
    }

    /**
     * WebSocket 升级 / SockJS 预检请求上若带过期 Bearer，此处不应写 401，否则握手直接被掐断。
     * STOMP 的鉴权仍可通过后续 {@code connectHeaders} 在消息层扩展（当前未强制）。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return uri.contains("/ws") || uri.contains("/stomp");
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            token = authorizationHeader.substring(7);
            try {
                username = jwtUtils.getUsernameFromToken(token);
            } catch (Exception e) {
                log.debug("从令牌获取用户名失败: {}", e.getMessage());
            }
        }

        // 请求带了 token 但解析不出用户名，或 token 验证失败 → 返回 401，触发前端重新登录
        if (authorizationHeader != null && username == null) {
            log.warn("JWT认证失败: 令牌无效或格式错误 - uri={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"登录已过期或已在其他设备登录，请重新登录\"}");
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtUtils.validateAccessToken(token)) {
                if (tokenRedisService.isUserBlacklisted(username)) {
                    log.warn("用户已被拉黑，拒绝访问: username={}, uri={}", username, request.getRequestURI());

                    String blacklistInfo = tokenRedisService.getBlacklistInfo(username);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(String.format(
                        "{\"code\":403,\"message\":\"账号已被封禁，如有疑问请联系管理员\",\"blacklistInfo\":%s}",
                        blacklistInfo != null ? blacklistInfo : "{}"
                    ));
                    return;
                }

                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                log.debug("JWT认证成功: username={}, uri={}", username, request.getRequestURI());
            } else {
                log.warn("JWT认证失败: 令牌无效或已被列入黑名单 - username={}, uri={}", username, request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"登录已过期或已在其他设备登录，请重新登录\"}");
                return;
            }
        }

        // 未携带 token 的请求，让 Spring Security 后续处理（返回 403）
        filterChain.doFilter(request, response);
    }
}
