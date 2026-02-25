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

        String path = request.getRequestURI();

        if (path.equals("/api/test/health")) {
            chain.doFilter(request, response);
            return;
        }

        if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        String token = null;

        // 1. Try to get token from Header
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }
        // 2. BACKUP: Try to get token from Query Parameter (For SSE Streams)
        else if (request.getParameter("token") != null) {
            token = request.getParameter("token");
        }

        if (token != null) {
            try {
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
                String uid = decodedToken.getUid();
                String email = decodedToken.getEmail();

                if (userRepository.findByFirebaseUid(uid).isEmpty()) {
                    User newUser = new User(uid, email, decodedToken.getName());
                    userRepository.save(newUser);
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        uid, null, new ArrayList<>());

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                System.out.println("❌ Auth Error: " + e.getMessage());
            }
        } else if (!request.getRequestURI().contains("/stream/")) {
            // Only log warning if it's NOT a stream request
            System.out.println("⚠️ No Token Found for " + request.getRequestURI());
        }

        chain.doFilter(request, response);
    }
}