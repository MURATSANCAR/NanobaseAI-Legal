package com.nanobase.specai.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantTransactionFilter extends OncePerRequestFilter {
    private final CurrentTenant currentTenant;
    private final TenantDatabaseContext databaseContext;
    private final TransactionTemplate transactions;

    public TenantTransactionFilter(CurrentTenant currentTenant,
                                   TenantDatabaseContext databaseContext,
                                   PlatformTransactionManager transactionManager) {
        this.currentTenant = currentTenant;
        this.databaseContext = databaseContext;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
        throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken)
            || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            transactions.executeWithoutResult(ignored -> {
                databaseContext.apply(currentTenant.require().tenantId());
                try {
                    filterChain.doFilter(request, response);
                } catch (IOException | ServletException exception) {
                    throw new FilterExecutionException(exception);
                }
            });
        } catch (FilterExecutionException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw (ServletException) exception.getCause();
        }
    }

    private static final class FilterExecutionException extends RuntimeException {
        private FilterExecutionException(Exception cause) {
            super(cause);
        }
    }
}
