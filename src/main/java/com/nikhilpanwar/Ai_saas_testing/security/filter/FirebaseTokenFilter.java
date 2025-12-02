package com.nikhilpanwar.Ai_saas_testing.security.filter;

import com.nikhilpanwar.Ai_saas_testing.Auth.User;
import com.nikhilpanwar.Ai_saas_testing.Auth.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class FirebaseTokenFilter extends OncePerRequestFilter {

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 🛑 FIX 1: Agar request OPTIONS hai (Pre-flight check), to usse jaane do.
        // Usme Token kabhi nahi hota, isliye check mat karo.
        if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");

        System.out.println("🔍 Request: " + request.getMethod() + " " + request.getRequestURI());

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
                String uid = decodedToken.getUid();
                String email = decodedToken.getEmail();

                System.out.println("✅ Token Valid for User: " + email);

                if (userRepository.findByFirebaseUid(uid).isEmpty()) {
                    User newUser = new User(uid, email, decodedToken.getName());
                    userRepository.save(newUser);
                    System.out.println("🆕 New User Saved to DB");
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        uid, null, new ArrayList<>());

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                System.out.println("❌ Auth Error: " + e.getMessage());
            }
        } else {
            // Sirf tab print karo jab ye OPTIONS na ho (jo humne upar handle kar liya)
            System.out.println("⚠️ No Bearer Token Found in Header for " + request.getRequestURI());
        }

        chain.doFilter(request, response);
    }
}