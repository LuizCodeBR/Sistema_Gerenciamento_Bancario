package com.luizcode.banco.repository;

import com.luizcode.banco.model.Conta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContaRepository extends JpaRepository<Conta, UUID> {
    @Query("SELECT c.id FROM Conta c WHERE c.numero = :numero AND c.cliente.id = :titularId")
    Optional<UUID> findIdDoTitular(@Param("numero") String numero, @Param("titularId") UUID titularId);
    @Query("SELECT c.id FROM Conta c WHERE c.numero = :numero")
    Optional<UUID> findIdPorNumero(@Param("numero") String numero);
    Optional<Conta> findByNumero(String numero);
    Optional<Conta> findByNumeroAndClienteId(String numero, UUID clienteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conta c WHERE c.id = :id")
    Optional<Conta> findByIdWithLock(@Param("id") UUID id);

    List<Conta> findAllByClienteId(UUID clienteId);
}