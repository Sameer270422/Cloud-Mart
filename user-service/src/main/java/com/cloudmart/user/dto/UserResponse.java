package com.cloudmart.user.dto;

import com.cloudmart.user.model.User;

public record UserResponse(Long id, String fullName, String email, String role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole().name());
    }
}
