package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bupt.charging.domain.AccountRole;
import com.bupt.charging.dto.AccountDtos;
import com.bupt.charging.dto.AuthDtos;
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

    @Autowired
    private AuthService authService;

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

    @Test
    void currentUserReadsActiveSession() {
        accountService.createNewAccount("CAR-AUTH-3", "Owner Three", 90.0);
        accountService.setPassword("CAR-AUTH-3", "secret");
        AuthDtos.LoginResponse login = authService.login("CAR-AUTH-3", "secret");

        AuthDtos.CurrentUserResponse currentUser = authService.currentUser(login.token());

        assertEquals(AccountRole.OWNER, currentUser.role());
        assertEquals("CAR-AUTH-3", currentUser.carId());
        assertEquals("Owner Three", currentUser.userName());
    }
}
