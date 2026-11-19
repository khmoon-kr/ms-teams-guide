package com.example.teams;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 08_java_library_coding_rules.md 핵심 규칙(도메인 중립성, 무의존성, Javadoc 및 package-info 필수화)을
 * 빌드 타임에 자동 검증하는 아키텍처 테스트.
 */
class CoreDependencyRuleTest {

    private static final List<String> BANNED_DOMAIN_COUPLING_TERMS = List.of(
            "Billing", "billing", "Refund", "refund", "Capture", "Merchant", "txId", "PG_"
    );

    @Test
    void coreModuleMustNotHaveSpringOnClasspath() {
        boolean springLoaded;
        try {
            Class.forName("org.springframework.context.ApplicationContext");
            springLoaded = true;
        } catch (ClassNotFoundException expected) {
            springLoaded = false;
        }
        assertThat(springLoaded)
                .as("teams-core 모듈 클래스패스에 Spring 의존성이 섞이면 안 됩니다.")
                .isFalse();
    }

    @Test
    void coreSourcesFollowDomainNeutralityCleanRulesAndDocumentation() throws IOException {
        Path mainJava = Path.of("src/main/java");
        try (Stream<Path> walk = Files.walk(mainJava)) {
            List<Path> javaFiles = walk.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(javaFiles).isNotEmpty();

            // 규칙 8: 소스 파일이 존재하는 모든 패키지에 package-info.java가 반드시 존재해야 함
            Set<Path> packageDirs = javaFiles.stream()
                    .filter(p -> !p.getFileName().toString().equals("package-info.java"))
                    .map(Path::getParent)
                    .collect(Collectors.toSet());

            for (Path pkgDir : packageDirs) {
                Path pkgInfo = pkgDir.resolve("package-info.java");
                assertThat(Files.exists(pkgInfo))
                        .as("라이브러리 패키지 '%s'에 package-info.java 문서가 누락되었습니다 (규칙 8).", pkgDir)
                        .isTrue();
            }

            for (Path file : javaFiles) {
                String fileName = file.getFileName().toString();
                String src = Files.readString(file);

                assertThat(src)
                        .as("라이브러리 파일(%s)에 Javadoc(/** ... */) 설명이 누락되었습니다 (규칙 8).", fileName)
                        .contains("/**");

                for (String bannedTerm : BANNED_DOMAIN_COUPLING_TERMS) {
                    assertThat(src)
                            .as("teams-core 파일(%s)에는 특정 비즈니스 도메인 용어('%s')가 포함되면 안 됩니다 (규칙 1).",
                                    fileName, bannedTerm)
                            .doesNotContain(bannedTerm);
                }

                assertThat(src)
                        .as("%s 에 Spring import가 포함되면 안 됩니다.", fileName)
                        .doesNotContain("import org.springframework.");
                assertThat(src)
                        .as("%s 에 System.out/err 직접 출력이 포함되면 안 됩니다.", fileName)
                        .doesNotContain("System.out.")
                        .doesNotContain("System.err.")
                        .doesNotContain("printStackTrace(");
                assertThat(src)
                        .as("%s 에서 JVM 전역 상태를 변경하면 안 됩니다.", fileName)
                        .doesNotContain("TimeZone.setDefault")
                        .doesNotContain("Locale.setDefault")
                        .doesNotContain("System.setProperty");
            }
        }
    }
}
