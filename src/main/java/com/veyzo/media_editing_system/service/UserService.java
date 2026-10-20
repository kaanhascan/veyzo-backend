package com.veyzo.media_editing_system.service;

import com.veyzo.media_editing_system.exception.BusinessException;
import com.veyzo.media_editing_system.model.User;
import com.veyzo.media_editing_system.repository.UserRepository;
import com.veyzo.media_editing_system.dto.request.LoginRequest;
import com.veyzo.media_editing_system.dto.request.RegisterRequest;
import com.veyzo.media_editing_system.dto.response.AuthResponse;
import com.veyzo.media_editing_system.dto.response.UserProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;


    public User register(RegisterRequest req){
        if(userRepository.existsByEmail(req.email()))
            throw new BusinessException("Bu e-posta zaten kayıtlı.", HttpStatus.CONFLICT);
        if(userRepository.existsByUsername(req.username()))
            throw new BusinessException("Bu kullanıcı adı zaten kayıtlı.", HttpStatus.CONFLICT);

        return userRepository.save(
                User.builder()
                        .username(req.username())
                        .email(req.email())
                        .password(passwordEncoder.encode(req.password()))
                        .build()
        );
    }

    public AuthResponse login(LoginRequest req){
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı.", HttpStatus.NOT_FOUND));

        String jwtToken = jwtService.generateToken(user);
        return new AuthResponse(jwtToken);
    }

    public UserProfileDto getMyProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı.", HttpStatus.NOT_FOUND));

        return new UserProfileDto(
                user.getActualUsername(),
                user.getEmail(),
                "Pro (Sınırsız)",
                user.getCreatedAt()
        );
    }
}
