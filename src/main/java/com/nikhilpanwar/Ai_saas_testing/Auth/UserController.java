package com.nikhilpanwar.Ai_saas_testing.Auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
// ✅ Ye Import Zaroori Hai
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "Anonymous";
    }

    // 1. Upload API (File -> DB)
    @PostMapping("/upload-photo")
    @Transactional // ✅ Error Hatane ke liye Zaroori hai
    public ResponseEntity<String> uploadPhoto(@RequestParam("file") MultipartFile file) {
        String uid = getCurrentUserId();
        Optional<User> userOpt = userRepository.findByFirebaseUid(uid);

        if (userOpt.isPresent()) {
            try {
                User user = userOpt.get();
                user.setProfilePicture(file.getBytes()); // Binary convert
                userRepository.save(user);
                return ResponseEntity.ok("Photo saved in Database");
            } catch (IOException e) {
                return ResponseEntity.internalServerError().body("Error saving file");
            }
        }
        return ResponseEntity.notFound().build();
    }

    // 2. Get Photo API (DB -> Browser)
    @GetMapping("/photo")
    @Transactional // ✅ Error Hatane ke liye Zaroori hai (Read karte waqt bhi)
    public ResponseEntity<byte[]> getPhoto() {
        String uid = getCurrentUserId();
        Optional<User> userOpt = userRepository.findByFirebaseUid(uid);

        if (userOpt.isPresent() && userOpt.get().getProfilePicture() != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(userOpt.get().getProfilePicture());
        }
        return ResponseEntity.notFound().build();
    }
}