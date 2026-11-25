package com.prestacontrol.AppPrestaControl.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PrestamoUpdateRequest(
        @Size(max = 100, message = "El nombre del cliente no debe exceder 100 caracteres")
        @Pattern(regexp = ".*\\S.*", message = "El cliente no puede estar vacío")
        String cliente,

        @Pattern(regexp = "\\d{8}", message = "El DNI debe tener 8 dígitos")
        String dni,

        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
        @Digits(integer = 8, fraction = 2, message = "El monto debe tener máximo 8 enteros y 2 decimales")
        BigDecimal monto,

        @DecimalMin(value = "0.00", message = "El interés no puede ser negativo")
        @DecimalMax(value = "999.99", message = "El interés no puede exceder 999.99")
        @Digits(integer = 3, fraction = 2, message = "El interés debe tener máximo 3 enteros y 2 decimales")
        BigDecimal interes,

        LocalDate fechaVencimiento,

        Boolean pagado
) {
}
