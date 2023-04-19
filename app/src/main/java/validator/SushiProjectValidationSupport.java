package validator;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SushiProjectValidationSupport extends PrePopulatedValidationSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(SushiProjectValidationSupport.class);

    @Nonnull private Path sushiProjectDirectory;
    @Nonnull private Path sushiOutputDirectory;
    @Nonnull private Path packagesCacheDirectory;

    public SushiProjectValidationSupport(@Nonnull FhirContext ctx, Path sushiProjectDirectory) throws IOException {
        this(
                ctx,
                sushiProjectDirectory,
                sushiProjectDirectory.resolve("fsh-generated"),
                Paths.get(System.getProperty("user.home"), ".fhir", "packages")
        );
    }

    public SushiProjectValidationSupport(
            @Nonnull FhirContext ctx,
            @Nonnull Path sushiProjectDirectory,
            @Nonnull Path sushiOutputDirectory,
            @Nonnull Path packagesCacheDirectory) throws IOException {
        super(ctx);
        this.sushiProjectDirectory = sushiProjectDirectory;
        this.packagesCacheDirectory = packagesCacheDirectory;
        this.sushiOutputDirectory = sushiOutputDirectory;
        loadSushiGeneratedResources();
        loadDependencies();
    }

    private void loadSushiGeneratedResources() throws IOException {
        try (Stream<Path> paths = Files.walk(sushiOutputDirectory)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(file -> file.toString().toLowerCase().endsWith(".json") )
                    .forEach(this::loadFile);
        }
    }

    private void loadDependencies() throws IOException {
        Path packageJsonFile = findPackageJsonFile(sushiProjectDirectory);

        if (packageJsonFile == null) {
            throw new FileNotFoundException(String.format("File package.json not found in folder (or it's parents): %s", sushiProjectDirectory));
        }

        resolveDependencies(packageJsonFile);
    }

    private Path findPackageJsonFile(Path dir) {
        Path file = dir.resolve("package.json");
        if (Files.exists(file)) {
            return file;
        } else if (dir.getParent() != null) {
            return findPackageJsonFile(dir.getParent());
        } else {
            return null;
        }
    }

    private void resolveDependencies(Path packageJsonFile) throws IOException {
        // Create an ObjectMapper object
        ObjectMapper mapper = new ObjectMapper();

        JsonNode packageJson = mapper.readTree(packageJsonFile.toFile());
        JsonNode dependencies = packageJson.get("dependencies");

        if (dependencies == null) {
            // we reached the end of dependency tree
            return;
        }

        Iterator<String> packageNames = dependencies.fieldNames();
        while (packageNames.hasNext()) {
            String packageName = packageNames.next();
            String packageVersion = dependencies.get(packageName).asText();
            loadPackage(packageName, packageVersion);
        }
    }

    private void loadPackage(String packageName, String packageVersion) throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Loading package {} version {}", packageName, packageVersion);
        }
        Path packagePath = this.packagesCacheDirectory.resolve(String.format("%s#%s", packageName, packageVersion));
        NpmPackage npmPackage = NpmPackage.fromFolder(packagePath.toString());
        NpmPackage.NpmPackageFolder packageFolder = npmPackage.getFolders().get("package");

        for (String resourceFile: npmPackage.getTypes().values().stream().flatMap(List::stream).collect(Collectors.toSet())) {
            String input = new String(packageFolder.fetchFile(resourceFile), StandardCharsets.UTF_8);
            IBaseResource resource = getFhirContext().newJsonParser().parseResource(input);
            super.addResource(resource);
        }
        // resolve the sub-dependencies
        resolveDependencies(packagePath.resolve("package").resolve("package.json"));
    }

    private void loadFile(Path resourceJsonFile) {
        String contents = null;
        try {
            contents = Files.readString(resourceJsonFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        IBaseResource resource = myCtx.newJsonParser().parseResource(contents);
        this.addResource(resource);
    }


}
