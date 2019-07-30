/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.rest;

import io.axoniq.axonserver.AxonServerAccessController;
import io.axoniq.axonserver.AxonServerStandardAccessController;
import io.axoniq.axonserver.config.AccessControlConfiguration;
import io.axoniq.axonserver.config.MessagingPlatformConfiguration;
import io.axoniq.axonserver.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

/**
 * @author Marc Gathier
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final AccessControlConfiguration accessControlConfiguration;
    private final AxonServerAccessController accessController;

    private final DataSource dataSource;

    public WebSecurityConfiguration(MessagingPlatformConfiguration messagingPlatformConfiguration,
                                    AxonServerAccessController accessController, DataSource dataSource) {
        this.accessControlConfiguration = messagingPlatformConfiguration.getAccesscontrol();
        this.accessController = accessController;
        this.dataSource = dataSource;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable();
        http.headers().frameOptions().disable();
        if (accessControlConfiguration.isEnabled()) {
            final TokenAuthenticationFilter tokenFilter = new TokenAuthenticationFilter(accessController);
            http.addFilterBefore(tokenFilter, BasicAuthenticationFilter.class);
            ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry auth = http
                    .authorizeRequests();

            if (accessController.isRoleBasedAuthentication()) {
                auth.antMatchers("/", "/**/*.html", "/v1/**")
                    .authenticated()
                    .accessDecisionManager(new AffirmativeBased(
                            Collections.singletonList(
                                    new RestRequestAccessDecisionVoter(accessController))));
            } else {
                auth.antMatchers("/", "/**/*.html", "/v1/**")
                    .authenticated();
                auth.anyRequest().permitAll();
            }
            auth
                    .and()
                    .formLogin()
                    .loginPage("/login")
                    .permitAll()
                    .and()
                    .logout()
                    .permitAll();
        } else {
            http.authorizeRequests().anyRequest().permitAll();
        }
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth, PasswordEncoder passwordEncoder) throws Exception {
        if (accessControlConfiguration.isEnabled()) {
            auth.jdbcAuthentication()
                .dataSource(dataSource)
                .usersByUsernameQuery(accessController.usersByUsernameQuery())
                .authoritiesByUsernameQuery(accessController.authoritiesByUsernameQuery())
                .passwordEncoder(passwordEncoder);
        }
    }

    public static class AuthenticationToken implements Authentication {

        private final boolean authenticated;
        private final String name;
        private final Set<GrantedAuthority> roles;

        AuthenticationToken(boolean authenticated, String name, Set<String> roles) {
            this.authenticated = authenticated;
            this.name = name;
            this.roles = roles.stream()
                              .map(s -> (GrantedAuthority) () -> s)
                              .collect(Collectors.toSet());
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return roles;
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getDetails() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return "Connected Application";
        }

        @Override
        public boolean isAuthenticated() {
            return authenticated;
        }

        @Override
        public void setAuthenticated(boolean b) {
            // authenticated is only set in constructor
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public static class TokenAuthenticationFilter extends GenericFilterBean {

        private final AxonServerAccessController accessController;

        public TokenAuthenticationFilter(AxonServerAccessController accessController) {
            this.accessController = accessController;
        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
                throws IOException, ServletException {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                final HttpServletRequest request = (HttpServletRequest) servletRequest;
                String token = request.getHeader(AxonServerAccessController.TOKEN_PARAM);
                if (token == null) {
                    token = request.getParameter(AxonServerAccessController.TOKEN_PARAM);
                }

                String authorization = request.getHeader("Authorization");

                if (token == null && authorization == null && isLocalRequest(request)) {
                    SecurityContextHolder.getContext().setAuthentication(
                            new AuthenticationToken(true,
                                                    "LocalAdmin",
                                                    accessController.rolesForLocalhost()));
                } else {
                    if (token != null) {
                        Set<String> roles = accessController.getRoles(token);
                        if (roles != null) {
                            SecurityContextHolder.getContext().setAuthentication(
                                    new AuthenticationToken(true,
                                                            "AuthenticatedApp",
                                                            roles));
                        } else {
                            HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
                            httpServletResponse.setStatus(ErrorCode.AUTHENTICATION_INVALID_TOKEN.getHttpCode().value());
                            try (ServletOutputStream outputStream = httpServletResponse.getOutputStream()) {
                                outputStream.println("Invalid token");
                            }
                            return;
                        }
                    } else if (isNonBrowserClient(request) && authorization == null) {
                        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
                        httpServletResponse.setStatus(ErrorCode.AUTHENTICATION_TOKEN_MISSING.getHttpCode().value());
                        try (ServletOutputStream outputStream = httpServletResponse.getOutputStream()) {
                            outputStream.println("Missing header: " + AxonServerStandardAccessController.TOKEN_PARAM);
                        }
                        return;
                    }
                }
            }
            try {
                filterChain.doFilter(servletRequest, servletResponse);
            } finally {
                if (SecurityContextHolder.getContext().getAuthentication() instanceof AuthenticationToken) {
                    SecurityContextHolder.getContext().setAuthentication(null);
                }
            }
        }

        private boolean isNonBrowserClient(HttpServletRequest request) {
            return stopRedirect(request.getHeader(HttpHeaders.USER_AGENT));
        }

        private boolean stopRedirect(String header) {
            if (header == null) {
                return false;
            }
            String lowercaseHeader = header.toLowerCase();
            return lowercaseHeader.startsWith("apache-httpclient") || lowercaseHeader.startsWith("curl")
                    || lowercaseHeader.startsWith("wget");
        }

        /**
         * Returns true if the request comes from localhost and is allowed to be call without authentication.
         * This is required mainly to enable CLI requests that comes from the same host of Axon Server.
         *
         * @param httpServletRequest the request
         * @return true if the request comes from localhost and is allowed to be call without authentication,
         * false otherwise
         */
        private boolean isLocalRequest(HttpServletRequest httpServletRequest) {
            return isNonBrowserClient(httpServletRequest) &&
                    httpServletRequest.getLocalAddr().equals(httpServletRequest.getRemoteAddr());
        }
    }
}
