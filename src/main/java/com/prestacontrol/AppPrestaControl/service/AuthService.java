package com.prestacontrol.AppPrestaControl.service;

import com.prestacontrol.AppPrestaControl.dto.AuthResponse;
import com.prestacontrol.AppPrestaControl.dto.LoginRequest;
import com.prestacontrol.AppPrestaControl.dto.RegisterRequest;
import com.prestacontrol.AppPrestaControl.entity.Usuario;
import com.prestacontrol.AppPrestaControl.repository.UsuarioRepository;
import com.prestacontrol.AppPrestaControl.security.JwtService;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("El email ya está registrado");
        }

        Usuario usuario = new Usuario(
                request.nombre().trim(),
                email,
                passwordEncoder.encode(request.password()),
                "USER"
        );
        Usuario saved = usuarioRepository.save(usuario);
        return AuthResponse.of(jwtService.generateToken(saved), saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return AuthResponse.of(jwtService.generateToken(usuario), usuario);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
