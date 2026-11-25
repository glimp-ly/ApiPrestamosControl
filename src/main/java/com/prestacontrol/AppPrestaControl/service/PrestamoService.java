package com.prestacontrol.AppPrestaControl.service;

import com.prestacontrol.AppPrestaControl.dto.PrestamoRequest;
import com.prestacontrol.AppPrestaControl.dto.PrestamoResponse;
import com.prestacontrol.AppPrestaControl.dto.PrestamoUpdateRequest;
import com.prestacontrol.AppPrestaControl.dto.ResumenCarteraResponse;
import com.prestacontrol.AppPrestaControl.entity.Prestamo;
import com.prestacontrol.AppPrestaControl.entity.Usuario;
import com.prestacontrol.AppPrestaControl.repository.PrestamoRepository;
import com.prestacontrol.AppPrestaControl.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PrestamoService {

    private final PrestamoRepository prestamoRepository;
    private final UsuarioRepository usuarioRepository;

    public PrestamoService(PrestamoRepository prestamoRepository, UsuarioRepository usuarioRepository) {
        this.prestamoRepository = prestamoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<PrestamoResponse> listar(String email, Boolean pagado, String buscar) {
        Long usuarioId = resolveUsuario(email).getId();
        String term = (buscar == null || buscar.isBlank()) ? null : buscar.trim();

        List<Prestamo> prestamos;
        if (pagado != null && term != null) {
            prestamos = prestamoRepository.searchByUsuarioIdAndPagado(usuarioId, pagado, term);
        } else if (pagado != null) {
            prestamos = prestamoRepository.findByUsuarioIdAndPagadoOrderByIdDesc(usuarioId, pagado);
        } else if (term != null) {
            prestamos = prestamoRepository.searchByUsuarioId(usuarioId, term);
        } else {
            prestamos = prestamoRepository.findByUsuarioIdOrderByIdDesc(usuarioId);
        }

        return prestamos.stream().map(PrestamoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PrestamoResponse obtenerPorId(String email, Long id) {
        Long usuarioId = resolveUsuario(email).getId();
        return PrestamoResponse.from(findPrestamo(usuarioId, id));
    }

    public PrestamoResponse crear(String email, PrestamoRequest request) {
        Usuario usuario = resolveUsuario(email);

        Prestamo prestamo = new Prestamo();
        prestamo.setCliente(request.cliente().trim());
        prestamo.setDni(request.dni().trim());
        prestamo.setMonto(request.monto().setScale(2, RoundingMode.HALF_UP));
        prestamo.setInteres(request.interes().setScale(2, RoundingMode.HALF_UP));
        prestamo.setFechaPrestamo(LocalDate.now());
        prestamo.setFechaVencimiento(request.fechaVencimiento());
        prestamo.setPagado(Boolean.TRUE.equals(request.pagado()));
        prestamo.setUsuario(usuario);

        return PrestamoResponse.from(prestamoRepository.save(prestamo));
    }

    public PrestamoResponse actualizar(String email, Long id, PrestamoUpdateRequest request) {
        Long usuarioId = resolveUsuario(email).getId();
        Prestamo prestamo = findPrestamo(usuarioId, id);

        if (request.cliente() != null) {
            prestamo.setCliente(request.cliente().trim());
        }
        if (request.dni() != null) {
            prestamo.setDni(request.dni().trim());
        }
        if (request.monto() != null) {
            prestamo.setMonto(request.monto().setScale(2, RoundingMode.HALF_UP));
        }
        if (request.interes() != null) {
            prestamo.setInteres(request.interes().setScale(2, RoundingMode.HALF_UP));
        }
        if (request.fechaVencimiento() != null) {
            prestamo.setFechaVencimiento(request.fechaVencimiento());
        }
        if (request.pagado() != null) {
            prestamo.setPagado(request.pagado());
        }

        return PrestamoResponse.from(prestamoRepository.save(prestamo));
    }

    public void eliminar(String email, Long id) {
        Long usuarioId = resolveUsuario(email).getId();
        Prestamo prestamo = findPrestamo(usuarioId, id);
        prestamoRepository.delete(prestamo);
    }

    @Transactional(readOnly = true)
    public ResumenCarteraResponse obtenerResumen(String email) {
        Long usuarioId = resolveUsuario(email).getId();
        List<Prestamo> prestamos = prestamoRepository.findByUsuarioIdOrderByIdDesc(usuarioId);

        BigDecimal capitalPrestado = BigDecimal.ZERO;
        BigDecimal totalPorCobrar = BigDecimal.ZERO;
        BigDecimal gananciaEsperada = BigDecimal.ZERO;
        long prestamosPendientes = 0;
        long prestamosPagados = 0;

        for (Prestamo prestamo : prestamos) {
            BigDecimal totalCobrar = prestamo.calcularTotalCobrar();
            capitalPrestado = capitalPrestado.add(prestamo.getMonto());
            gananciaEsperada = gananciaEsperada.add(totalCobrar.subtract(prestamo.getMonto()));
            if (prestamo.isPagado()) {
                prestamosPagados++;
            } else {
                prestamosPendientes++;
                totalPorCobrar = totalPorCobrar.add(totalCobrar);
            }
        }

        return new ResumenCarteraResponse(
                capitalPrestado.setScale(2, RoundingMode.HALF_UP),
                totalPorCobrar.setScale(2, RoundingMode.HALF_UP),
                gananciaEsperada.setScale(2, RoundingMode.HALF_UP),
                prestamos.size(),
                prestamosPendientes,
                prestamosPagados
        );
    }

    private Usuario resolveUsuario(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return usuarioRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private Prestamo findPrestamo(Long usuarioId, Long id) {
        return prestamoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Préstamo no encontrado"));
    }
}
