package com.zntsns.awardtrace.search.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The committed {@code backend/openapi.json} is the API's contract (doc 07), and the web app's types are generated
 * from it. This fails when the generated spec drifts from it. After an intended change, run with
 * {@code -Dopenapi.update=true} to rewrite the file, then review its diff like any other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import(TestcontainersConfiguration.class)
class OpenApiContractIT {

    private static final Path CONTRACT = Path.of("openapi.json");

    @Autowired
    MockMvcTester mvc;

    @Test
    void generatedSpecMatchesTheCommittedContract() throws Exception {
        var result = mvc.get().uri("/api/v1/openapi.json").exchange();
        assertThat(result).hasStatusOk();
        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(CONTRACT, result.getResponse().getContentAsString());
        }

        assertThat(result).bodyJson()
                .as("The API no longer matches openapi.json; after an intended change, run with -Dopenapi.update=true")
                .isStrictlyEqualTo(new FileSystemResource(CONTRACT));
    }
}
