package cn.iocoder.yudao.module.ilvy.local;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.servlet.*;
import javax.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Local-only opaque sessions. Never enables mock login in the production host. */
@Configuration
@Profile("ilvy-local")
public class LocalSecurity {
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis());

  private static class Session {
    long id;
    Instant expiry;

    Session(long id) {
      this.id = id;
      this.expiry = Instant.now().plusSeconds(86400);
    }
  }

  @Bean
  SecurityFilterChain localChain(HttpSecurity http) throws Exception {
    http.csrf()
        .disable()
        .cors()
        .configurationSource(localCors())
        .and()
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
        .authorizeRequests()
        .antMatchers(HttpMethod.OPTIONS, "/**")
        .permitAll()
        .antMatchers(HttpMethod.GET, "/app-api/ilvy/places/**", "/app-api/ilvy/capabilities")
        .permitAll()
        .antMatchers(HttpMethod.POST, "/app-api/ilvy/local-session")
        .permitAll()
        .anyRequest()
        .authenticated()
        .and()
        .exceptionHandling()
        .authenticationEntryPoint(
            (q, s, e) -> {
              s.setStatus(401);
              s.setContentType("application/json;charset=UTF-8");
              s.getWriter().write("{\"code\":401,\"msg\":\"请重新建立本机开发会话\"}");
            });
    http.addFilterBefore(
        new OncePerRequestFilter() {
          protected void doFilterInternal(
              HttpServletRequest req, HttpServletResponse res, FilterChain chain)
              throws ServletException, IOException {
            String auth = req.getHeader("Authorization");
            Session session =
                auth != null && auth.startsWith("Bearer ") ? sessions.get(auth.substring(7)) : null;
            try {
              if (session != null && session.expiry.isAfter(Instant.now()))
                SecurityFrameworkUtils.setLoginUser(
                    new LoginUser().setId(session.id).setUserType(1).setTenantId(0L), req);
              chain.doFilter(req, res);
            } finally {
              org.springframework.security.core.context.SecurityContextHolder.clearContext();
            }
          }
        },
        UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  CorsConfigurationSource localCors() {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(Arrays.asList("http://127.0.0.1:10086", "http://localhost:10086"));
    c.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization"));
    UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return s;
  }

  @RestController
  @Profile("ilvy-local")
  class SessionController {
    @PostMapping("/app-api/ilvy/local-session")
    CommonResult<Map<String, Object>> create() {
      sessions.entrySet().removeIf(e -> e.getValue().expiry.isBefore(Instant.now()));
      if (sessions.size() > 1000)
        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "开发会话过多");
      String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
      Session s = new Session(sequence.incrementAndGet());
      sessions.put(token, s);
      Map<String, Object> result = new HashMap<>();
      result.put("token", token);
      result.put("mode", "local-development");
      result.put("expiresAt", s.expiry.toString());
      return CommonResult.success(result);
    }
  }
}
