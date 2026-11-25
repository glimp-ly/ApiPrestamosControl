package com.prestacontrol.AppPrestaControl.dto;

import com.prestacontrol.AppPrestaControl.entity.Usuario;

public record AuthResponse(
        String token,
        String tipo,
        String nombre,
        String email,
        String rol
) {
    public static AuthResponse of(String token, Usuario usuario) {
        return new AuthResponse(token, "Bearer", usuario.getNombre(), usuario.getEmail(), usuario.getRol());
    }
}
