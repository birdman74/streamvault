package com.streamvault.backend.convention;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Enforces ADR-001 (docs/adr/ADR-001-spring-mvc-test-auth-pattern.md) and the "Two-Layer Testing
 * Convention" in CONTRIBUTING.md for Layer 1 controller slice tests:
 *
 * <ul>
 *   <li>the authenticated principal is populated with {@code @WithMockUser} or a custom
 *       {@code @WithSecurityContext} annotation, and</li>
 *   <li>{@code SecurityContextHolder} is never touched directly from test code -- that is an
 *       implementation detail of how {@code @WithMockUser} works internally.</li>
 * </ul>
 *
 * Added in response to Brian's Changes Requested review on PR #25: the story-005
 * {@code AccountSettingsControllerTest} seeds its principal by calling
 * {@code SecurityContextHolder.getContext().setAuthentication(...)} in a {@code @BeforeEach}. This
 * guard fails until that slice test is refactored to the agreed convention, and then protects
 * every future controller slice test from reintroducing the non-standard pattern.
 *
 * This is a source-scanning convention test: it reads the test tree from disk rather than loading
 * a Spring context, so it is deterministic and holds no state between runs.
 */
class ControllerSliceTestAuthConventionTest {

    private static final Path TEST_SOURCE_ROOT = Path.of("src", "test", "java");
    private static final String THIS_TEST_FILE = "ControllerSliceTestAuthConventionTest.java";

    /** Token that marks a Layer 1 slice test, kept split so this file does not match itself. */
    private static final String SLICE_TEST_MARKER = "@" + "WebMvcTest";
    private static final String FORBIDDEN_DIRECT_CONTEXT_ACCESS = "Security" + "ContextHolder";

    @Test
    void should_notReferenceSecurityContextHolderDirectly_when_testIsAControllerSliceTest() throws IOException {
        assertThat(Files.isDirectory(TEST_SOURCE_ROOT))
                .as("test source root %s must exist (run from the backend module dir)", TEST_SOURCE_ROOT.toAbsolutePath())
                .isTrue();

        List<String> offenders = sliceTestSources()
                .filter(source -> source.content().contains(FORBIDDEN_DIRECT_CONTEXT_ACCESS))
                .map(TestSource::relativePath)
                .sorted()
                .toList();

        assertThat(offenders)
                .as("@WebMvcTest controller slice tests must populate the security context via "
                        + "@WithMockUser or a custom @WithSecurityContext annotation (ADR-001), never by "
                        + "touching SecurityContextHolder directly")
                .isEmpty();
    }

    @Test
    void should_populatePrincipalWithAWithAnnotation_when_sliceTestNeedsAnAuthenticatedPrincipal() throws IOException {
        List<String> offenders = sliceTestSources()
                .filter(source -> source.content().contains("@AuthenticationPrincipal")
                        || source.content().contains("new AuthenticatedUser("))
                .filter(source -> !mentionsAWithSecurityAnnotation(source.content()))
                .map(TestSource::relativePath)
                .sorted()
                .toList();

        assertThat(offenders)
                .as("@WebMvcTest slice tests that exercise @AuthenticationPrincipal must declare the "
                        + "principal with @WithMockUser or a custom @WithSecurityContext annotation (ADR-001)")
                .isEmpty();
    }

    private static boolean mentionsAWithSecurityAnnotation(String content) {
        return content.contains("@WithMockUser")
                || content.contains("@WithSecurityContext")
                || content.contains("@WithMockAuthenticatedUser");
    }

    private Stream<TestSource> sliceTestSources() throws IOException {
        try (Stream<Path> paths = Files.walk(TEST_SOURCE_ROOT)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith("Test.java"))
                    .filter(path -> !path.getFileName().toString().equals(THIS_TEST_FILE))
                    .map(TestSource::read)
                    .filter(source -> source.content().contains(SLICE_TEST_MARKER))
                    .toList()
                    .stream();
        }
    }

    private record TestSource(Path path, String content) {

        static TestSource read(Path path) {
            try {
                return new TestSource(path, Files.readString(path));
            } catch (IOException e) {
                throw new UncheckedIOException("failed to read test source " + path, e);
            }
        }

        String relativePath() {
            return TEST_SOURCE_ROOT.relativize(path).toString().replace('\\', '/');
        }
    }
}
