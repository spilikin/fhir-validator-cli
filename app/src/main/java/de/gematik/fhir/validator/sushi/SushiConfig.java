package de.gematik.fhir.validator.sushi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

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

    public SushiConfig(File configFile) throws IOException {
        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        this.rootNode = om.readTree(configFile);
    }

    public Collection<SushiDependency> getDependencies() {
        return Collections.emptyList();
    }
}
