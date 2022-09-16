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

import com.palantir.goethe.Goethe;
import com.squareup.javapoet.*;
import net.ckozak.speeddial.annotation.SpeedDial;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.stream.Collectors;

public final class SpeedDialProcessor extends AbstractProcessor {

    private static final Set<String> ANNOTATIONS = Set.of(SpeedDial.class.getName());

    private Messager messager;
    private Filer filer;
    private Elements elements;
    private Types types;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        if (!env.processingOver()) {
            Map<TypeElement, Set<ExecutableElement>> methodsByType = new HashMap<>();
            for (Element element : env.getElementsAnnotatedWith(SpeedDial.class)) {
                if (element.getKind() != ElementKind.METHOD) {
                    messager.printMessage(
                            Diagnostic.Kind.ERROR, "Unexpected SpeedDial annotation on " + element.getKind(), element);
                    continue;
                }
                ExecutableElement executableElement = (ExecutableElement) element;
                TypeElement declaringType = (TypeElement) executableElement.getEnclosingElement();
                methodsByType.computeIfAbsent(declaringType, ignored -> new LinkedHashSet<>()).add(executableElement);
            }
            methodsByType.forEach(this::generateHelper);
        }
        return false;
    }

    private void generateHelper(TypeElement declaringType, Set<ExecutableElement> methods) {
        if (methods.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.ERROR, "No methods found", declaringType);
        }
        // TODO(ckozak): Support stand-in subtypes in modules with access to relevant subtypes.
        ClassName declaringTypeName = ClassName.get(declaringType);
        // TODO(ckozak): Better support for nested classes?
        ClassName utilityClassName = ClassName.get(declaringTypeName.packageName(), declaringTypeName.simpleName() + "SpeedDialer");
        Modifier[] additionalModifiers = declaringType.getModifiers().contains(Modifier.PUBLIC)
                ? new Modifier[]{Modifier.PUBLIC} : new Modifier[0];
        TypeSpec.Builder builder = TypeSpec.classBuilder(utilityClassName)
                .addOriginatingElement(declaringType)
                .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", "$S", SpeedDialProcessor.class.getName()).build())
                .addModifiers(Modifier.FINAL)
                .addModifiers(additionalModifiers)
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());
        for (ExecutableElement executableElement : methods) {
            AnnotationMirror speedDialAnnotation = executableElement.getAnnotationMirrors()
                    .stream().filter(mirror -> TypeName.get(mirror.getAnnotationType()).equals(ClassName.get(SpeedDial.class)))
                    .findFirst()
                    .orElse(null);
            if (speedDialAnnotation == null) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR, "Failed to find @SpeedDial annotation on element", executableElement);
                continue;
            }
            // be better here... should only be a single method on this interface for now
            Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValues =
                    elements.getElementValuesWithDefaults(speedDialAnnotation);
            AnnotationValue speedDialCommonImplementations = annotationValues.entrySet().stream()
                    .filter(entry -> entry.getKey().getSimpleName().contentEquals("common"))
                    .map(Map.Entry::getValue)
                    .findFirst().orElse(null);
            TypeMirror speedDialBase = annotationValues.entrySet().stream()
                    .filter(entry -> entry.getKey().getSimpleName().contentEquals("target"))
                    .map(Map.Entry::getValue)
                    .map(value -> (TypeMirror) value.getValue())
                    .findFirst().orElse(null);
            if (speedDialCommonImplementations == null || speedDialBase == null) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to read @SpeedDial annotation values", executableElement);
                continue;
            }
            List<AnnotationValue> values = (List<AnnotationValue>) speedDialCommonImplementations.getValue();
            List<TypeMirror> fastTypes = values.stream().map(value -> (TypeMirror) value.getValue()).collect(Collectors.toList());
            TypeName baseTypeName = TypeName.get(speedDialBase);

            TypeName returnType = TypeName.get(executableElement.getReturnType());
            String returnPrefix = TypeName.VOID.equals(returnType) ? "" : "return ";
            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(executableElement.getSimpleName().toString())
                    .addModifiers(additionalModifiers)
                    .addModifiers(Modifier.STATIC)
                    .addTypeVariables(executableElement.getTypeParameters().stream().map(TypeVariableName::get).collect(Collectors.toList()))
                            .addExceptions(executableElement.getThrownTypes().stream().map(TypeName::get).collect(Collectors.toList()))
                    .addParameter(ParameterSpec.builder(baseTypeName, "speedDialDelegate").build())
                    // TODO(ckozak): handle params named 'speedDialDelegate'?
                    .addParameters(executableElement.getParameters().stream().map(ParameterSpec::get).collect(Collectors.toList()))
                    .returns(returnType);
            for (TypeMirror fastType : fastTypes) {
                TypeName name = TypeName.get(fastType);
                methodBuilder.beginControlFlow("if ($N instanceof $T)", "speedDialDelegate", name)
                    .addStatement("$L$L", returnPrefix, invokeDelegate(CodeBlock.of("(($T) $N)", name, "speedDialDelegate"), executableElement));
                if (TypeName.VOID.equals(returnType)) {
                    methodBuilder.addStatement("return");
                }
                methodBuilder.endControlFlow();
            }
            builder.addMethod(methodBuilder.addStatement(
                            "$L$L",
                            returnPrefix,
                            invokeDelegate(CodeBlock.of("(($T) $N)", declaringType, "speedDialDelegate"),
                                    executableElement))
                    .build());
        }
        Goethe.formatAndEmit(
                JavaFile.builder(utilityClassName.packageName(), builder.build())
                        .skipJavaLangImports(true).build(),
                filer);
    }

    private static CodeBlock invokeDelegate(CodeBlock delegate, ExecutableElement executableElement) {
        String methodName = executableElement.getSimpleName().toString();
        return CodeBlock.of("$L.$L$N($L)",
                delegate,
                executableElement.getTypeParameters().isEmpty()
                        ? ""
                        : executableElement.getTypeParameters().stream().map(param -> CodeBlock.of("$N", param.getSimpleName())).collect(CodeBlock.joining(",", "<", ">")),
                methodName,
                executableElement.getParameters().stream().map(param -> CodeBlock.of("$N", param.getSimpleName().toString())).collect(CodeBlock.joining(", ")));
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return ANNOTATIONS;
    }
}
