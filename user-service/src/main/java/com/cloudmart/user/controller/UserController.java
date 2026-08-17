package com.cloudmart.user.controller;

import com.cloudmart.user.dto.UserResponse;
import com.cloudmart.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    // Not admin-gated (no requester-role check) since a user looking up
    // their own profile by id is legitimate - the requesterId check below
    // is what actually matters. Returns 404 rather than 403 for a mismatch
    // so this doesn't confirm/deny whether a given id exists to a caller
    // who isn't its owner.
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id,
                                                 @RequestHeader("X-User-Id") Long requesterId) {
        if (!id.equals(requesterId)) {
            return ResponseEntity.notFound().build();
        }
        return userRepository.findById(id)
                .map(UserResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@RequestHeader("X-User-Email") String email) {
        return userRepository.findByEmail(email)
                .map(UserResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
