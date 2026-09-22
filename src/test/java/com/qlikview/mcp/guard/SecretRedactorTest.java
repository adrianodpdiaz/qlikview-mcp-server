package com.qlikview.mcp.guard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretRedactorTest {

    private final SecretRedactor redactor = new SecretRedactor();

    @Test
    void redactsPwdKeyValuePair() {
        String result = redactor.redactConnectionCredentials("Driver={SQL Server};UID=admin;PWD=hunter2;Server=x");
        assertThat(result).contains("PWD=***REDACTED***").doesNotContain("hunter2");
    }

    @Test
    void redactsPasswordKeyValuePair() {
        String result = redactor.redactConnectionCredentials("Password=hunter2;Server=x");
        assertThat(result).contains("Password=***REDACTED***").doesNotContain("hunter2");
    }

    @Test
    void redactsUidAndUserIdVariants() {
        String result = redactor.redactConnectionCredentials("UID=admin;User ID=admin2;PWD=secret");

        assertThat(result)
            .contains("UID=***REDACTED***")
            .contains("User ID=***REDACTED***")
            .doesNotContain("admin")
            .doesNotContain("admin2");
    }

    @Test
    void redactsQuotedValue() {
        String result = redactor.redactConnectionCredentials("PWD='hunter2;withSemicolon'");
        assertThat(result).contains("PWD=***REDACTED***").doesNotContain("hunter2");
    }

    @Test
    void isCaseInsensitive() {
        String result = redactor.redactConnectionCredentials("pwd=hunter2");
        assertThat(result).contains("***REDACTED***").doesNotContain("hunter2");
    }

    @Test
    void leavesNonCredentialTextUnchanged() {
        String script = "LOAD RecNo() as FactID AUTOGENERATE 10;";
        assertThat(redactor.redactConnectionCredentials(script)).isEqualTo(script);
    }

    @Test
    void leavesTablesAndServerNamesUnredacted() {
        String result = redactor.redactConnectionCredentials("Server=prod-db-01;Database=Sales;PWD=hunter2");
        assertThat(result).contains("Server=prod-db-01").contains("Database=Sales").doesNotContain("hunter2");
    }

    @Test
    void handlesNullAndEmptyGracefully() {
        assertThat(redactor.redactConnectionCredentials(null)).isNull();
        assertThat(redactor.redactConnectionCredentials("")).isEmpty();
    }

    @Test
    void flagsSecretLikeVariableNames() {
        assertThat(redactor.looksLikeSecretName("vPassword")).isTrue();
        assertThat(redactor.looksLikeSecretName("PWD")).isTrue();
        assertThat(redactor.looksLikeSecretName("apiSecret")).isTrue();
        assertThat(redactor.looksLikeSecretName("authToken")).isTrue();
        assertThat(redactor.looksLikeSecretName("myApiKey")).isTrue();
    }

    @Test
    void doesNotFlagOrdinaryVariableNames() {
        assertThat(redactor.looksLikeSecretName("vRowCount")).isFalse();
        assertThat(redactor.looksLikeSecretName("DateFormat")).isFalse();
        assertThat(redactor.looksLikeSecretName(null)).isFalse();
    }
}
