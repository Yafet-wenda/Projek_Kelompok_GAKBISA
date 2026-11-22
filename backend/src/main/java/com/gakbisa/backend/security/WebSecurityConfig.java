package com.gakbisa.backend.security;

import com.gakbisa.backend.repository.UserRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class WebSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http, UserRepository users,
                                              JsonSecurityErrors errors) throws Exception {
        var webApi = PathPatternRequestMatcher.withDefaults().matcher("/web/api/**");
        var login = new LoginUrlAuthenticationEntryPoint("/login");
        http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/login", "/register", "/catalog",
                    "/css/**", "/js/**", "/images/**", "/media/profile/**", "/web/portfolios/*/content").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/", "/index.html", "/login", "/register", "/catalog",
                    "/css/**", "/js/**", "/images/**", "/media/profile/**", "/web/portfolios/*/content").permitAll()
                .requestMatchers("/error", "/access-denied").permitAll()
                .requestMatchers(HttpMethod.POST, "/register").permitAll()
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").usernameParameter("email")
                .defaultSuccessUrl("/dashboard", true).failureUrl("/login?error").permitAll())
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID"))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, failure) -> {
                    if (webApi.matches(request)) {
                        errors.commence(request, response, failure);
                    } else {
                        login.commence(request, response, failure);
                    }
                })
                .accessDeniedHandler((request, response, denied) -> {
                    if (webApi.matches(request)) {
                        errors.handle(request, response, denied);
                    } else {
                        response.setStatus(403);
                        request.getRequestDispatcher("/access-denied").forward(request, response);
                    }
                }))
            .addFilterAfter(new WebSessionRefreshFilter(users), SecurityContextHolderFilter.class);
        return http.build();
    }
}
