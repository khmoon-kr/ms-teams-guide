package com.example.teams.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starter 모듈의 AutoConfiguration 규칙 및 문서화 규칙(package-info.java, Javadoc, 메타데이터)을 자동 검증하는 테스트.
 */
class LibraryArchitectureRulesTest {

    @Test
    void autoConfigurationMustNotUseComponentScan() {
        assertThat(TeamsAutoConfiguration.class.isAnnotationPresent(ComponentScan.class))
                .as("Starter AutoConfiguration은 소비 앱 오염 방지를 위해 @ComponentScan을 사용하면 안 됩니다.")
                .isFalse();
    }

    @Test
    void everyAutoConfiguredBeanMustHaveConditionalOnMissingBean() {
        Method[] methods = TeamsAutoConfiguration.class.getDeclaredMethods();
        int beanCount = 0;
        for (Method method : methods) {
            if (method.isAnnotationPresent(Bean.class)) {
                beanCount++;
                assertThat(method.isAnnotationPresent(ConditionalOnMissingBean.class))
                        .as("@Bean 메서드 '%s'에는 반드시 @ConditionalOnMissingBean이 선언되어야 합니다.", method.getName())
                        .isTrue();
            }
        }
        assertThat(beanCount).isGreaterThan(0);
    }

    @Test
    void starterPackagesHavePackageInfoJavadocAndConfigMetadata() throws IOException {
        Path mainJava = Path.of("src/main/java");
        try (Stream<Path> walk = Files.walk(mainJava)) {
            List<Path> javaFiles = walk.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(javaFiles).isNotEmpty();

            Set<Path> packageDirs = javaFiles.stream()
                    .filter(p -> !p.getFileName().toString().equals("package-info.java"))
                    .map(Path::getParent)
                    .collect(Collectors.toSet());

            for (Path pkgDir : packageDirs) {
                assertThat(Files.exists(pkgDir.resolve("package-info.java")))
                        .as("Starter 패키지 '%s'에 package-info.java가 존재해야 합니다.", pkgDir)
                        .isTrue();
            }

            for (Path file : javaFiles) {
                assertThat(Files.readString(file))
                        .as("Starter 파일(%s)에 Javadoc이 존재해야 합니다.", file.getFileName())
                        .contains("/**");
            }
        }

        Path metadataJson = Path.of("src/main/resources/META-INF/spring-configuration-metadata.json");
        assertThat(Files.exists(metadataJson))
                .as("IDE 자동완성 지원을 위한 spring-configuration-metadata.json 파일이 존재해야 합니다.")
                .isTrue();
    }
}
