package com.cloudmart.user;

import com.cloudmart.user.dto.RegisterRequest;
import com.cloudmart.user.repository.UserRepository;
import com.cloudmart.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceApplicationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void registerCreatesUserAndReturnsToken() {
        var request = new RegisterRequest("Jane Doe", "jane" + System.nanoTime() + "@example.com", "password123");
        var response = userService.register(request);

        assertThat(response.token()).isNotBlank();
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(userRepository.existsByEmail(request.email())).isTrue();
    }

    @Test
    void registerRejectsDuplicateEmail() {
        var email = "dupe" + System.nanoTime() + "@example.com";
        userService.register(new RegisterRequest("First", email, "password123"));

        assertThrows(IllegalArgumentException.class, () ->
                userService.register(new RegisterRequest("Second", email, "password123")));
    }
}
