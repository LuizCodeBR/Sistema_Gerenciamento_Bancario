package com.luizcode.banco.security;

import com.luizcode.banco.repository.ClienteRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// Instanciado somente na cadeia de segurança, sem registro de filtro servlet duplicado.
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final ClienteRepository clientes;
    private final SecurityErrorHandler errors;
    private final AntPathRequestMatcher cadastro = new AntPathRequestMatcher("/api/v1/clientes", "POST");
    private final AntPathRequestMatcher login = new AntPathRequestMatcher("/api/v1/auth/login", "POST");

    public JwtAuthenticationFilter(JwtService jwt, ClienteRepository clientes, SecurityErrorHandler errors) {
        this.jwt = jwt;
        this.clientes = clientes;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return cadastro.matches(request) || login.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        List<String> headers = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (headers.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String header = headers.get(0);
            if (headers.size() != 1 || header.length() > 8192
                    || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                throw new BadCredentialsException("Token inválido.");
            }
            UUID clienteId = jwt.validar(header.substring(7));
            if (!clientes.existsById(clienteId)) {
                throw new BadCredentialsException("Token inválido.");
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    new ClienteAutenticado(clienteId), null,
                    List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
            SecurityContextHolder.setContext(context);
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            errors.commence(request, response, exception);
            return;
        }
        chain.doFilter(request, response);
    }
}
