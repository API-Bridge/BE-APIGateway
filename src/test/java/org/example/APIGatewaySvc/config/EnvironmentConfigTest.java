package org.example.APIGatewaySvc.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 환경변수 설정 테스트
 */
@SpringBootTest
@TestPropertySource(properties = {
    "AUTH0_CLIENT_ID=test-client-id",
    "AUTH0_CLIENT_SECRET=test-client-secret",
    "AUTH0_ISSUER_URI=https://test.auth0.com/",
    "AUTH0_AUDIENCE=test-audience"
})
class EnvironmentConfigTest {

    @Autowired
    private Environment environment;

    @Autowired
    private EnvironmentConfig.EnvironmentChecker environmentChecker;

    @Test
    void testEnvironmentVariablesLoaded() {
        // Auth0 환경변수가 올바르게 로드되었는지 확인
        assertThat(environment.getProperty("AUTH0_CLIENT_ID")).isEqualTo("test-client-id");
        assertThat(environment.getProperty("AUTH0_CLIENT_SECRET")).isEqualTo("test-client-secret");
        assertThat(environment.getProperty("AUTH0_ISSUER_URI")).isEqualTo("https://test.auth0.com/");
        assertThat(environment.getProperty("AUTH0_AUDIENCE")).isEqualTo("test-audience");
    }

    @Test
    void testEnvironmentChecker() {
        // EnvironmentChecker가 정상 동작하는지 확인
        assertThat(environmentChecker.getEnvironmentVariable("AUTH0_CLIENT_ID"))
            .isEqualTo("test-client-id");
        
        assertThat(environmentChecker.isEnvironmentVariableSet("AUTH0_CLIENT_ID"))
            .isTrue();
        
        assertThat(environmentChecker.isEnvironmentVariableSet("NON_EXISTENT_VAR"))
            .isFalse();
        
        assertThat(environmentChecker.areAuth0VariablesSet())
            .isTrue();
    }
}