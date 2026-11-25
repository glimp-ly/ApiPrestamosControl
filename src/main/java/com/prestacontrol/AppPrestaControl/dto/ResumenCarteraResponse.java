package com.prestacontrol.AppPrestaControl.dto;

import java.math.BigDecimal;

public record ResumenCarteraResponse(
        BigDecimal capitalPrestado,
        BigDecimal totalPorCobrar,
        BigDecimal gananciaEsperada,
        long totalPrestamos,
        long prestamosPendientes,
        long prestamosPagados
) {
}
