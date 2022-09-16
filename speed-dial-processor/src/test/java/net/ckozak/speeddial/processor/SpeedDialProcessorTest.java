/*
 * (c) Copyright 2022 Carter Kozak
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.ckozak.speeddial.processor;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.io.ByteSource;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import net.ckozak.speeddial.processor.example.Simple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.testing.compile.CompilationSubject.assertThat;

class SpeedDialProcessorTest {

    private static final boolean DEV_MODE = Boolean.getBoolean("recreate");
    private static final Path TEST_CLASSES_BASE_DIR = Paths.get("src", "test", "java");
    private static final Path RESOURCES_BASE_DIR = Paths.get("src", "test", "resources");

    @Test
    void testExampleFileCompiles() {
        assertTestFileCompileAndMatches(TEST_CLASSES_BASE_DIR, Simple.class);
    }

    private static void assertTestFileCompileAndMatches(Path basePath, Class<?> clazz) {
        Compilation compilation = compileTestClass(basePath, clazz);
        assertThat(compilation).succeededWithoutWarnings();
        String generatedClassName = clazz.getSimpleName() + "SpeedDialer";
        String generatedFqnClassName = clazz.getPackage().getName() + "." + generatedClassName;
        String generatedClassFileRelativePath = generatedFqnClassName.replaceAll("\\.", "/") + ".java";
        Assertions.assertThat(compilation.generatedFile(StandardLocation.SOURCE_OUTPUT, generatedClassFileRelativePath))
                .hasValueSatisfying(
                        javaFileObject -> assertContentsMatch(javaFileObject, generatedClassFileRelativePath));
    }

    private static Compilation compileTestClass(Path basePath, Class<?> clazz) {
        Path clazzPath = basePath.resolve(Paths.get(
                Joiner.on("/").join(Splitter.on(".").split(clazz.getPackage().getName())),
                clazz.getSimpleName() + ".java"));
        try {
            return Compiler.javac()
                    .withOptions("-source", "11", "-Werror", "-Xlint:unchecked")
                    .withProcessors(new SpeedDialProcessor())
                    .compile(JavaFileObjects.forResource(clazzPath.toUri().toURL()));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertContentsMatch(JavaFileObject javaFileObject, String generatedClassFile) {
        try {
            Path output = RESOURCES_BASE_DIR.resolve(generatedClassFile + ".generated");
            String generatedContents = readJavaFileObject(javaFileObject);
            if (DEV_MODE) {
                Files.deleteIfExists(output);
                if (!Files.exists(output.getParent())) {
                    Files.createDirectories(output.getParent());
                }
                Files.write(output, generatedContents.getBytes(StandardCharsets.UTF_8));
            }
            Assertions.assertThat(generatedContents).isEqualTo(Files.readString(output));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String readJavaFileObject(JavaFileObject javaFileObject) throws IOException {
        return new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return javaFileObject.openInputStream();
            }
        }.asCharSource(StandardCharsets.UTF_8).read();
    }
}