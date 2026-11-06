# Backend Convention — ArenaFlow-be

> Quy ước code backend (Spring Boot). Agent `solution-architect` và `developer` MUST tuân thủ.

## 1. Package Structure

Maven multi-module (ADR-0006), base package `com.phananh.arenaflow`. Chiều phụ thuộc `api → application → domain → common`. Maven chỉ chặn **phụ thuộc ngược** (vd `domain` → `application`); module trên vẫn thấy module dưới qua phụ thuộc bắc cầu, nên **controller không gọi thẳng repository** vẫn là quy tắc giữ bằng review (§1 Quy tắc).

```
ArenaFlow-be/
├── pom.xml                  # packaging=pom, parent spring-boot-starter-parent, khai báo 4 module
├── common/                  # com.phananh.arenaflow.common
│   ├── enums/               # Enum dùng chung (SkillLevel…)
│   ├── exception/           # ErrorCode, BusinessException, ErrorResponse… (chỉ phụ thuộc spring-web: HttpStatus)
│   └── DeletedUserMasking   # Tiện ích ở root package common
├── domain/                  # com.phananh.arenaflow.domain
│   ├── entity/<nhóm>/       # JPA Entities (@Entity) theo nhóm: user/, team/, match/… + converter/
│   ├── repository/          # JPA Repositories (extends JpaRepository)
│   └── projection/          # Interface projection cho native query
├── application/             # com.phananh.arenaflow.application
│   ├── service/             # Business logic interfaces
│   │   └── impl/            # Service implementations
│   ├── dto/
│   │   ├── request/         # Request DTOs
│   │   └── response/        # Response DTOs (ApiResponse, PageResponse…)
│   ├── mapper/              # Entity ↔ DTO mappers (MapStruct)
│   ├── validation/          # Custom constraint annotation + validator
│   └── support/             # Thành phần hỗ trợ service: guard, retry, phân trang
│       └── security/        # JwtTokenService, JwtProperties (service gọi trực tiếp)
└── api/                     # com.phananh.arenaflow (ArenaFlowApplication) + com.phananh.arenaflow.api
    ├── <nhóm>/              # REST Controllers (@RestController) theo nhóm: auth/, profile/, team/…
    ├── config/              # @Configuration, SecurityConfig, WebMvcConfig, GlobalExceptionHandler
    └── security/            # Filter/handler/argument resolver của Spring Security
```

Resource: `api/src/main/resources/` (`application.properties`, `db/migration/` Flyway). Module khác không có resource.

**Đặt lớp mới vào module nào:** tầng thấp nhất mà mọi nơi dùng lớp đó đều thấy được. Ví dụ service dùng → `application`, entity/projection dùng → `domain` hoặc `common`.
**Test:** cùng package với lớp được kiểm; cùng module, **trừ** test cần Spring context (`@SpringBootTest`) → đặt ở `api` (chỉ `api` có lớp `main` và migration) nhưng giữ package của lớp được kiểm (vd `api/src/test/.../domain/repository/TeamRepositoryTest`).

**Quy tắc:**
- **Controller** chỉ xử lý HTTP, gọi service, không chứa business logic
- **Service** chứa business logic, transaction management
- **Repository** chỉ truy cập DB, không chứa business logic
- **Entity** là JPA entity, ánh xạ 1-1 với bảng DB
- **DTO** cho request/response, không expose entity ra ngoài

---

## 2. Naming Convention

### 2.1. Class

| Loại | Pattern | Ví dụ |
|---|---|---|
| Entity | `<TênEntity>` | `Arena`, `Booking`, `User` |
| Repository | `<TênEntity>Repository` | `ArenaRepository`, `BookingRepository` |
| Service Interface | `<TênEntity>Service` | `ArenaService`, `BookingService` |
| Service Impl | `<TênEntity>ServiceImpl` | `ArenaServiceImpl` |
| Controller | `<TênEntity>Controller` | `ArenaController`, `BookingController` |
| Request DTO | `<Action><TênEntity>Request` | `CreateArenaRequest`, `UpdateBookingRequest` |
| Response DTO | `<TênEntity>Response` | `ArenaResponse`, `BookingResponse` |
| Exception | `<Mô tả>Exception` | `ArenaNotFoundException`, `SlotAlreadyBookedException` |

### 2.2. Method

- **camelCase**: `findArenaById()`, `createBooking()`, `updateBookingStatus()`
- **Controller method** = HTTP action: `getArenas()`, `createArena()`, `updateArena()`, `deleteArena()`
- **Service method** = business action: `findAllArenas()`, `createArenaWithValidation()`, `cancelBooking()`
- **Repository method** = query: `findByStatus()`, `findByUserIdAndStatus()`, `existsByArenaIdAndDate()`

### 2.3. Variables & Fields

- **camelCase**: `arenaId`, `pricePerHour`, `bookingList`
- **Constants**: `UPPER_SNAKE_CASE`: `MAX_BOOKING_PER_USER`, `DEFAULT_PAGE_SIZE`
- **Boolean**: prefix `is`, `has`, `can`: `isActive`, `hasDiscount`, `canBook`

### 2.4. Comment & Javadoc

- Javadoc **tiếng Việt**, giải thích **tại sao / hợp đồng**, không lặp lại code.
- **Bắt buộc** cho method `public`/`protected` có hợp đồng hoặc ngữ nghĩa không hiển nhiên từ tên: service (quyền, thứ tự guard, mã lỗi, retry/transaction, số truy vấn khi đáng kể), guard, mapper có xử lý đặc biệt (che tên user đã xoá…), repository có `@Query` (native, bulk `@Modifying`, `@Lock`, projection), component hạ tầng (retrier, JWT, security handler).
- **Bắt buộc** cho method `private` có logic không hiển nhiên (điều kiện nghiệp vụ, thứ tự kiểm, xử lý race).
- **Bỏ qua** khi tên đã đủ nghĩa: derived query (`findByIdAndDeletedAtIsNull`), getter/setter/accessor, DTO record, projection, controller handler chỉ uỷ quyền service.
- **NEVER** trỏ sang bất kỳ tài liệu nào trong comment: ADR (`ADR-0007`), đường dẫn/mục tài liệu thiết kế/spec (`USER-002 API_Spec §2.4`), mã neo nghiệp vụ/thiết kế (`BR-T12`, `W6`, `L4`, `race M1`). Viết mô tả tự đủ nghĩa thay thế (vd `(W6)` → "6 truy vấn") — người đọc (dev/AI) không phải mở tài liệu khác. Mã lỗi (`TEAM_014`) được dùng vì là enum trong code.

---

## 3. Controller Layer

### 3.1. Annotations

```java
@RestController
@RequestMapping("/api/v1/arenas")
@RequiredArgsConstructor // Lombok
@Validated
public class ArenaController {
    private final ArenaService arenaService;
}
```

### 3.2. Method Structure

```java
@GetMapping("/{id}")
public ResponseEntity<ArenaResponse> getArena(@PathVariable UUID id) {
    ArenaResponse response = arenaService.findById(id);
    return ResponseEntity.ok(response);
}

@PostMapping
public ResponseEntity<ArenaResponse> createArena(
    @Valid @RequestBody CreateArenaRequest request
) {
    ArenaResponse response = arenaService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}

@PatchMapping("/{id}")
public ResponseEntity<ArenaResponse> updateArena(
    @PathVariable UUID id,
    @Valid @RequestBody UpdateArenaRequest request
) {
    ArenaResponse response = arenaService.update(id, request);
    return ResponseEntity.ok(response);
}

@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteArena(@PathVariable UUID id) {
    arenaService.delete(id);
    return ResponseEntity.noContent().build();
}
```

**Quy tắc:**
- Return `ResponseEntity<T>`
- Mã thành công theo `api-convention.md` §5: `201` → `ResponseEntity.status(HttpStatus.CREATED).body(...)` (không dùng `created(uri)` — không trả `Location`); `200` → `ok(...)`; `204` → `noContent().build()`
- Request body dùng DTO, có `@Valid`
- Path variable/Request param có validation annotation
- Không catch exception trong controller (để global handler xử lý)

---

## 4. Service Layer

### 4.1. Interface

```java
public interface ArenaService {
    ArenaResponse findById(Long id);
    List<ArenaResponse> findAll(Pageable pageable);
    ArenaResponse create(CreateArenaRequest request);
    ArenaResponse update(Long id, UpdateArenaRequest request);
    void delete(Long id);
}
```

### 4.2. Implementation

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // Default read-only
public class ArenaServiceImpl implements ArenaService {
    private final ArenaRepository arenaRepository;
    private final ArenaMapper arenaMapper;

    @Override
    public ArenaResponse findById(Long id) {
        Arena arena = arenaRepository.findById(id)
            .orElseThrow(() -> new ArenaNotFoundException(id));
        return arenaMapper.toResponse(arena);
    }

    @Override
    @Transactional // Ghi → bỏ readOnly
    public ArenaResponse create(CreateArenaRequest request) {
        Arena arena = arenaMapper.toEntity(request);
        Arena saved = arenaRepository.save(arena);
        return arenaMapper.toResponse(saved);
    }
}
```

**Quy tắc:**
- `@Transactional(readOnly = true)` ở class level
- Method ghi: `@Transactional` (bỏ readOnly)
- Business logic validation trước khi save
- Throw custom exception thay vì return null

---

## 5. Repository Layer

```java
public interface ArenaRepository extends JpaRepository<Arena, Long> {
    List<Arena> findByStatusAndLocationContaining(
        ArenaStatus status, 
        String location
    );
    
    boolean existsByArenaNameAndLocation(String name, String location);
    
    @Query("SELECT a FROM Arena a WHERE a.pricePerHour <= :maxPrice")
    List<Arena> findArenasUnderPrice(@Param("maxPrice") BigDecimal maxPrice);
}
```

**Quy tắc:**
- Extend `JpaRepository<Entity, ID>`
- Method query theo Spring Data JPA naming: `findBy`, `existsBy`, `countBy`
- Complex query dùng `@Query`
- Không có business logic trong repository

---

## 6. Entity

```java
@Entity
@Table(name = "arenas", indexes = {
    @Index(name = "idx_arena_location", columnList = "location"),
    @Index(name = "idx_arena_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Arena extends BaseEntity {
    
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)  // mọi id UUID dùng v7
    private UUID id;
    
    @Column(name = "arena_name", nullable = false, length = 100)
    private String arenaName;
    
    @Column(name = "location", nullable = false, length = 200)
    private String location;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "arena_type", nullable = false)
    private ArenaType arenaType;
    
    @Column(name = "price_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerHour;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ArenaStatus status;
    
    @OneToMany(mappedBy = "arena", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TimeSlot> timeSlots = new ArrayList<>();
}
```

**BaseEntity:**
```java
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
```

**Quy tắc:**
- `@Entity` + `@Table(name = "...")`
- Lombok: `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- Column name = **snake_case**: `arena_name`, `price_per_hour`
- Enum dùng `@Enumerated(EnumType.STRING)`
- Timestamp: `LocalDateTime`, có `createdAt`, `updatedAt`, `deletedAt`
- Relationship: `@OneToMany`, `@ManyToOne`, `mappedBy`, `cascade`

---

## 7. DTO

### Request DTO

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateArenaRequest {
    
    @NotBlank(message = "Tên sân không được để trống")
    @Size(max = 100, message = "Tên sân tối đa 100 ký tự")
    private String arenaName;
    
    @NotBlank(message = "Địa chỉ không được để trống")
    @Size(max = 200, message = "Địa chỉ tối đa 200 ký tự")
    private String location;
    
    @NotNull(message = "Loại sân không được để trống")
    private ArenaType arenaType;
    
    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá phải lớn hơn 0")
    private BigDecimal pricePerHour;
}
```

### Response DTO

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArenaResponse {
    private Long id;
    private String arenaName;
    private String location;
    private ArenaType arenaType;
    private BigDecimal pricePerHour;
    private ArenaStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Quy tắc:**
- Field name = **camelCase** (trùng API convention)
- Request: có validation annotations (`@NotBlank`, `@NotNull`, `@Size`, ...)
- Response: không có validation, chỉ có data
- KHÔNG expose entity ra ngoài

---

## 8. Exception Handling

### Custom Exception

```java
public class ArenaNotFoundException extends RuntimeException {
    public ArenaNotFoundException(Long id) {
        super("Arena with ID " + id + " not found");
    }
}
```

### Global Exception Handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ArenaNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleArenaNotFound(
        ArenaNotFoundException ex,
        WebRequest request
    ) {
        ErrorResponse error = ErrorResponse.builder()
            .code("ARENA_001")
            .message("Sân không tồn tại")
            .details(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(((ServletWebRequest) request).getRequest().getRequestURI())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException ex,
        WebRequest request
    ) {
        List<ValidationError> validationErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(e -> new ValidationError(e.getField(), e.getDefaultMessage()))
            .collect(Collectors.toList());
            
        ErrorResponse error = ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message("Dữ liệu không hợp lệ")
            .validationErrors(validationErrors)
            .timestamp(LocalDateTime.now())
            .path(((ServletWebRequest) request).getRequest().getRequestURI())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
```

---

## 9. Transaction Management

- **Service method default:** `@Transactional(readOnly = true)` ở class level
- **Method ghi (create/update/delete):** `@Transactional` (bỏ readOnly)
- **Isolation level:** default = `READ_COMMITTED`
- **Propagation:** default = `REQUIRED`
- **Ghi entity:** mặc định để Hibernate ghi lúc commit; `saveAndFlush` / `flush()` chỉ khi có lý do và kèm comment — xem `non-functional-standards.md` §4.1

```java
@Transactional
public BookingResponse createBooking(CreateBookingRequest request) {
    // Tất cả trong 1 transaction
    // Rollback nếu có exception
}
```

**Ngoại lệ — Method có optimistic lock retry:**
- **Không** annotate `@Transactional` ở method level
- Transaction do `OptimisticLockRetrier` quản lý — mỗi lần thử là 1 transaction mới
- Lý do: retry phải nằm **ngoài** transaction; nếu `@Transactional` bọc retry → toàn bộ lần thử dùng chung persistence context đã rollback-only
- Xem ADR-0003 cho chi tiết

```java
// Method có retry - KHÔNG có @Transactional
public TeamResponse updateTeam(UUID teamId, UUID userId, UpdateTeamRequest request) {
    return optimisticLockRetrier.execute(() -> {
        // Transaction mới cho mỗi lần thử
        // ...
    });
}
```

---

## 10. Soft Delete

```java
// Entity
@SQLDelete(sql = "UPDATE arenas SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class Arena extends BaseEntity {
    // ...
}

// Service
@Transactional
public void delete(Long id) {
    Arena arena = findEntityById(id);
    arenaRepository.delete(arena); // Gọi soft delete SQL
}
```

---

## 11. Logging

```java
@Slf4j
@Service
public class ArenaServiceImpl implements ArenaService {
    
    public ArenaResponse create(CreateArenaRequest request) {
        log.info("Creating arena: {}", request.getArenaName());
        // ...
        log.info("Arena created with ID: {}", saved.getId());
        return response;
    }
}
```

**Quy tắc:**
- Dùng `@Slf4j` (Lombok)
- Log level: `DEBUG` (dev), `INFO` (important actions), `WARN` (recoverable), `ERROR` (exception)
- KHÔNG log sensitive data (password, token, credit card)

---

## 12. Testing

### Unit Test (Service)

```java
@ExtendWith(MockitoExtension.class)
class ArenaServiceImplTest {
    
    @Mock
    private ArenaRepository arenaRepository;
    
    @Mock
    private ArenaMapper arenaMapper;
    
    @InjectMocks
    private ArenaServiceImpl arenaService;
    
    @Test
    void shouldFindArenaById() {
        // Given
        Long id = 1L;
        Arena arena = Arena.builder().id(id).build();
        when(arenaRepository.findById(id)).thenReturn(Optional.of(arena));
        
        // When
        ArenaResponse response = arenaService.findById(id);
        
        // Then
        assertNotNull(response);
        verify(arenaRepository).findById(id);
    }
}
```

### Integration Test (Repository)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE) // Dùng PostgreSQL thật
class ArenaRepositoryTest {
    
    @Autowired
    private ArenaRepository arenaRepository;
    
    @Test
    void shouldFindByStatus() {
        // Given
        Arena arena = Arena.builder()
            .arenaName("Test Arena")
            .status(ArenaStatus.ACTIVE)
            .build();
        arenaRepository.save(arena);
        
        // When
        List<Arena> result = arenaRepository.findByStatus(ArenaStatus.ACTIVE);
        
        // Then
        assertEquals(1, result.size());
    }
}
```

---

## Checklist tuân thủ

- [ ] Đặt đúng module & package (`api/<nhóm>` controller, `application/service/impl`, `application/dto/request|response`, `domain/repository`, `domain/entity`); không import ngược tầng
- [ ] Class naming đúng pattern
- [ ] Javadoc tiếng Việt theo §2.4 (có cho method có hợp đồng không hiển nhiên; không cho method tự mô tả; không trỏ tài liệu/ADR)
- [ ] Controller chỉ xử lý HTTP, không có business logic
- [ ] Service có `@Transactional`, method ghi bỏ `readOnly`
- [ ] Entity có `@Entity`, column name snake_case, có BaseEntity
- [ ] DTO không expose entity, có validation
- [ ] Exception có custom exception + global handler
- [ ] Soft delete dùng `deletedAt`
- [ ] Logging dùng `@Slf4j`, không log sensitive data
