# Role Session Workspaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the top-level station/owner/admin tab shell with a real login-driven role workspace foundation that supports owner and admin accounts, per-tab session persistence, and role-specific entry pages.

**Architecture:** Add lightweight backend auth/session services on top of existing `UserAccount` and `Vehicle` entities, then add frontend session utilities and route guards backed by `sessionStorage`. Keep the current Spring Boot + Vue stack and existing owner/admin panels, but change the application entry so users reach those panels through login rather than visible role tabs.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring MVC, Spring Data JPA, H2, Vue 3, Element Plus, Axios, Node test runner.

---

## Scope

This plan implements phase 1 of `docs/superpowers/specs/2026-06-15-role-session-dispatch-redesign.md`.

Included:

- Backend account roles.
- Backend login/session endpoints.
- Frontend auth API and session storage.
- Login view.
- Route guard and role-specific shell.
- Existing owner/admin panels reachable only through login.

Not included in this phase:

- Admin revenue dashboard.
- Single-point dispatch.
- Fault-aware dispatch rebalance.
- Charging-time target amount modification.
- Full backend authorization enforcement on every existing operation.

Those belong in follow-up plans after the role/session foundation is working.

## File Structure

Backend files:

- Create `backend/src/main/java/com/bupt/charging/domain/AccountRole.java` for `OWNER` and `ADMIN`.
- Create `backend/src/main/java/com/bupt/charging/domain/LoginSession.java` for server-side demo sessions.
- Create `backend/src/main/java/com/bupt/charging/repository/LoginSessionRepository.java` for token lookup.
- Create `backend/src/main/java/com/bupt/charging/dto/AuthDtos.java` for login responses and current user payloads.
- Create `backend/src/main/java/com/bupt/charging/service/AuthService.java` for credential checks, default admin provisioning, session creation, and session lookup.
- Create `backend/src/main/java/com/bupt/charging/controller/AuthController.java` for `/api/auth/*`.
- Modify `backend/src/main/java/com/bupt/charging/domain/UserAccount.java` to store role.
- Modify `backend/src/main/java/com/bupt/charging/repository/UserAccountRepository.java` to query admins by name and role.
- Modify `backend/src/main/java/com/bupt/charging/service/AccountService.java` so owner accounts are explicitly created with role `OWNER` and password hashing is reusable.
- Modify `backend/src/main/java/com/bupt/charging/dto/AccountDtos.java` so account responses include role.
- Modify `backend/src/test/java/com/bupt/charging/controller/RestApiSmokeTest.java` for auth smoke coverage.
- Create `backend/src/test/java/com/bupt/charging/service/AuthServiceTest.java` for service-level auth rules.

Frontend files:

- Create `frontend/src/utils/authSession.js` for `sessionStorage` session helpers.
- Create `frontend/src/utils/authSession.test.js` for per-tab storage behavior and route decisions.
- Modify `frontend/src/utils/hashRoute.js` to add `LOGIN` and normalize route labels.
- Modify `frontend/src/utils/hashRoute.test.js` for the new login route.
- Modify `frontend/src/api/client.js` to attach the active session token.
- Modify `frontend/src/api/chargingApi.js` to add auth calls.
- Create `frontend/src/views/LoginView.vue` for unified login.
- Create `frontend/src/components/shell/RoleWorkspaceShell.vue` for post-login layout and logout.
- Modify `frontend/src/App.vue` to route by auth state and role.
- Modify `frontend/src/views/OwnerPanel.vue` to accept an authenticated owner session as initial identity.

Verification commands:

- Backend targeted: `.\mvnw.cmd -Dtest=AuthServiceTest,RestApiSmokeTest test`
- Backend full: `.\mvnw.cmd test`
- Frontend tests: `npm test`
- Frontend build: `npm run build`

## Task 1: Backend Account Roles

**Files:**

- Create: `backend/src/main/java/com/bupt/charging/domain/AccountRole.java`
- Modify: `backend/src/main/java/com/bupt/charging/domain/UserAccount.java`
- Modify: `backend/src/main/java/com/bupt/charging/repository/UserAccountRepository.java`
- Modify: `backend/src/main/java/com/bupt/charging/service/AccountService.java`
- Modify: `backend/src/main/java/com/bupt/charging/dto/AccountDtos.java`
- Test: `backend/src/test/java/com/bupt/charging/service/AuthServiceTest.java`

- [ ] **Step 1: Write the failing account role test**

Create `backend/src/test/java/com/bupt/charging/service/AuthServiceTest.java` with this initial test:

```java
package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bupt.charging.domain.AccountRole;
import com.bupt.charging.dto.AccountDtos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-service-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthServiceTest {
    @Autowired
    private AccountService accountService;

    @Test
    void createdVehicleAccountIsOwnerRole() {
        AccountDtos.AccountResponse response = accountService.createNewAccount(
                "CAR-AUTH-1",
                "Auth Owner",
                80.0
        );

        assertEquals(AccountRole.OWNER, response.role());
        assertEquals("CAR-AUTH-1", response.carId());
        assertEquals("Auth Owner", response.userName());
    }
}
```

- [ ] **Step 2: Run the targeted test and verify it fails**

Run:

```powershell
cd D:\softe\backend
.\mvnw.cmd -Dtest=AuthServiceTest test
```

Expected: compilation fails because `AccountRole` and `AccountResponse.role()` do not exist.

- [ ] **Step 3: Add the role enum**

Create `backend/src/main/java/com/bupt/charging/domain/AccountRole.java`:

```java
package com.bupt.charging.domain;

public enum AccountRole {
    OWNER,
    ADMIN
}
```

- [ ] **Step 4: Add role to `UserAccount`**

Modify `backend/src/main/java/com/bupt/charging/domain/UserAccount.java`:

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private AccountRole role;
```

Add `jakarta.persistence.EnumType` and `jakarta.persistence.Enumerated` imports.

Change the existing constructor to delegate:

```java
public UserAccount(String userName, LocalDateTime createdAt) {
    this(userName, AccountRole.OWNER, createdAt);
}

public UserAccount(String userName, AccountRole role, LocalDateTime createdAt) {
    this.userName = userName;
    this.role = role;
    this.status = "PENDING_PASSWORD";
    this.createdAt = createdAt;
}
```

Add:

```java
public AccountRole getRole() {
    return role;
}
```

- [ ] **Step 5: Add account repository queries**

Modify `backend/src/main/java/com/bupt/charging/repository/UserAccountRepository.java`:

```java
package com.bupt.charging.repository;

import com.bupt.charging.domain.AccountRole;
import com.bupt.charging.domain.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findFirstByUserNameAndRole(String userName, AccountRole role);
}
```

- [ ] **Step 6: Return role in account DTOs**

Modify `backend/src/main/java/com/bupt/charging/dto/AccountDtos.java`:

```java
import com.bupt.charging.domain.AccountRole;
```

Change `AccountResponse` to:

```java
public record AccountResponse(String carId, String userName, double carCapacity, String status, AccountRole role) {
}
```

- [ ] **Step 7: Update account service responses**

Modify each `new AccountDtos.AccountResponse(...)` in `backend/src/main/java/com/bupt/charging/service/AccountService.java` to pass `account.getRole()` as the final argument.

In `createNewAccount`, keep owner creation explicit:

```java
UserAccount account = userAccountRepository.save(new UserAccount(
        userName,
        com.bupt.charging.domain.AccountRole.OWNER,
        LocalDateTime.now()
));
```

- [ ] **Step 8: Run the targeted test and verify it passes**

Run:

```powershell
cd D:\softe\backend
.\mvnw.cmd -Dtest=AuthServiceTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit task 1**

```powershell
cd D:\softe
git add backend/src/main/java/com/bupt/charging/domain/AccountRole.java `
        backend/src/main/java/com/bupt/charging/domain/UserAccount.java `
        backend/src/main/java/com/bupt/charging/repository/UserAccountRepository.java `
        backend/src/main/java/com/bupt/charging/service/AccountService.java `
        backend/src/main/java/com/bupt/charging/dto/AccountDtos.java `
        backend/src/test/java/com/bupt/charging/service/AuthServiceTest.java
git commit -m "feat: add account roles"
```

## Task 2: Backend Auth Sessions

**Files:**

- Create: `backend/src/main/java/com/bupt/charging/domain/LoginSession.java`
- Create: `backend/src/main/java/com/bupt/charging/repository/LoginSessionRepository.java`
- Create: `backend/src/main/java/com/bupt/charging/dto/AuthDtos.java`
- Create: `backend/src/main/java/com/bupt/charging/service/AuthService.java`
- Create: `backend/src/main/java/com/bupt/charging/controller/AuthController.java`
- Modify: `backend/src/test/java/com/bupt/charging/service/AuthServiceTest.java`
- Modify: `backend/src/test/java/com/bupt/charging/controller/RestApiSmokeTest.java`

- [ ] **Step 1: Add failing auth service tests**

Append these tests to `AuthServiceTest`:

```java
@Autowired
private AuthService authService;

@Test
void ownerCanLoginWithCarIdAndPassword() {
    accountService.createNewAccount("CAR-AUTH-2", "Owner Two", 90.0);
    accountService.setPassword("CAR-AUTH-2", "secret");

    AuthDtos.LoginResponse response = authService.login("CAR-AUTH-2", "secret");

    assertEquals(AccountRole.OWNER, response.role());
    assertEquals("CAR-AUTH-2", response.carId());
    assertEquals("Owner Two", response.userName());
}

@Test
void adminCanLoginWithSeededDefaultCredentials() {
    AuthDtos.LoginResponse response = authService.login("admin", "123456");

    assertEquals(AccountRole.ADMIN, response.role());
    assertEquals("admin", response.userName());
}
```

Add import:

```java
import com.bupt.charging.dto.AuthDtos;
```

- [ ] **Step 2: Run the targeted test and verify it fails**

Run:

```powershell
cd D:\softe\backend
.\mvnw.cmd -Dtest=AuthServiceTest test
```

Expected: compilation fails because `AuthService`, `AuthDtos`, and login methods do not exist.

- [ ] **Step 3: Create login session entity**

Create `backend/src/main/java/com/bupt/charging/domain/LoginSession.java`:

```java
package com.bupt.charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class LoginSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountRole role;

    private String carId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    protected LoginSession() {
    }

    public LoginSession(String token, Long accountId, AccountRole role, String carId,
                        LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.token = token;
        this.accountId = accountId;
        this.role = role;
        this.carId = carId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public Long getAccountId() {
        return accountId;
    }

    public AccountRole getRole() {
        return role;
    }

    public String getCarId() {
        return carId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
```

- [ ] **Step 4: Create login session repository**

Create `backend/src/main/java/com/bupt/charging/repository/LoginSessionRepository.java`:

```java
package com.bupt.charging.repository;

import com.bupt.charging.domain.LoginSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
    Optional<LoginSession> findByToken(String token);

    void deleteByToken(String token);
}
```

- [ ] **Step 5: Create auth DTOs**

Create `backend/src/main/java/com/bupt/charging/dto/AuthDtos.java`:

```java
package com.bupt.charging.dto;

import com.bupt.charging.domain.AccountRole;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String loginName, @NotBlank String password) {
    }

    public record LoginResponse(
            String token,
            AccountRole role,
            Long accountId,
            String userName,
            String carId
    ) {
    }

    public record CurrentUserResponse(
            AccountRole role,
            Long accountId,
            String userName,
            String carId
    ) {
    }
}
```

- [ ] **Step 6: Implement auth service**

Create `backend/src/main/java/com/bupt/charging/service/AuthService.java`:

```java
package com.bupt.charging.service;

import com.bupt.charging.domain.AccountRole;
import com.bupt.charging.domain.LoginSession;
import com.bupt.charging.domain.UserAccount;
import com.bupt.charging.domain.Vehicle;
import com.bupt.charging.dto.AuthDtos;
import com.bupt.charging.repository.LoginSessionRepository;
import com.bupt.charging.repository.UserAccountRepository;
import com.bupt.charging.repository.VehicleRepository;
import com.bupt.charging.support.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String DEFAULT_ADMIN = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "123456";

    private final UserAccountRepository userAccountRepository;
    private final VehicleRepository vehicleRepository;
    private final LoginSessionRepository loginSessionRepository;

    public AuthService(
            UserAccountRepository userAccountRepository,
            VehicleRepository vehicleRepository,
            LoginSessionRepository loginSessionRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.vehicleRepository = vehicleRepository;
        this.loginSessionRepository = loginSessionRepository;
    }

    @Transactional
    public AuthDtos.LoginResponse login(String loginName, String password) {
        ensureDefaultAdmin();
        Vehicle vehicle = vehicleRepository.findByCarId(loginName).orElse(null);
        if (vehicle != null) {
            UserAccount owner = vehicle.getOwner();
            requireActivePassword(owner, password);
            return createSession(owner, vehicle.getCarId());
        }

        UserAccount admin = userAccountRepository.findFirstByUserNameAndRole(loginName, AccountRole.ADMIN)
                .orElseThrow(() -> new BusinessException("invalid login credentials"));
        requireActivePassword(admin, password);
        return createSession(admin, null);
    }

    @Transactional(readOnly = true)
    public AuthDtos.CurrentUserResponse currentUser(String token) {
        LoginSession session = loginSessionRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException("session not found"));
        if (session.isExpired(LocalDateTime.now())) {
            throw new BusinessException("session expired");
        }
        UserAccount account = userAccountRepository.findById(session.getAccountId())
                .orElseThrow(() -> new BusinessException("account not found"));
        return new AuthDtos.CurrentUserResponse(
                session.getRole(),
                account.getId(),
                account.getUserName(),
                session.getCarId()
        );
    }

    @Transactional
    public void logout(String token) {
        loginSessionRepository.deleteByToken(token);
    }

    private AuthDtos.LoginResponse createSession(UserAccount account, String carId) {
        LocalDateTime now = LocalDateTime.now();
        LoginSession session = loginSessionRepository.save(new LoginSession(
                UUID.randomUUID().toString(),
                account.getId(),
                account.getRole(),
                carId,
                now,
                now.plusHours(8)
        ));
        return new AuthDtos.LoginResponse(
                session.getToken(),
                account.getRole(),
                account.getId(),
                account.getUserName(),
                carId
        );
    }

    private void ensureDefaultAdmin() {
        userAccountRepository.findFirstByUserNameAndRole(DEFAULT_ADMIN, AccountRole.ADMIN)
                .orElseGet(() -> {
                    UserAccount admin = new UserAccount(DEFAULT_ADMIN, AccountRole.ADMIN, LocalDateTime.now());
                    admin.setPasswordHash(hashPassword(DEFAULT_ADMIN_PASSWORD));
                    return userAccountRepository.save(admin);
                });
    }

    private void requireActivePassword(UserAccount account, String password) {
        if (!hashPassword(password).equals(account.getPasswordHash())) {
            throw new BusinessException("invalid login credentials");
        }
    }

    static String hashPassword(String password) {
        return Base64.getEncoder().encodeToString(("demo:" + password).getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 7: Reuse auth password hashing in account service**

In `AccountService.setPassword`, replace the inline Base64 expression with:

```java
String hash = AuthService.hashPassword(password);
```

Remove now-unused `java.nio.charset.StandardCharsets` and `java.util.Base64` imports.

- [ ] **Step 8: Create auth controller**

Create `backend/src/main/java/com/bupt/charging/controller/AuthController.java`:

```java
package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.AuthDtos;
import com.bupt.charging.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResult<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResult.ok(authService.login(request.loginName(), request.password()));
    }

    @GetMapping("/me")
    public ApiResult<AuthDtos.CurrentUserResponse> me(@RequestHeader("X-Session-Token") String token) {
        return ApiResult.ok(authService.currentUser(token));
    }

    @PostMapping("/logout")
    public ApiResult<Boolean> logout(@RequestHeader("X-Session-Token") String token) {
        authService.logout(token);
        return ApiResult.ok(true);
    }
}
```

- [ ] **Step 9: Add auth API smoke test**

Append to `RestApiSmokeTest`:

```java
@Test
void authLoginAndMeReturnRoleSession() throws Exception {
    ResponseEntity<String> accountResponse = restTemplate.postForEntity(
            "/api/accounts",
            Map.of("carId", "CAR-LOGIN-API", "userName", "Login API", "carCapacity", 80.0),
            String.class
    );
    assertEquals(HttpStatus.OK, accountResponse.getStatusCode());
    ResponseEntity<String> passwordResponse = restTemplate.postForEntity(
            "/api/accounts/CAR-LOGIN-API/password",
            Map.of("password", "secret"),
            String.class
    );
    assertEquals(HttpStatus.OK, passwordResponse.getStatusCode());

    ResponseEntity<String> loginResponse = restTemplate.postForEntity(
            "/api/auth/login",
            Map.of("loginName", "CAR-LOGIN-API", "password", "secret"),
            String.class
    );

    assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
    JsonNode loginData = objectMapper.readTree(loginResponse.getBody()).path("data");
    assertEquals("OWNER", loginData.path("role").asText());
    assertEquals("CAR-LOGIN-API", loginData.path("carId").asText());

    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/me"))
            .header("X-Session-Token", loginData.path("token").asText())
            .GET()
            .build();
    java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
            .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    JsonNode meData = objectMapper.readTree(response.body()).path("data");
    assertEquals("OWNER", meData.path("role").asText());
    assertEquals("CAR-LOGIN-API", meData.path("carId").asText());
}
```

- [ ] **Step 10: Run targeted backend tests**

Run:

```powershell
cd D:\softe\backend
.\mvnw.cmd -Dtest=AuthServiceTest,RestApiSmokeTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 11: Commit task 2**

```powershell
cd D:\softe
git add backend/src/main/java/com/bupt/charging/domain/LoginSession.java `
        backend/src/main/java/com/bupt/charging/repository/LoginSessionRepository.java `
        backend/src/main/java/com/bupt/charging/dto/AuthDtos.java `
        backend/src/main/java/com/bupt/charging/service/AuthService.java `
        backend/src/main/java/com/bupt/charging/controller/AuthController.java `
        backend/src/main/java/com/bupt/charging/service/AccountService.java `
        backend/src/test/java/com/bupt/charging/service/AuthServiceTest.java `
        backend/src/test/java/com/bupt/charging/controller/RestApiSmokeTest.java
git commit -m "feat: add login sessions"
```

## Task 3: Frontend Auth Session Utilities

**Files:**

- Create: `frontend/src/utils/authSession.js`
- Create: `frontend/src/utils/authSession.test.js`
- Modify: `frontend/src/utils/hashRoute.js`
- Modify: `frontend/src/utils/hashRoute.test.js`

- [ ] **Step 1: Write failing auth session tests**

Create `frontend/src/utils/authSession.test.js`:

```js
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  AUTH_SESSION_KEY,
  clearAuthSession,
  defaultRouteForSession,
  loadAuthSession,
  saveAuthSession
} from './authSession.js'

function memoryStorage() {
  const values = new Map()
  return {
    getItem: (key) => (values.has(key) ? values.get(key) : null),
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key)
  }
}

describe('auth session helpers', () => {
  it('saves and loads the active tab session', () => {
    const storage = memoryStorage()
    const session = {
      token: 'token-1',
      role: 'OWNER',
      accountId: 1,
      userName: 'Alice',
      carId: 'CAR-1'
    }

    saveAuthSession(session, storage)

    assert.equal(storage.getItem(AUTH_SESSION_KEY).includes('token-1'), true)
    assert.deepEqual(loadAuthSession(storage), session)
  })

  it('clears the active tab session', () => {
    const storage = memoryStorage()
    saveAuthSession({ token: 'token-2', role: 'ADMIN', accountId: 2, userName: 'admin' }, storage)

    clearAuthSession(storage)

    assert.equal(loadAuthSession(storage), null)
  })

  it('routes each role to its workspace', () => {
    assert.equal(defaultRouteForSession(null), '/login')
    assert.equal(defaultRouteForSession({ role: 'OWNER' }), '/owner')
    assert.equal(defaultRouteForSession({ role: 'ADMIN' }), '/admin')
  })
})
```

- [ ] **Step 2: Run frontend tests and verify failure**

Run:

```powershell
cd D:\softe\frontend
npm test
```

Expected: fails because `authSession.js` does not exist.

- [ ] **Step 3: Implement auth session helpers**

Create `frontend/src/utils/authSession.js`:

```js
export const AUTH_SESSION_KEY = 'charging-auth-session'

export function loadAuthSession(storage = globalThis.sessionStorage) {
  if (!storage) {
    return null
  }
  const raw = storage.getItem(AUTH_SESSION_KEY)
  if (!raw) {
    return null
  }
  try {
    const session = JSON.parse(raw)
    return session?.token && session?.role ? session : null
  } catch {
    return null
  }
}

export function saveAuthSession(session, storage = globalThis.sessionStorage) {
  if (!storage) {
    return
  }
  storage.setItem(AUTH_SESSION_KEY, JSON.stringify(session))
}

export function clearAuthSession(storage = globalThis.sessionStorage) {
  if (!storage) {
    return
  }
  storage.removeItem(AUTH_SESSION_KEY)
}

export function defaultRouteForSession(session) {
  if (session?.role === 'OWNER') {
    return '/owner'
  }
  if (session?.role === 'ADMIN') {
    return '/admin'
  }
  return '/login'
}
```

- [ ] **Step 4: Add login route helpers**

Modify `frontend/src/utils/hashRoute.js`:

```js
export const ROUTES = {
  LOGIN: '/login',
  STATION: '/station',
  OWNER: '/owner',
  ADMIN: '/admin'
}
```

Add label:

```js
[ROUTES.LOGIN]: '登录'
```

Change unknown fallback:

```js
return Object.values(ROUTES).includes(route) ? route : ROUTES.LOGIN
```

- [ ] **Step 5: Update hash route tests**

Modify `frontend/src/utils/hashRoute.test.js`:

```js
it('falls back unknown or blank routes to login', () => {
  assert.equal(normalizeRoute(''), ROUTES.LOGIN)
  assert.equal(normalizeRoute(null), ROUTES.LOGIN)
  assert.equal(normalizeRoute('#/unknown'), ROUTES.LOGIN)
})

it('maps routes to workspace labels', () => {
  assert.equal(routeLabel(ROUTES.LOGIN), '登录')
  assert.equal(routeLabel(ROUTES.OWNER), '车主自助')
  assert.equal(routeLabel(ROUTES.ADMIN), '运营管理')
  assert.equal(routeLabel(ROUTES.STATION), '站点运行')
})
```

Update the final assertion in the write test:

```js
assert.equal(setHashRoute('#/bad'), ROUTES.LOGIN)
assert.equal(globalThis.window.location.hash, '#/login')
```

- [ ] **Step 6: Run frontend tests**

Run:

```powershell
cd D:\softe\frontend
npm test
```

Expected: all frontend tests pass.

- [ ] **Step 7: Commit task 3**

```powershell
cd D:\softe
git add frontend/src/utils/authSession.js `
        frontend/src/utils/authSession.test.js `
        frontend/src/utils/hashRoute.js `
        frontend/src/utils/hashRoute.test.js
git commit -m "feat: add frontend auth session helpers"
```

## Task 4: Frontend Auth API

**Files:**

- Modify: `frontend/src/api/client.js`
- Modify: `frontend/src/api/chargingApi.js`

- [ ] **Step 1: Add token attachment helpers to Axios client**

Modify `frontend/src/api/client.js`:

```js
import axios from 'axios'
import { loadAuthSession } from '../utils/authSession'

export const http = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 8000
})

http.interceptors.request.use((config) => {
  const token = loadAuthSession()?.token
  if (token) {
    config.headers = config.headers || {}
    config.headers['X-Session-Token'] = token
  }
  return config
})

export async function unwrap(promise) {
  try {
    const response = await promise
    if (!response.data.success) {
      throw new Error(response.data.message || '请求失败')
    }
    return response.data.data
  } catch (error) {
    const message = error.response?.data?.message || error.message || '请求失败'
    throw new Error(message)
  }
}
```

- [ ] **Step 2: Add auth endpoints to `chargingApi.js`**

Modify `frontend/src/api/chargingApi.js` near the top of `api`:

```js
login: (payload) => unwrap(http.post('/auth/login', payload)),
currentUser: () => unwrap(http.get('/auth/me')),
logout: () => unwrap(http.post('/auth/logout')),
```

- [ ] **Step 3: Run frontend tests**

Run:

```powershell
cd D:\softe\frontend
npm test
```

Expected: all frontend tests pass.

- [ ] **Step 4: Commit task 4**

```powershell
cd D:\softe
git add frontend/src/api/client.js frontend/src/api/chargingApi.js
git commit -m "feat: add frontend auth api"
```

## Task 5: Unified Login View

**Files:**

- Create: `frontend/src/views/LoginView.vue`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: Create login view**

Create `frontend/src/views/LoginView.vue`:

```vue
<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="login-brand">
        <p>波普特大学充电站</p>
        <h1>调度计费系统</h1>
      </div>

      <el-form class="login-form" label-position="top" @submit.prevent="submitLogin">
        <el-form-item label="账号">
          <el-input v-model="loginName" placeholder="如 admin 或 CAR-1" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            show-password
          />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
        <el-button class="login-button" type="primary" native-type="submit" :loading="loading">
          登录
        </el-button>
      </el-form>

      <div class="login-hints">
        <span>管理员：admin / 123456</span>
        <span>车主：车辆编号 / 已设置密码</span>
      </div>
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { api } from '../api/chargingApi'
import { defaultRouteForSession, saveAuthSession } from '../utils/authSession'

const emit = defineEmits(['authenticated'])

const loginName = ref('admin')
const password = ref('123456')
const loading = ref(false)
const error = ref('')

async function submitLogin() {
  error.value = ''
  if (!loginName.value.trim() || !password.value) {
    error.value = '请输入账号和密码'
    return
  }
  loading.value = true
  try {
    const session = await api.login({
      loginName: loginName.value.trim(),
      password: password.value
    })
    saveAuthSession(session)
    emit('authenticated', {
      session,
      route: defaultRouteForSession(session)
    })
  } catch (err) {
    error.value = err.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>
```

- [ ] **Step 2: Add login styles**

Append to `frontend/src/styles.css`:

```css
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #f3f6fa;
  color: #172033;
}

.login-panel {
  width: min(420px, calc(100vw - 32px));
  background: #ffffff;
  border: 1px solid #dce4f0;
  border-radius: 8px;
  padding: 28px;
  box-shadow: 0 16px 40px rgba(20, 39, 70, 0.12);
}

.login-brand p {
  margin: 0 0 6px;
  color: #64748b;
}

.login-brand h1 {
  margin: 0 0 24px;
  font-size: 28px;
}

.login-form {
  display: grid;
  gap: 8px;
}

.login-button {
  width: 100%;
  margin-top: 8px;
}

.login-hints {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
  color: #64748b;
  font-size: 13px;
}
```

- [ ] **Step 3: Wire login view temporarily in App**

Modify `frontend/src/App.vue` imports:

```js
import LoginView from './views/LoginView.vue'
```

The full route guard is in Task 6, so do not commit this task until Task 6 passes.

## Task 6: Role Workspace Routing

**Files:**

- Create: `frontend/src/components/shell/RoleWorkspaceShell.vue`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/views/OwnerPanel.vue`

- [ ] **Step 1: Create role shell**

Create `frontend/src/components/shell/RoleWorkspaceShell.vue`:

```vue
<template>
  <div class="role-shell">
    <aside class="role-sidebar">
      <div class="workspace-brand">
        <strong>波普特大学充电站</strong>
        <span>{{ roleTitle }}</span>
      </div>

      <nav class="workspace-nav" aria-label="工作区导航">
        <button
          v-for="item in navItems"
          :key="item.route"
          class="workspace-nav-item"
          :class="{ active: item.route === activeRoute }"
          type="button"
          @click="$emit('navigate', item.route)"
        >
          <span class="workspace-nav-mark">{{ item.mark }}</span>
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.description }}</small>
          </span>
        </button>
      </nav>

      <el-button plain @click="$emit('logout')">退出登录</el-button>
    </aside>

    <section class="workspace-main">
      <header class="workspace-header">
        <div>
          <p class="workspace-eyebrow">{{ session.userName }}</p>
          <h1>{{ activeTitle }}</h1>
        </div>
        <el-tag effect="plain">{{ session.role === 'ADMIN' ? '管理员' : '车主' }}</el-tag>
      </header>
      <main class="workspace-content">
        <slot />
      </main>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ROUTES } from '../../utils/hashRoute'

defineEmits(['navigate', 'logout'])

const props = defineProps({
  session: {
    type: Object,
    required: true
  },
  activeRoute: {
    type: String,
    required: true
  }
})

const roleTitle = computed(() => (props.session.role === 'ADMIN' ? '运营管理工作台' : '车主自助门户'))

const navItems = computed(() => {
  if (props.session.role === 'ADMIN') {
    return [
      { route: ROUTES.ADMIN, mark: 'A', label: '运营管理', description: '调度、故障、参数与账单' },
      { route: ROUTES.STATION, mark: 'S', label: '站点沙盘', description: '查看统一站点态势' }
    ]
  }
  return [
    { route: ROUTES.OWNER, mark: 'O', label: '车主门户', description: '申请、排队、充电与账单' },
    { route: ROUTES.STATION, mark: 'S', label: '站点概览', description: '查看站点实时状态' }
  ]
})

const activeTitle = computed(() => {
  return navItems.value.find((item) => item.route === props.activeRoute)?.label || roleTitle.value
})
</script>
```

- [ ] **Step 2: Add shell styles**

Append to `frontend/src/styles.css`:

```css
.role-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  background: #f3f6fa;
}

.role-sidebar {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px;
  background: #14243a;
  color: #ffffff;
}

.role-sidebar .workspace-nav {
  flex: 1;
}

@media (max-width: 900px) {
  .role-shell {
    grid-template-columns: 1fr;
  }

  .role-sidebar {
    position: static;
  }
}
```

- [ ] **Step 3: Replace App route logic**

Replace `frontend/src/App.vue` with:

```vue
<template>
  <LoginView v-if="activeRoute === ROUTES.LOGIN || !session" @authenticated="handleAuthenticated" />

  <RoleWorkspaceShell
    v-else
    :session="session"
    :active-route="activeRoute"
    @navigate="navigateTo"
    @logout="logout"
  >
    <SimulationSandbox v-if="activeRoute === ROUTES.STATION" />
    <OwnerPanel v-else-if="activeRoute === ROUTES.OWNER" :session="session" />
    <AdminPanel v-else />
  </RoleWorkspaceShell>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import RoleWorkspaceShell from './components/shell/RoleWorkspaceShell.vue'
import SimulationSandbox from './views/SimulationSandbox.vue'
import OwnerPanel from './views/OwnerPanel.vue'
import AdminPanel from './views/AdminPanel.vue'
import LoginView from './views/LoginView.vue'
import { api } from './api/chargingApi'
import { clearAuthSession, defaultRouteForSession, loadAuthSession } from './utils/authSession'
import { ROUTES, normalizeRoute, setHashRoute } from './utils/hashRoute'

const session = ref(loadAuthSession())
const activeRoute = ref(normalizeRoute(window.location.hash))

function routeAllowed(route, currentSession) {
  if (!currentSession) {
    return route === ROUTES.LOGIN
  }
  if (route === ROUTES.LOGIN) {
    return false
  }
  if (route === ROUTES.OWNER) {
    return currentSession.role === 'OWNER'
  }
  if (route === ROUTES.ADMIN) {
    return currentSession.role === 'ADMIN'
  }
  return route === ROUTES.STATION
}

function syncRoute() {
  const normalized = normalizeRoute(window.location.hash)
  const fallback = defaultRouteForSession(session.value)
  const nextRoute = routeAllowed(normalized, session.value) ? normalized : fallback
  activeRoute.value = nextRoute

  if (window.location.hash !== `#${nextRoute}`) {
    setHashRoute(nextRoute)
  }
}

function navigateTo(route) {
  const normalized = normalizeRoute(route)
  if (!routeAllowed(normalized, session.value)) {
    activeRoute.value = setHashRoute(defaultRouteForSession(session.value))
    return
  }
  activeRoute.value = setHashRoute(normalized)
}

function handleAuthenticated({ session: nextSession, route }) {
  session.value = nextSession
  activeRoute.value = setHashRoute(route)
}

async function logout() {
  try {
    await api.logout()
  } catch {
    // Local logout is still valid if the backend session already expired.
  }
  clearAuthSession()
  session.value = null
  activeRoute.value = setHashRoute(ROUTES.LOGIN)
}

onMounted(() => {
  syncRoute()
  window.addEventListener('hashchange', syncRoute)
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncRoute)
})
</script>
```

- [ ] **Step 4: Let owner panel accept authenticated session**

Modify `frontend/src/views/OwnerPanel.vue` by adding props:

```js
const props = defineProps({
  session: {
    type: Object,
    default: null
  }
})
```

In `onMounted`, before demo account fallback, use:

```js
if (props.session?.carId) {
  await useExistingAccount(props.session.carId)
  return
}
```

Keep the existing manual registration/login flow for a user who opens the owner panel without a session during development, but the app should normally pass a session.

- [ ] **Step 5: Run frontend tests and build**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: tests pass and Vite build succeeds. Existing Rollup chunk warnings are acceptable.

- [ ] **Step 6: Commit tasks 5 and 6 together**

```powershell
cd D:\softe
git add frontend/src/views/LoginView.vue `
        frontend/src/components/shell/RoleWorkspaceShell.vue `
        frontend/src/App.vue `
        frontend/src/views/OwnerPanel.vue `
        frontend/src/styles.css
git commit -m "feat: add role-based workspaces"
```

## Task 7: End-to-End Verification

**Files:**

- No new files unless a verification failure requires a targeted fix.

- [ ] **Step 1: Run full backend test suite**

Run:

```powershell
cd D:\softe\backend
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run full frontend verification**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: `npm test` passes and `npm run build` succeeds.

- [ ] **Step 3: Manual browser verification**

Start or reuse backend and frontend:

```powershell
cd D:\softe\backend
.\mvnw.cmd spring-boot:run
```

```powershell
cd D:\softe\frontend
npm run dev
```

Open `http://127.0.0.1:5173/#/login`.

Verify:

1. Logging in with `admin / 123456` opens the admin workspace.
2. Refreshing the admin tab keeps admin logged in.
3. Opening a new tab and registering/setting a car password, then logging in as that car, opens the owner workspace.
4. Refreshing the owner tab keeps owner logged in.
5. The admin tab and owner tab do not overwrite each other's session.
6. Logging out returns only that tab to the login page.

- [ ] **Step 4: Commit any verification fixes**

If verification required fixes:

```powershell
cd D:\softe
git add <changed-files>
git commit -m "fix: stabilize role session workflow"
```

If no fixes were needed, do not create an empty commit.

## Self-Review

Spec coverage:

- Unified login: covered by Tasks 2, 5, 6.
- Multiple tabs with different accounts: covered by Task 3 and Task 7 manual verification.
- Refresh without re-login: covered by `sessionStorage` in Task 3 and Task 7.
- Role-separated entry: covered by Task 6.
- Existing owner/admin panels retained behind role shell: covered by Task 6.
- Strong backend permission enforcement: not included in this phase by design; it is the first item for the next auth hardening plan.

Placeholder scan:

- The plan contains no placeholder markers or unnamed files.
- Every created file has a path and concrete contents or exact change instructions.

Type consistency:

- Backend role type is `AccountRole`.
- Backend login response type is `AuthDtos.LoginResponse`.
- Frontend session fields match backend response: `token`, `role`, `accountId`, `userName`, `carId`.
- Frontend routes match `ROUTES.LOGIN`, `ROUTES.OWNER`, `ROUTES.ADMIN`, and `ROUTES.STATION`.
