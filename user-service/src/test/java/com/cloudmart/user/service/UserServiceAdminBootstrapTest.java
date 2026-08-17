package com.cloudmart.user.service;

import com.cloudmart.user.dto.RegisterRequest;
import com.cloudmart.user.model.User;
import com.cloudmart.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:useradmintestdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserServiceAdminBootstrapTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void theFirstRegisteredUserBecomesAdmin() {
        var response = userService.register(new RegisterRequest("First User", "first@example.com", "password123"));

        assertThat(response.role()).isEqualTo("ADMIN");
        User saved = userRepository.findByEmail("first@example.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    void subsequentRegisteredUsersAreCustomers() {
        userService.register(new RegisterRequest("First User", "first@example.com", "password123"));

        var second = userService.register(new RegisterRequest("Second User", "second@example.com", "password123"));

        assertThat(second.role()).isEqualTo("CUSTOMER");
    }
}
