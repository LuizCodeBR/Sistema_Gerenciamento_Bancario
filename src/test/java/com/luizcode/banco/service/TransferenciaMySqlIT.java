package com.luizcode.banco.service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Execução opt-in pelo perfil mysql-it. Usa exclusivamente o schema descartável abaixo.
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/banco_portfolio_it?serverTimezone=UTC",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.datasource.username=${MYSQL_TEST_USER:banco_test}",
    "spring.datasource.password=${MYSQL_TEST_PASS:}",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"
})
@ActiveProfiles("test")
class TransferenciaMySqlIT extends TransferenciaContract { }
