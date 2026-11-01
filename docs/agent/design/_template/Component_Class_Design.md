# Component & Class Design — [TÊN CHỨC NĂNG]

| Mục | Giá trị |
|---|---|
| Mã chức năng | `[MÃ]` |
| Phạm vi tầng | Backend (Spring Boot REST API) |
| Repo | ArenaFlow-be |
| Quy ước áp dụng | `[cần xác nhận: convention backend]` |
| Tác giả | [Agent/User] |
| Ngày | [YYYY-MM-DD] |
| Phiên bản | v1.0 (baseline) |

## 1. Tổng quan kiến trúc

[Mô tả tổng quan cấu trúc module/package]

## 2. Package Structure

```
com.arenaflow
├── controller
│   └── [TênController].java
├── service
│   ├── [TênService].java (interface)
│   └── impl
│       └── [TênServiceImpl].java
├── repository
│   └── [TênRepository].java (extends JpaRepository)
├── entity
│   └── [TênEntity].java
└── dto
    ├── request
    │   └── [TênRequest].java
    └── response
        └── [TênResponse].java
```

## 3. Controller Layer

### 3.1. [TênController]

**Responsibility:** [Xử lý HTTP request cho chức năng X]

**Endpoints:**
- `[method] /api/v1/[path]` → `[methodName]()`

**Dependencies:**
- `[TênService]`

---

## 4. Service Layer

### 4.1. [TênService] (interface)

**Methods:**
```java
[ReturnType] [methodName]([ParamType] param);
```

### 4.2. [TênServiceImpl]

**Responsibility:** [Business logic]

**Dependencies:**
- `[TênRepository]`
- `[Dependency khác]`

**Transaction:** `@Transactional` trên method ghi

---

## 5. Repository Layer

### 5.1. [TênRepository]

```java
public interface [TênRepository] extends JpaRepository<[Entity], [IdType]> {
    Optional<[Entity]> findBy[Field]([FieldType] field);
}
```

**Custom queries:** [nếu có]

---

## 6. Entity

### 6.1. [TênEntity]

**Table:** `[tên_bảng]`

| Field | Type | Column | Constraint | Mô tả |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK, Auto | [Mô tả] |
| `field1` | `String` | `field1` | NOT NULL | [Mô tả] |

**Relationships:**
- `@ManyToOne` / `@OneToMany` [nếu có]

**Indexes:**
- `[field]` [nếu cần]

---

## 7. DTO

### 7.1. Request DTOs

**[TênRequest]:**
- [Liệt kê fields và validation annotations]

### 7.2. Response DTOs

**[TênResponse]:**
- [Liệt kê fields]

**Mapper:** [TênMapper] (MapStruct hoặc manual)

---

## 8. Dependencies

- Spring Boot Starter Web
- Spring Boot Starter Data JPA
- Spring Boot Starter Security
- [Dependency khác nếu cần thêm]

---

## 9. Danh sách [cần xác nhận]

1. [Điểm chưa rõ 1]
2. [Điểm chưa rõ 2]
