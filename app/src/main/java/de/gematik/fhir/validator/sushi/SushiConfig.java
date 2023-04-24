package de.gematik.fhir.validator.sushi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import de.gematik.fhir.validator.ValidatorException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public class SushiConfig {
    class SushiDependency {
        public final String packageName;
        public final String packageVersion;

        public SushiDependency(String packageName, String packageVersion) {
            this.packageName = packageName;
            this.packageVersion = packageVersion;
        }
    }

    final private JsonNode rootNode;

    public SushiConfig(Path sushiProjectDir) throws IOException, ValidatorException {
        Path sushiConfigPath = sushiProjectDir.resolve("sushi-config.yaml");
        if (!sushiConfigPath.toFile().exists()) {
            throw new ValidatorException(String.format("File 'sushi-config.yaml' not found in %s", sushiProjectDir));
        }

        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        this.rootNode = om.readTree(sushiConfigPath.toFile());
    }

    public Collection<SushiDependency> getDependencies() {
        JsonNode dependenciesNode = rootNode.get("dependencies");
        if (dependenciesNode == null) {
            return Collections.emptyList();
        }
        Iterator<String> packageNames = dependenciesNode.fieldNames();
        ArrayList<SushiDependency> dependencies = new ArrayList<>();
        while (packageNames.hasNext()) {
            String packageName = packageNames.next();
            JsonNode dependencyNode = dependenciesNode.get(packageName);
            if (dependencyNode.isTextual()) {
               dependencies.add(new SushiDependency(packageName, dependencyNode.textValue()));
            } else {
                dependencies.add(new SushiDependency(packageName, dependencyNode.get("version").textValue()));
            }
        }

        return dependencies;
    }
}
