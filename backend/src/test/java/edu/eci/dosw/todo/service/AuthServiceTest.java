package edu.eci.dosw.todo.service;

import edu.eci.dosw.todo.dto.LoginRequest;
import edu.eci.dosw.todo.dto.RegisterRequest;
import edu.eci.dosw.todo.dto.TokenResponse;
import edu.eci.dosw.todo.dto.UserResponse;
import edu.eci.dosw.todo.entity.Role;
import edu.eci.dosw.todo.entity.UserEntity;
import edu.eci.dosw.todo.exception.EmailAlreadyExistsException;
import edu.eci.dosw.todo.exception.InvalidCredentialsException;
import edu.eci.dosw.todo.exception.UserNotFoundException;
import edu.eci.dosw.todo.repository.UserRepository;
import edu.eci.dosw.todo.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "ContrasenaDemo123!";

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService);
    }

    private UserEntity storedUser() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setName("Ana Pérez");
        user.setEmail("ana@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(Role.USER);
        return user;
    }

    @Test
    void register_shouldCreateUserWithRoleUserAndHashedPassword() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        UserResponse response = authService.register(new RegisterRequest("  Ana Pérez ", "  Ana@Example.COM ", PASSWORD));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getEmail()).isEqualTo("ana@example.com");
        assertThat(saved.getName()).isEqualTo("Ana Pérez");
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(response).isEqualTo(new UserResponse(1L, "Ana Pérez", "ana@example.com", Role.USER));
    }

    @Test
    void register_shouldRejectDuplicatedEmail() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ana", "ANA@example.com", PASSWORD)))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

    @Test
    void register_shouldTranslateUniqueConstraintViolationIntoConflict() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ana", "ana@example.com", PASSWORD)))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void login_shouldIssueTokenWhenCredentialsAreValid() {
        UserEntity user = storedUser();
        TokenResponse token = new TokenResponse("Bearer", "signed-token", 1800);
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
        when(jwtService.issueToken(user)).thenReturn(token);

        assertThat(authService.login(new LoginRequest(" ANA@example.com", PASSWORD))).isEqualTo(token);
    }

    @Test
    void login_shouldRejectWrongPassword() {
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(storedUser()));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).issueToken(any(UserEntity.class));
    }

    @Test
    void login_shouldRejectUnknownEmailWithTheSameError() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void getProfile_shouldReturnNonSensitiveData() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(storedUser()));

        assertThat(authService.getProfile(1L))
                .isEqualTo(new UserResponse(1L, "Ana Pérez", "ana@example.com", Role.USER));
    }

    @Test
    void getProfile_shouldFailWhenTheAccountNoLongerExists() {
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getProfile(5L)).isInstanceOf(UserNotFoundException.class);
    }
}
