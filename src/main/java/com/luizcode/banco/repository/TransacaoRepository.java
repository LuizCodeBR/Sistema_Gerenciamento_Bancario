package com.luizcode.banco.repository;

import com.luizcode.banco.model.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TransacaoRepository extends JpaRepository<Transacao, UUID> {
    Optional<Transacao> findByChaveIdempotencia(String chaveIdempotencia);
}
