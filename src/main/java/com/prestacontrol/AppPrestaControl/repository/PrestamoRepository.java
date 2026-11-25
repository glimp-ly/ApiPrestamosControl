package com.prestacontrol.AppPrestaControl.repository;

import com.prestacontrol.AppPrestaControl.entity.Prestamo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

    List<Prestamo> findByUsuarioIdOrderByIdDesc(Long usuarioId);

    List<Prestamo> findByUsuarioIdAndPagadoOrderByIdDesc(Long usuarioId, boolean pagado);

    @Query("""
            SELECT p FROM Prestamo p
            WHERE p.usuario.id = :usuarioId
              AND (LOWER(p.cliente) LIKE LOWER(CONCAT('%', :buscar, '%'))
                   OR p.dni LIKE CONCAT('%', :buscar, '%'))
            ORDER BY p.id DESC
            """)
    List<Prestamo> searchByUsuarioId(@Param("usuarioId") Long usuarioId, @Param("buscar") String buscar);

    @Query("""
            SELECT p FROM Prestamo p
            WHERE p.usuario.id = :usuarioId
              AND p.pagado = :pagado
              AND (LOWER(p.cliente) LIKE LOWER(CONCAT('%', :buscar, '%'))
                   OR p.dni LIKE CONCAT('%', :buscar, '%'))
            ORDER BY p.id DESC
            """)
    List<Prestamo> searchByUsuarioIdAndPagado(@Param("usuarioId") Long usuarioId,
                                              @Param("pagado") boolean pagado,
                                              @Param("buscar") String buscar);

    Optional<Prestamo> findByIdAndUsuarioId(Long id, Long usuarioId);
}
