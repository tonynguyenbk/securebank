package com.securebank.common.error;

import jakarta.validation.constraints.Max;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void optimisticLockConflictIs409() throws Exception {
        mvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"))
                .andExpect(jsonPath("$.path").value("/conflict"));
    }

    @Test
    void invalidRequestParamIs400WithFieldError() throws Exception {
        mvc.perform(get("/days").param("days", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("days"));
    }

    @Test
    void apiExceptionKeepsItsCodeAndStatus() throws Exception {
        mvc.perform(get("/busy"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BUSY"));
    }

    @Test
    void unexpectedErrorHidesDetails() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    @RestController
    static class TestController {
        @GetMapping("/conflict")
        String conflict() {
            throw new OptimisticLockingFailureException("row changed");
        }

        @GetMapping("/days")
        String days(@RequestParam @Max(90) int days) {
            return "ok";
        }

        @GetMapping("/busy")
        String busy() {
            throw new ApiException(ErrorCode.ACCOUNT_BUSY);
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
