package com.luizcode.banco.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.luizcode.banco.model.enums.TipoConta;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

import java.util.Locale;

public record CadastroClienteRequest(
        @NotBlank(message = "Informe o CPF.")
        @Pattern(regexp = "[0-9]{11}", message = "O CPF deve conter 11 dígitos.")
        @CPF(message = "CPF inválido.")
        String cpf,
        @NotBlank(message = "Informe o nome completo.")
        @Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres.")
        String nomeCompleto,
        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "E-mail inválido.")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
        String email,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "Informe a senha.")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
        String senha,
        @NotNull(message = "Informe o tipo de conta.")
        TipoConta tipoConta) {

    public CadastroClienteRequest {
        cpf = cpf == null ? null : cpf.strip().replace(".", "").replace("-", "");
        nomeCompleto = nomeCompleto == null ? null : nomeCompleto.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "CadastroClienteRequest[dados protegidos]";
    }
}
