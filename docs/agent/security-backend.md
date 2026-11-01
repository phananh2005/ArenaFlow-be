# Security Convention — ArenaFlow-be

> Quy ước bảo mật cho backend. Agent `solution-architect` và `developer` MUST tuân thủ.

## 1. Authentication & Authorization

### 1.1. Authentication Flow

**JWT-based authentication:**
```
1. User login → POST /api/v1/auth/login
2. Server xác thực → trả JWT token
3. Client lưu token (localStorage/sessionStorage)
4. Request tiếp theo → Header: Authorization: Bearer <token>
5. Server verify token → cho phép/từ chối
```

### 1.2. JWT Token Structure

```json
{
  "sub": "user-id",
  "email": "user@example.com",
  "roles": ["USER"],
  "iat": 1730444243,
  "exp": 1730530643
}
```

**Token expiration:**
- Access token: 24 giờ
- Refresh token: 7 ngày

### 1.3. Authorization Rules

| Endpoint | USER | ADMIN | Public |
|---|---|---|---|
| `GET /api/v1/arenas` | ✅ | ✅ | ✅ |
| `GET /api/v1/arenas/{id}` | ✅ | ✅ | ✅ |
| `POST /api/v1/arenas` | ❌ | ✅ | ❌ |
| `PUT /api/v1/arenas/{id}` | ❌ | ✅ | ❌ |
| `DELETE /api/v1/arenas/{id}` | ❌ | ✅ | ❌ |
| `GET /api/v1/bookings` | ✅ (own) | ✅ (all) | ❌ |
| `POST /api/v1/bookings` | ✅ | ✅ | ❌ |
| `PATCH /api/v1/bookings/{id}` | ✅ (own) | ✅ (all) | ❌ |
| `DELETE /api/v1/bookings/{id}` | ✅ (own, pending) | ✅ (all) | ❌ |

**Roles:**
- `USER` — Người dùng thường
- `ADMIN` — Quản trị viên

---

## 2. Spring Security Configuration

### 2.1. Security Config

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/arenas/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

### 2.2. Method Security

```java
@PreAuthorize("hasRole('ADMIN')")
public void deleteArena(Long id) { ... }

@PreAuthorize("hasRole('USER') and #userId == authentication.principal.id")
public List<BookingResponse> getUserBookings(Long userId) { ... }

@PreAuthorize("hasRole('ADMIN') or (#bookingId == authentication.principal.id)")
public void cancelBooking(Long bookingId) { ... }
```

---

## 3. Password Security

### 3.1. Password Requirements

- **Độ dài tối thiểu:** 8 ký tự
- **Chứa:** chữ thường, chữ hoa, số, ký tự đặc biệt
- **Không chứa:** tên user, email, thông tin cá nhân

### 3.2. Password Hashing

**BCrypt (strength = 10):**
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
}

// Usage
String hashedPassword = passwordEncoder.encode(rawPassword); // allow-secret: example only
boolean matches = passwordEncoder.matches(rawPassword, hashedPassword);
```

**NEVER:**
- Lưu plaintext password
- Log password (raw hoặc hashed)
- Trả password trong response

---

## 4. Input Validation

### 4.1. Request DTO Validation

```java
@Getter
@Setter
public class CreateBookingRequest {
    
    @NotNull(message = "Arena ID không được để trống")
    @Positive(message = "Arena ID phải là số dương")
    private Long arenaId;
    
    @NotNull(message = "Ngày chơi không được để trống")
    @Future(message = "Ngày chơi phải là tương lai")
    private LocalDate playDate;
    
    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại không hợp lệ")
    private String phoneNumber;
    
    @Email(message = "Email không hợp lệ")
    private String email;
}
```

### 4.2. Sanitization

**Chống XSS:**
```java
public String sanitizeInput(String input) {
    if (input == null) return null;
    return input
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#x27;");
}
```

**Chống SQL Injection:**
- Dùng JPA/Hibernate (parameterized queries tự động)
- KHÔNG nối string để build query

❌ **BAD:**
```java
String query = "SELECT * FROM users WHERE email = '" + email + "'";
```

✅ **GOOD:**
```java
@Query("SELECT u FROM User u WHERE u.email = :email")
User findByEmail(@Param("email") String email);
```

---

## 5. Data Protection

### 5.1. Sensitive Data

**NEVER expose:**
- Password (raw hoặc hashed)
- JWT secret key
- API keys
- Database credentials
- PII (Personal Identifiable Information) không cần thiết

### 5.2. Response DTO

❌ **BAD:**
```java
@Getter
@Setter
public class UserResponse {
    private Long id;
    private String email;
    private String password; // ❌ Lộ password
}
```

✅ **GOOD:**
```java
@Getter
@Setter
public class UserResponse {
    private Long id;
    private String email;
    private String fullName;
    // Không có password
}
```

### 5.3. Logging

❌ **NEVER log:**
```java
log.info("User login: email={}, password={}", email, password); // ❌
log.info("JWT token: {}", token); // ❌
log.info("Credit card: {}", cardNumber); // ❌
```

✅ **DO log:**
```java
log.info("User login attempt: email={}", email); // ✅
log.info("User authenticated: userId={}", userId); // ✅
```

---

## 6. CORS Configuration

```java
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",  // Frontend dev
            "https://arenaflow.com"   // Frontend prod
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

**NEVER:**
- `allowedOrigins("*")` khi `allowCredentials(true)`
- Cho phép origin không tin cậy

---

## 7. Rate Limiting

**Chống brute-force attack:**

```java
// Login endpoint: max 5 attempts / 15 phút
@RateLimiter(name = "loginRateLimit", fallbackMethod = "loginFallback")
@PostMapping("/api/v1/auth/login")
public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
    // ...
}

public ResponseEntity<LoginResponse> loginFallback(LoginRequest request, Throwable t) {
    throw new TooManyRequestsException("Quá nhiều lần thử đăng nhập. Vui lòng thử lại sau 15 phút.");
}
```

**Config (application.yml):**
```yaml
resilience4j:
  ratelimiter:
    instances:
      loginRateLimit:
        limit-for-period: 5
        limit-refresh-period: 15m
        timeout-duration: 0
```

---

## 8. API Security Headers

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> 
                csp.policyDirectives("default-src 'self'")
            )
            .frameOptions(frame -> frame.deny())
            .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
            .contentTypeOptions(Customizer.withDefaults())
        );
    return http.build();
}
```

**Headers:**
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
- `Content-Security-Policy: default-src 'self'`
- `Strict-Transport-Security: max-age=31536000; includeSubDomains` (production HTTPS)

---

## 9. Error Handling (Security)

❌ **BAD:**
```json
{
  "error": "SQL error: Duplicate entry 'user@example.com' for key 'users.email'"
}
```
→ Lộ cấu trúc DB

✅ **GOOD:**
```json
{
  "error": {
    "code": "USER_002",
    "message": "Email đã tồn tại",
    "timestamp": "2026-11-01T07:00:00Z"
  }
}
```

**Quy tắc:**
- Không lộ stack trace ra ngoài (chỉ log internal)
- Không lộ cấu trúc DB, query, internal path
- Error message chung chung, error code cụ thể

---

## 10. HTTPS & TLS

**Production:**
- MUST dùng HTTPS
- TLS 1.2+ (không dùng TLS 1.0, 1.1)
- Redirect HTTP → HTTPS

**application.yml (production):**
```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEY_STORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: arenaflow
```

---

## 11. Audit Logging

**Log security events:**
- Login success/fail
- Logout
- Permission denied (403)
- Password change
- Admin actions (create/delete user, approve booking)

```java
@Aspect
@Component
@Slf4j
public class SecurityAuditAspect {
    
    @AfterReturning("@annotation(PreAuthorize)")
    public void logSecurityAction(JoinPoint joinPoint) {
        String user = SecurityContextHolder.getContext().getAuthentication().getName();
        String action = joinPoint.getSignature().getName();
        log.info("Security audit: user={}, action={}", user, action);
    }
}
```

---

## 12. Secrets Management

**NEVER commit:**
- `application.properties` với secrets thật
- `.env` với credentials
- Private keys

**DO:**
- Dùng environment variables: `${DB_PASSWORD}`
- Dùng secrets manager (AWS Secrets Manager, HashiCorp Vault)
- `.gitignore` cho `.env`, `application-local.properties`

**application.properties:**
```properties
# ✅ GOOD
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
jwt.secret=${JWT_SECRET}

# ❌ BAD
# spring.datasource.password=mySecretPassword123 # allow-secret
# jwt.secret=hardcodedSecretKey # allow-secret
```

---

## Checklist tuân thủ

- [ ] Authentication dùng JWT, stateless
- [ ] Authorization theo roles (USER, ADMIN)
- [ ] Password dùng BCrypt (strength ≥ 10)
- [ ] Request DTO có validation (`@NotBlank`, `@Pattern`, `@Email`, ...)
- [ ] Không lộ sensitive data trong response/log
- [ ] CORS chỉ cho phép trusted origins
- [ ] Rate limiting cho login endpoint
- [ ] Security headers đầy đủ
- [ ] Error message không lộ internal info
- [ ] Production dùng HTTPS
- [ ] Secrets dùng environment variables
- [ ] Audit log cho security events
