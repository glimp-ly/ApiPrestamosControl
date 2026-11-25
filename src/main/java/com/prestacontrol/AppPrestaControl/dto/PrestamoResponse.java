package com.prestacontrol.AppPrestaControl.dto;

import com.prestacontrol.AppPrestaControl.entity.Prestamo;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PrestamoResponse(
        Long id,
        String cliente,
        String dni,
        BigDecimal monto,
        BigDecimal interes,
        BigDecimal totalCobrar,
        LocalDate fechaPrestamo,
        LocalDate fechaVencimiento,
        boolean pagado
) {
    public static PrestamoResponse from(Prestamo prestamo) {
        return new PrestamoResponse(
                prestamo.getId(),
                prestamo.getCliente(),
                prestamo.getDni(),
                prestamo.getMonto(),
                prestamo.getInteres(),
                prestamo.calcularTotalCobrar(),
                prestamo.getFechaPrestamo(),
                prestamo.getFechaVencimiento(),
                prestamo.isPagado()
        );
    }
}
