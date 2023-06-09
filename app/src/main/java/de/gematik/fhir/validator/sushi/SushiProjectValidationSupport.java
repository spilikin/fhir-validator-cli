package de.gematik.fhir.validator.sushi;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.fhir.validator.ValidatorException;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pre-configured HAPI {@link ca.uhn.fhir.context.support.IValidationSupport}
 * for validating generated FHIR Resources in FSH/SUSHI projects.
 */
public class SushiProjectValidationSupport extends PrePopulatedValidationSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(SushiProjectValidationSupport.class);

    @Nonnull private final Path sushiProjectDirectory;
    @Nonnull private final Path sushiOutputDirectory;
    @Nonnull private final Path packagesCacheDirectory;

    public SushiProjectValidationSupport(@Nonnull FhirContext ctx, Path sushiProjectDirectory) throws ValidatorException, IOException {
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
            @Nonnull Path packagesCacheDirectory) throws ValidatorException, IOException {
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
                    .forEach(this::addRecourceFromFile);
        }
    }

    private void loadDependencies() throws ValidatorException, IOException {
        SushiConfig sushiConfig = new SushiConfig(this.sushiProjectDirectory);
        Collection<SushiConfig.SushiDependency> sushiDependencies = sushiConfig.getDependencies();

        for (SushiConfig.SushiDependency sushiDependency: sushiDependencies ) {
            loadPackage(sushiDependency.packageName, sushiDependency.packageVersion);
        }
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

    private void resolveDependencies(Path packageJsonFile) throws ValidatorException, IOException {
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

    private void loadPackage(String packageName, String packageVersion) throws ValidatorException, IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Loading package {} version {}", packageName, packageVersion);
        }
        Path packagePath = this.packagesCacheDirectory.resolve(String.format("%s#%s", packageName, packageVersion));
        if (!packagePath.toFile().exists()) {
            throw new ValidatorException(String.format("Package %s version %s is not installed.", packageName, packageVersion));
        }
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

    private void addRecourceFromFile(Path resourceJsonFile) {
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
