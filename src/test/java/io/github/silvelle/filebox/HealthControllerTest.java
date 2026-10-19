package io.github.silvelle.filebox;

import io.github.silvelle.filebox.health.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(HealthController.class)
public final class HealthControllerTest {
    @Autowired
    MockMvcTester mvc;

    @Test
    void healthReturnsUp() {
        assertThat(mvc.get().uri("/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status").isEqualTo("UP");
    }
}
