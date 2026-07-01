package com.medicalapp.controller;

import com.medicalapp.model.User;
import com.medicalapp.repository.UserRepository;
import com.medicalapp.util.HashUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Optional;

@Controller
public class HomeController {

    @Autowired
    private UserRepository userRepository;

    @PostConstruct
    public void initAdmin() {
        Optional<User> adminOpt = userRepository.findByEmail("admin@system.com");
        if (adminOpt.isEmpty()) {
            User admin = new User();
            admin.setEmail("admin@system.com");
            admin.setPassword(HashUtils.hashPassword("admin123"));
            admin.setRole("admin");
            admin.setName("Administrator");
            admin.setPhone("0000000000");
            admin.setAddress("System Address");
            admin.setApproved(true);
            userRepository.save(admin);
        }
    }

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/register")
    public String handleRegister(@RequestParam String email,
                                 @RequestParam String password,
                                 @RequestParam String confirm_password,
                                 @RequestParam String name,
                                 @RequestParam String role,
                                 @RequestParam String phone,
                                 @RequestParam String address,
                                 @RequestParam(required = false, defaultValue = "") String specialization,
                                 @RequestParam(required = false, defaultValue = "") String qualification,
                                 @RequestParam(required = false, defaultValue = "") String experience,
                                 @RequestParam(required = false, defaultValue = "0.0") double consultation_fee,
                                 RedirectAttributes redirectAttributes) {

        if (!password.equals(confirm_password)) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match!");
            return "redirect:/register";
        }

        if (userRepository.findByEmail(email).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Email already registered!");
            return "redirect:/register";
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(HashUtils.hashPassword(password));
        user.setName(name);
        user.setRole(role);
        user.setPhone(phone);
        user.setAddress(address);

        if ("doctor".equalsIgnoreCase(role)) {
            user.setSpecialization(specialization);
            user.setQualification(qualification);
            user.setExperience(experience);
            user.setApproved(false); // Doctors require admin approval
            user.setConsultationFee(consultation_fee);
        } else {
            user.setApproved(true);
        }

        userRepository.save(user);

        if ("doctor".equalsIgnoreCase(role)) {
            redirectAttributes.addFlashAttribute("success", "Registration successful! Please wait for admin approval.");
        } else {
            redirectAttributes.addFlashAttribute("success", "Registration successful! Please login.");
        }

        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/login")
    public String handleLogin(@RequestParam String email,
                              @RequestParam String password,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (HashUtils.checkPassword(password, user.getPassword())) {
                if ("doctor".equalsIgnoreCase(user.getRole()) && !user.isApproved()) {
                    redirectAttributes.addFlashAttribute("warning", "Your account is pending approval from admin.");
                    return "redirect:/login";
                }

                session.setAttribute("user_id", user.getId());
                session.setAttribute("role", user.getRole());
                session.setAttribute("name", user.getName());
                session.setAttribute("email", user.getEmail());

                if ("patient".equalsIgnoreCase(user.getRole())) {
                    return "redirect:/patient/dashboard";
                } else if ("doctor".equalsIgnoreCase(user.getRole())) {
                    return "redirect:/doctor/dashboard";
                } else if ("admin".equalsIgnoreCase(user.getRole())) {
                    return "redirect:/admin/dashboard";
                }
            }
        }

        redirectAttributes.addFlashAttribute("error", "Invalid email or password!");
        return "redirect:/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "forgot_password";
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(@RequestParam String email, RedirectAttributes redirectAttributes) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("success", "A password reset link has been sent to " + email + " (Mocked)");
        } else {
            redirectAttributes.addFlashAttribute("info", "If that email exists, a reset link was sent.");
        }
        return "redirect:/login";
    }

    @GetMapping("/reset-password/{token}")
    public String resetPassword(@PathVariable String token, Model model) {
        model.addAttribute("token", token);
        return "reset_password";
    }

    @PostMapping("/reset-password/{token}")
    public String handleResetPassword(@PathVariable String token, @RequestParam String password, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("success", "Password reset successful! Please login.");
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        session.invalidate();
        redirectAttributes.addFlashAttribute("success", "Logged out successfully!");
        return "redirect:/home";
    }

    // Spring Boot alias for logout redirect
    @GetMapping("/home")
    public String homeRedirect() {
        return "redirect:/";
    }
}
