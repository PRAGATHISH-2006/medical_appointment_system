package com.medicalapp.config;

import com.medicalapp.repository.NotificationRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private NotificationRepository notificationRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        HttpSession session = request.getSession();
        Long userId = (Long) session.getAttribute("user_id");
        String role = (String) session.getAttribute("role");

        // Allow public pages and assets
        if (uri.equals("/") || uri.equals("/login") || uri.equals("/register") || 
            uri.equals("/forgot-password") || uri.startsWith("/reset-password") ||
            uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/uploads/") ||
            uri.startsWith("/h2-console") || uri.equals("/logout")) {
            return true;
        }

        // Check if logged in
        if (userId == null) {
            response.sendRedirect("/login");
            return false;
        }

        // Check path permissions
        if (uri.startsWith("/patient/") && !"patient".equals(role) && !"admin".equals(role)) {
            response.sendRedirect("/login");
            return false;
        }

        if (uri.startsWith("/doctor/") && !"doctor".equals(role)) {
            response.sendRedirect("/login");
            return false;
        }

        if (uri.startsWith("/admin/") && !"admin".equals(role)) {
            response.sendRedirect("/login");
            return false;
        }

        if (uri.startsWith("/hospital/") && !"hospital".equals(role)) {
            response.sendRedirect("/login");
            return false;
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        if (modelAndView != null) {
            HttpSession session = request.getSession();
            Long userId = (Long) session.getAttribute("user_id");
            if (userId != null) {
                // Add unread count and session properties to thymeleaf views globally
                long unreadCount = notificationRepository.countByUserIdAndIsRead(userId, false);
                modelAndView.addObject("unread_count", unreadCount);
                modelAndView.addObject("name", session.getAttribute("name"));
                modelAndView.addObject("role", session.getAttribute("role"));
                modelAndView.addObject("email", session.getAttribute("email"));
            }
            // Add current request path for active nav item highlighting
            modelAndView.addObject("requestURI", request.getRequestURI());
        }
    }
}
