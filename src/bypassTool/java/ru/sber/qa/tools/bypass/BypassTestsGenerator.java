package ru.sber.qa.tools.bypass;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Generates registration-only JUnit5 tests for the Gradle bypassTests step.
 *
 * <p>The generated tests intentionally do not execute functional checks. They keep the original
 * package/class/method identity and finish as passed, so TMS/TestOps can import/update autotests
 * from a dedicated technical run.</p>
 *
 * <p>Only report-eligible tests are generated. Display names, links and semantic reporting
 * annotations ({@code @CriticalRegression}, {@code @Regression}, {@code @ManualTest}) are retained;
 * canonical tags are added by the shared Allure listener.</p>
 */
public final class BypassTestsGenerator {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z_][\\w.]*)\\s*;");
    private static final Pattern TYPE_PATTERN = Pattern.compile("(?m)^\\s*(?:public\\s+|protected\\s+|private\\s+|abstract\\s+|final\\s+|static\\s+)*class\\s+([A-Za-z_$][\\w$]*)\\b");
    private static final Pattern TEST_METHOD_PATTERN = Pattern.compile(
            "(?s)@(?:org\\.junit\\.jupiter\\.api\\.)?(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\\b" +
                    ".*?" +
                    "(?:public|protected|private|static|final|synchronized|native|strictfp|\\s)+" +
                    "(?:<[^>{}]+>\\s*)?" +
                    "[A-Za-z_$][\\w$<>\\[\\].?,\\s]*\\s+" +
                    "([A-Za-z_$][\\w$]*)\\s*\\(([^;{}]*)\\)\\s*" +
                    "(?:throws\\s+[^;{}]+)?\\{");
    private static final Pattern TAG_PATTERN = Pattern.compile("@(?:org\\.junit\\.jupiter\\.api\\.)?Tag\\s*\\(\\s*\"((?:\\\\.|[^\\\"])*)\"\\s*\\)", Pattern.DOTALL);
    private static final Pattern LABEL_PATTERN = Pattern.compile("@(?:io\\.qameta\\.allure\\.)?Label\\s*\\(\\s*(?:name\\s*=\\s*)?\"((?:\\\\.|[^\\\"])*)\"\\s*,\\s*(?:value\\s*=\\s*)?\"((?:\\\\.|[^\\\"])*)\"\\s*\\)", Pattern.DOTALL);
    private static final Pattern LINK_PATTERN = Pattern.compile("@(?:io\\.qameta\\.allure\\.)?Link\\s*\\(([^)]*)\\)", Pattern.DOTALL);
    private static final Pattern NAMED_STRING_ARGUMENT_PATTERN = Pattern.compile("(?:^|,)\\s*%s\\s*=\\s*\"((?:\\\\.|[^\\\"])*)\"", Pattern.DOTALL);
    private static final Pattern FIRST_STRING_ARGUMENT_PATTERN = Pattern.compile("^\\s*\"((?:\\\\.|[^\\\"])*)\"", Pattern.DOTALL);
    private static final Pattern SEVERITY_PATTERN = Pattern.compile("@(?:io\\.qameta\\.allure\\.)?Severity\\s*\\(\\s*(?:SeverityLevel\\.)?([A-Z_]+)\\s*\\)", Pattern.DOTALL);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+(?!static\\b)([a-zA-Z_][\\w.]*)(\\.\\*)?\\s*;");
    private static final Set<String> JAVA_LANG_TYPES = Set.of(
            "Boolean", "Byte", "Character", "Class", "Double", "Enum", "Float", "Integer",
            "Long", "Object", "Short", "String", "StringBuilder", "StringBuffer", "Throwable"
    );
    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "boolean", "byte", "char", "double", "float", "int", "long", "short"
    );

    private BypassTestsGenerator() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected arguments: <source-test-java-dir> <generated-output-dir> <excluded-tests-file>");
        }

        Path sourceRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path outputRoot = Path.of(args[1]).toAbsolutePath().normalize();
        Path excludedTestsFile = Path.of(args[2]).toAbsolutePath().normalize();
        Set<String> excludedTests = loadExcludedTests(excludedTestsFile);

        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source test directory does not exist: " + sourceRoot);
        }

        deleteDirectory(outputRoot);
        Files.createDirectories(outputRoot);

        GenerationStats stats = new GenerationStats();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            Optional<GeneratedClass> generatedClass = generateClass(sourceRoot, path, excludedTests);
                            if (generatedClass.isPresent()) {
                                writeGeneratedClass(outputRoot, generatedClass.get());
                                stats.classes++;
                                stats.methods += generatedClass.get().methods().size();
                            }
                        } catch (IOException exception) {
                            throw new RuntimeException("Failed to generate bypass test for " + path, exception);
                        }
                    });
        }

        String summary = "Bypass tests generated: classes=" + stats.classes + ", methods=" + stats.methods;
        Files.writeString(outputRoot.resolve("bypass-tests-summary.txt"), summary + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println(summary);
    }

    private static Optional<GeneratedClass> generateClass(Path sourceRoot, Path sourceFile, Set<String> excludedTests) throws IOException {
        String rawSource = Files.readString(sourceFile, StandardCharsets.UTF_8);
        String source = stripComments(rawSource);

        String packageName = findFirst(PACKAGE_PATTERN, source)
                .orElseThrow(() -> new IllegalStateException("Cannot find package in " + sourceFile));

        Matcher typeMatcher = TYPE_PATTERN.matcher(source);
        String className;
        String classAnnotationBlock = "";
        if (typeMatcher.find()) {
            className = typeMatcher.group(1);
            classAnnotationBlock = findAnnotationBlockBefore(source, typeMatcher.start());
        } else {
            className = sourceFile.getFileName().toString().replaceFirst("\\.java$", "");
        }

        Metadata classMetadata = extractMetadata(classAnnotationBlock);
        String fqcn = packageName + "." + className;
        ImportContext imports = extractImports(source);
        List<GeneratedMethod> methods = findTestMethods(source, classMetadata, fqcn, packageName, imports, excludedTests);
        if (methods.isEmpty()) {
            return Optional.empty();
        }

        Path relativeSource = sourceRoot.relativize(sourceFile);
        return Optional.of(new GeneratedClass(packageName, className, relativeSource.toString().replace('\\', '/'), classMetadata, methods));
    }

    private static List<GeneratedMethod> findTestMethods(String source,
                                                         Metadata classMetadata,
                                                         String fqcn,
                                                         String packageName,
                                                         ImportContext imports,
                                                         Set<String> excludedTests) {
        Matcher matcher = TEST_METHOD_PATTERN.matcher(source);
        List<GeneratedMethod> methods = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();

        while (matcher.find()) {
            String testBlock = matcher.group();
            String annotationBlock = findAnnotationBlockBefore(source, matcher.start()) + testBlock;
            Metadata methodMetadata = extractMetadata(annotationBlock);

            String originalName = matcher.group(1);
            String originalParameters = matcher.group(2);
            if (excludedTests.contains(fqcn + "." + originalName)) {
                continue;
            }
            String generatedName = originalName;

            int duplicateNumber = 2;
            while (!seenNames.add(generatedName)) {
                generatedName = originalName + "Bypass" + duplicateNumber;
                duplicateNumber++;
            }

            String displayName = methodMetadata.displayName()
                    .or(() -> classMetadata.displayName())
                    .orElse(originalName);

            String originalParameterSignature = resolveParameterSignature(originalParameters, packageName, imports);
            String originalTestCaseId = "[engine:junit-jupiter]/[class:" + fqcn + "]/[method:"
                    + originalName + "(" + originalParameterSignature + ")]";
            methods.add(new GeneratedMethod(
                    originalName,
                    generatedName,
                    displayName,
                    methodMetadata,
                    fqcn + "." + originalName,
                    originalTestCaseId,
                    md5Hex(originalTestCaseId)
            ));
        }

        return methods;
    }

    private static Metadata extractMetadata(String annotationBlock) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        Matcher tagMatcher = TAG_PATTERN.matcher(annotationBlock);
        while (tagMatcher.find()) {
            tags.add(unescapeJavaString(tagMatcher.group(1)));
        }

        LinkedHashMap<String, LinkedHashSet<String>> labels = new LinkedHashMap<>();
        addStringAnnotationLabels(annotationBlock, labels, "Owner", "owner");
        addStringAnnotationLabels(annotationBlock, labels, "Epic", "epic");
        addStringAnnotationLabels(annotationBlock, labels, "Feature", "feature");
        addStringAnnotationLabels(annotationBlock, labels, "Story", "story");
        addStringAnnotationLabels(annotationBlock, labels, "Description", "description");
        addStringAnnotationLabels(annotationBlock, labels, "ParentSuite", "parentSuite");
        addStringAnnotationLabels(annotationBlock, labels, "Suite", "suite");
        addStringAnnotationLabels(annotationBlock, labels, "SubSuite", "subSuite");
        addStringAnnotationLabels(annotationBlock, labels, "AllureId", "AS_ID");
        addStringAnnotationLabels(annotationBlock, labels, "TmsLink", "tms");
        addStringAnnotationLabels(annotationBlock, labels, "Issue", "issue");

        Matcher severityMatcher = SEVERITY_PATTERN.matcher(annotationBlock);
        while (severityMatcher.find()) {
            addLabel(labels, "severity", severityMatcher.group(1).toLowerCase());
        }

        Matcher labelMatcher = LABEL_PATTERN.matcher(annotationBlock);
        while (labelMatcher.find()) {
            addLabel(labels, unescapeJavaString(labelMatcher.group(1)), unescapeJavaString(labelMatcher.group(2)));
        }

        List<GeneratedLink> links = extractLinks(annotationBlock);
        boolean criticalRegression = containsSimpleAnnotation(annotationBlock, "CriticalRegression");
        boolean regression = criticalRegression || containsSimpleAnnotation(annotationBlock, "Regression");
        boolean manual = containsSimpleAnnotation(annotationBlock, "ManualTest");
        Optional<String> displayName = extractDisplayName(annotationBlock);

        return new Metadata(tags, labels, links, criticalRegression, regression, manual, displayName);
    }

    private static void addStringAnnotationLabels(String annotationBlock,
                                                  LinkedHashMap<String, LinkedHashSet<String>> labels,
                                                  String annotationName,
                                                  String labelName) {
        Pattern pattern = Pattern.compile("@(?:[a-zA-Z_][\\w.]*\\.)?" + Pattern.quote(annotationName)
                + "\\s*\\(\\s*\"((?:\\\\.|[^\\\"])*)\"\\s*\\)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(annotationBlock);
        while (matcher.find()) {
            addLabel(labels, labelName, unescapeJavaString(matcher.group(1)));
        }
    }

    private static List<GeneratedLink> extractLinks(String annotationBlock) {
        List<GeneratedLink> links = new ArrayList<>();
        Matcher linkMatcher = LINK_PATTERN.matcher(annotationBlock);
        while (linkMatcher.find()) {
            String args = linkMatcher.group(1);
            String url = namedStringArgument(args, "url")
                    .or(() -> firstStringArgument(args))
                    .orElse("");
            String name = namedStringArgument(args, "name").orElse(url);
            String type = namedStringArgument(args, "type").orElse("link");
            if (!url.isBlank()) {
                links.add(new GeneratedLink(name, type, url));
            }
        }
        return links;
    }

    private static Optional<String> namedStringArgument(String args, String name) {
        Pattern pattern = Pattern.compile(NAMED_STRING_ARGUMENT_PATTERN.pattern().formatted(Pattern.quote(name)), Pattern.DOTALL);
        Matcher matcher = pattern.matcher(args);
        if (matcher.find()) {
            return Optional.of(unescapeJavaString(matcher.group(1)));
        }
        return Optional.empty();
    }

    private static Optional<String> firstStringArgument(String args) {
        Matcher matcher = FIRST_STRING_ARGUMENT_PATTERN.matcher(args);
        if (matcher.find()) {
            return Optional.of(unescapeJavaString(matcher.group(1)));
        }
        return Optional.empty();
    }

    private static boolean containsSimpleAnnotation(String annotationBlock, String annotationName) {
        Pattern pattern = Pattern.compile("@(?:[a-zA-Z_][\\w.]*\\.)?" + Pattern.quote(annotationName) + "(?:\\s|\\(|$)", Pattern.DOTALL);
        return pattern.matcher(annotationBlock).find();
    }

    private static void addLabel(LinkedHashMap<String, LinkedHashSet<String>> labels, String name, String value) {
        if (name == null || name.isBlank() || value == null || value.isBlank()) {
            return;
        }
        labels.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(value);
    }

    private static void writeGeneratedClass(Path outputRoot, GeneratedClass generatedClass) throws IOException {
        Path packageDir = outputRoot.resolve(generatedClass.packageName().replace('.', '/'));
        Files.createDirectories(packageDir);

        Path targetFile = packageDir.resolve(generatedClass.className() + ".java");
        Files.writeString(targetFile, render(generatedClass), StandardCharsets.UTF_8);
    }

    private static String render(GeneratedClass generatedClass) {
        StringBuilder builder = new StringBuilder();
        Metadata classMetadata = generatedClass.classMetadata();

        builder.append("package ").append(generatedClass.packageName()).append(";\n\n")
                .append("import io.qameta.allure.Allure;\n")
                .append("import io.qameta.allure.model.Label;\n")
                .append("import org.junit.jupiter.api.DisplayName;\n")
                .append("import org.junit.jupiter.api.Test;\n");
        if (hasCriticalRegression(generatedClass)) {
            builder.append("import ru.sber.qa.allure.CriticalRegression;\n");
        }
        if (hasRegression(generatedClass)) {
            builder.append("import ru.sber.qa.allure.Regression;\n");
        }
        if (hasManual(generatedClass)) {
            builder.append("import ru.sber.qa.allure.ManualTest;\n");
        }
        builder.append("\n")
                .append("@SuppressWarnings(\"all\")\n");
        if (classMetadata.criticalRegression()) {
            builder.append("@CriticalRegression\n");
        } else if (classMetadata.regression()) {
            builder.append("@Regression\n");
        }
        if (classMetadata.manual()) {
            builder.append("@ManualTest\n");
        }

        builder.append("@DisplayName(\"").append(escapeJavaString(generatedClass.className())).append(" / bypass registration mode\")\n")
                .append("class ").append(generatedClass.className()).append(" {\n\n");

        for (GeneratedMethod method : generatedClass.methods()) {
            Metadata methodMetadata = method.metadata();
            builder.append("    @Test\n")
                    .append("    @DisplayName(\"").append(escapeJavaString(method.displayName())).append("\")\n");
            if (methodMetadata.criticalRegression()) {
                builder.append("    @CriticalRegression\n");
            } else if (methodMetadata.regression()) {
                builder.append("    @Regression\n");
            }
            if (methodMetadata.manual()) {
                builder.append("    @ManualTest\n");
            }
            builder.append("    void ").append(method.generatedName()).append("() {\n");
            renderOriginalIdentity(builder, method);

            renderMetadataLabels(builder, mergeMetadata(classMetadata, methodMetadata));
            builder.append("        Allure.label(\"testFramework\", \"platform-v-at-framework\");\n");

            builder.append("        Allure.step(\"Bypass registration: original functional test is not executed\", () -> {\n")
                    .append("            Allure.addAttachment(\"Bypass mode\", \"text/plain\", \"")
                    .append(escapeJavaString("This is a technical passed result generated by ./gradlew bypassTests for TMS/TestOps registration. Original source: "
                            + generatedClass.sourceFile() + ", method: " + method.originalName()))
                    .append("\");\n")
                    .append("        });\n")
                    .append("    }\n\n");
        }

        builder.append("}\n");
        return builder.toString();
    }

    private static boolean hasCriticalRegression(GeneratedClass generatedClass) {
        if (generatedClass.classMetadata().criticalRegression()) {
            return true;
        }
        return generatedClass.methods().stream().anyMatch(method -> method.metadata().criticalRegression());
    }

    private static boolean hasRegression(GeneratedClass generatedClass) {
        if (generatedClass.classMetadata().regression() && !generatedClass.classMetadata().criticalRegression()) {
            return true;
        }
        return generatedClass.methods().stream().anyMatch(method ->
                method.metadata().regression() && !method.metadata().criticalRegression());
    }

    private static boolean hasManual(GeneratedClass generatedClass) {
        if (generatedClass.classMetadata().manual()) {
            return true;
        }
        return generatedClass.methods().stream().anyMatch(method -> method.metadata().manual());
    }

    private static Set<String> loadExcludedTests(Path file) throws IOException {
        if (!Files.exists(file)) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                result.add(line);
            }
        }
        return result;
    }

    private static Metadata mergeMetadata(Metadata classMetadata, Metadata methodMetadata) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.addAll(classMetadata.tags());
        tags.addAll(methodMetadata.tags());

        LinkedHashMap<String, LinkedHashSet<String>> labels = new LinkedHashMap<>();
        mergeLabels(labels, classMetadata.labels());
        mergeLabels(labels, methodMetadata.labels());

        List<GeneratedLink> links = new ArrayList<>();
        links.addAll(classMetadata.links());
        links.addAll(methodMetadata.links());

        boolean critical = classMetadata.criticalRegression() || methodMetadata.criticalRegression();
        return new Metadata(tags, labels, links,
                critical,
                critical || classMetadata.regression() || methodMetadata.regression(),
                classMetadata.manual() || methodMetadata.manual(),
                methodMetadata.displayName().or(() -> classMetadata.displayName()));
    }

    private static void mergeLabels(LinkedHashMap<String, LinkedHashSet<String>> target,
                                    LinkedHashMap<String, LinkedHashSet<String>> source) {
        source.forEach((name, values) -> values.forEach(value -> addLabel(target, name, value)));
    }

    private static void renderMetadataLabels(StringBuilder builder, Metadata metadata) {
        for (String tag : metadata.tags()) {
            builder.append("        Allure.label(\"tag\", \"").append(escapeJavaString(tag)).append("\");\n");
        }
        metadata.labels().forEach((name, values) -> values.forEach(value -> builder
                .append("        Allure.label(\"").append(escapeJavaString(name)).append("\", \"")
                .append(escapeJavaString(value)).append("\");\n")));
        metadata.links().forEach(link -> builder
                .append("        Allure.link(\"").append(escapeJavaString(link.name())).append("\", \"")
                .append(escapeJavaString(link.type())).append("\", \"")
                .append(escapeJavaString(link.url())).append("\");\n"));
    }

    private static void renderOriginalIdentity(StringBuilder builder, GeneratedMethod method) {
        builder.append("        Allure.getLifecycle().updateTestCase(testResult -> {\n")
                .append("            testResult.setFullName(\"").append(escapeJavaString(method.originalFullName())).append("\");\n")
                .append("            testResult.setTestCaseId(\"").append(escapeJavaString(method.originalTestCaseId())).append("\");\n")
                .append("            testResult.setHistoryId(\"").append(escapeJavaString(method.originalHistoryId())).append("\");\n")
                .append("            testResult.getLabels().removeIf(label -> \"junit.platform.uniqueid\".equals(label.getName()));\n")
                .append("            testResult.getLabels().add(new Label().setName(\"junit.platform.uniqueid\").setValue(\"")
                .append(escapeJavaString(method.originalTestCaseId())).append("\"));\n")
                .append("        });\n");
    }

    private static ImportContext extractImports(String source) {
        LinkedHashMap<String, String> explicitImports = new LinkedHashMap<>();
        List<String> wildcardImports = new ArrayList<>();
        Matcher matcher = IMPORT_PATTERN.matcher(source);
        while (matcher.find()) {
            String importName = matcher.group(1);
            if (matcher.group(2) == null) {
                int lastDot = importName.lastIndexOf('.');
                if (lastDot > 0) {
                    explicitImports.put(importName.substring(lastDot + 1), importName);
                }
            } else {
                wildcardImports.add(importName);
            }
        }
        return new ImportContext(explicitImports, wildcardImports);
    }

    private static String resolveParameterSignature(String originalParameters,
                                                    String packageName,
                                                    ImportContext imports) {
        if (originalParameters == null || originalParameters.isBlank()) {
            return "";
        }
        List<String> parameterTypes = new ArrayList<>();
        for (String parameter : splitTopLevel(originalParameters, ',')) {
            String type = extractParameterType(parameter);
            if (!type.isBlank()) {
                parameterTypes.add(resolveType(type, packageName, imports));
            }
        }
        return String.join(",", parameterTypes);
    }

    private static List<String> splitTopLevel(String source, char separator) {
        List<String> result = new ArrayList<>();
        int genericDepth = 0;
        int parenthesisDepth = 0;
        boolean inString = false;
        boolean escaped = false;
        int segmentStart = 0;

        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '<') {
                genericDepth++;
            } else if (current == '>') {
                genericDepth = Math.max(0, genericDepth - 1);
            } else if (current == '(') {
                parenthesisDepth++;
            } else if (current == ')') {
                parenthesisDepth = Math.max(0, parenthesisDepth - 1);
            } else if (current == separator && genericDepth == 0 && parenthesisDepth == 0) {
                result.add(source.substring(segmentStart, index).trim());
                segmentStart = index + 1;
            }
        }

        result.add(source.substring(segmentStart).trim());
        return result;
    }

    private static String extractParameterType(String parameter) {
        String normalized = removeAnnotations(parameter)
                .replace("...", "[]")
                .replaceAll("\\bfinal\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            return "";
        }
        int lastSpace = normalized.lastIndexOf(' ');
        return lastSpace > 0 ? normalized.substring(0, lastSpace).trim() : normalized;
    }

    private static String removeAnnotations(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '@') {
                result.append(current);
                continue;
            }

            index++;
            while (index < value.length()) {
                char annotationChar = value.charAt(index);
                if (Character.isJavaIdentifierPart(annotationChar) || annotationChar == '.') {
                    index++;
                } else {
                    break;
                }
            }
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            if (index < value.length() && value.charAt(index) == '(') {
                int closeParenthesis = findMatchingParenthesis(value, index);
                index = closeParenthesis >= 0 ? closeParenthesis : index;
            } else {
                index--;
            }
        }
        return result.toString();
    }

    private static String resolveType(String rawType, String packageName, ImportContext imports) {
        String erased = eraseGenericType(rawType)
                .replaceAll("\\s+", "")
                .trim();
        StringBuilder arraySuffix = new StringBuilder();
        while (erased.endsWith("[]")) {
            arraySuffix.append("[]");
            erased = erased.substring(0, erased.length() - 2);
        }
        if (PRIMITIVE_TYPES.contains(erased) || erased.contains(".")) {
            return erased + arraySuffix;
        }
        String imported = imports.explicitImports().get(erased);
        if (imported != null) {
            return imported + arraySuffix;
        }
        if (JAVA_LANG_TYPES.contains(erased)) {
            return "java.lang." + erased + arraySuffix;
        }
        for (String wildcardImport : imports.wildcardImports()) {
            if (wildcardImport.startsWith("java.")) {
                return wildcardImport + "." + erased + arraySuffix;
            }
        }
        return packageName + "." + erased + arraySuffix;
    }

    private static String eraseGenericType(String type) {
        StringBuilder result = new StringBuilder(type.length());
        int depth = 0;
        for (int index = 0; index < type.length(); index++) {
            char current = type.charAt(index);
            if (current == '<') {
                depth++;
            } else if (current == '>') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0) {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static String md5Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                result.append(String.format("%02x", current & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 algorithm is not available", exception);
        }
    }

    private static String findAnnotationBlockBefore(String source, int index) {
        int currentLineStart = source.lastIndexOf('\n', Math.max(0, index - 1)) + 1;
        int scanEnd = currentLineStart;
        int scanStart = currentLineStart;

        while (scanStart > 0) {
            int previousLineEnd = scanStart - 1;
            int previousLineStart = source.lastIndexOf('\n', Math.max(0, previousLineEnd - 1)) + 1;
            String line = source.substring(previousLineStart, previousLineEnd).trim();

            if (line.isEmpty()) {
                scanStart = previousLineStart;
                continue;
            }
            if (line.startsWith("@") || line.startsWith("}") || line.startsWith(")")
                    || line.startsWith("\"") || line.startsWith("+") || line.startsWith(",")) {
                scanStart = previousLineStart;
                continue;
            }
            break;
        }

        String candidate = source.substring(scanStart, scanEnd);
        int firstAnnotation = candidate.indexOf('@');
        if (firstAnnotation < 0) {
            return "";
        }
        return candidate.substring(firstAnnotation);
    }

    private static Optional<String> extractDisplayName(String annotationBlock) {
        int annotationIndex = annotationBlock.indexOf("@DisplayName");
        if (annotationIndex < 0) {
            annotationIndex = annotationBlock.indexOf("@org.junit.jupiter.api.DisplayName");
        }
        if (annotationIndex < 0) {
            return Optional.empty();
        }

        int openParenthesis = annotationBlock.indexOf('(', annotationIndex);
        if (openParenthesis < 0) {
            return Optional.empty();
        }

        int closeParenthesis = findMatchingParenthesis(annotationBlock, openParenthesis);
        if (closeParenthesis < 0) {
            return Optional.empty();
        }

        String arguments = annotationBlock.substring(openParenthesis + 1, closeParenthesis);
        Matcher stringMatcher = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(arguments);
        StringBuilder displayName = new StringBuilder();
        while (stringMatcher.find()) {
            displayName.append(unescapeJavaString(stringMatcher.group(1)));
        }

        if (displayName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(displayName.toString());
    }

    private static int findMatchingParenthesis(String source, int openParenthesis) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = openParenthesis; index < source.length(); index++) {
            char current = source.charAt(index);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
            } else if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static Optional<String> findFirst(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private static String stripComments(String source) {
        StringBuilder result = new StringBuilder(source.length());
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;

        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                    result.append(current);
                }
                continue;
            }

            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    index++;
                } else if (current == '\n' || current == '\r') {
                    result.append(current);
                }
                continue;
            }

            if (inString) {
                result.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (inChar) {
                result.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '\'') {
                    inChar = false;
                }
                continue;
            }

            if (current == '/' && next == '/') {
                inLineComment = true;
                index++;
                continue;
            }

            if (current == '/' && next == '*') {
                inBlockComment = true;
                index++;
                continue;
            }

            if (current == '"') {
                inString = true;
            } else if (current == '\'') {
                inChar = true;
            }
            result.append(current);
        }

        return result.toString();
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException exception) {
                            throw new RuntimeException("Failed to delete " + path, exception);
                        }
                    });
        }
    }

    private static String escapeJavaString(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJavaString(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private record ImportContext(Map<String, String> explicitImports, List<String> wildcardImports) {
    }

    private record GeneratedClass(String packageName, String className, String sourceFile, Metadata classMetadata,
                                  List<GeneratedMethod> methods) {
    }

    private record GeneratedMethod(String originalName,
                                   String generatedName,
                                   String displayName,
                                   Metadata metadata,
                                   String originalFullName,
                                   String originalTestCaseId,
                                   String originalHistoryId) {
    }

    private record Metadata(LinkedHashSet<String> tags,
                            LinkedHashMap<String, LinkedHashSet<String>> labels,
                            List<GeneratedLink> links,
                            boolean criticalRegression,
                            boolean regression,
                            boolean manual,
                            Optional<String> displayName) {
    }

    private record GeneratedLink(String name, String type, String url) {
    }

    private static final class GenerationStats {
        private int classes;
        private int methods;
    }
}
