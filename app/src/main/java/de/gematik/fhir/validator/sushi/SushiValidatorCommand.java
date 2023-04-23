package de.gematik.fhir.validator.sushi;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.*;
import de.gematik.fhir.validator.ValidatorException;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandLine.Command(
        name="sushi",
        mixinStandardHelpOptions = true,
        description = "Validates the generated resources in SUSHI project"
)
public class SushiValidatorCommand implements Callable<Integer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SushiValidatorCommand.class);

    @CommandLine.Option(names = {"-p", "--project-dir"}, description = "SUSHI project directory")
    private final Path sushiProjectDir = Paths.get(".");

    @CommandLine.Option(names = {"-g", "--generated-dir"}, description = "Path to generated resources")
    private Path sushiGeneratedResourcesDir = null;

    @CommandLine.Parameters(description = "Specific files to validate")
    private List<Path> filesToValidate = null;

    @CommandLine.Option(names = {"-i", "--ignore-ids"}, description = "Specify message ids to be ignored")
    private final List<String> ignoreMessageIds = List.of("SD_TYPE_NOT_LOCAL");

    @Override
    public Integer call() throws Exception {
        if (!sushiProjectDir.toFile().exists()) {
            throw new ValidatorException(String.format("SUSHI Project directory does not exists: %s", sushiProjectDir));
        }

        if (sushiGeneratedResourcesDir == null) {
            sushiGeneratedResourcesDir = sushiProjectDir.resolve("fsh-generated");
        }

        if (!sushiGeneratedResourcesDir.toFile().exists()) {
            throw new ValidatorException(String.format("FSH generated resources directory not found: %s", sushiGeneratedResourcesDir));
        }
        FhirContext ctx = FhirContext.forR4();

        // Configure Sushi project validation support
        SushiProjectValidationSupport sushiProjectValidationSupport = new SushiProjectValidationSupport(ctx, Paths.get("/Users/serg/Development/gematik/api-vzd/src/fhir"));

        ValidationSupportChain validationSupportChain = new ValidationSupportChain(
                sushiProjectValidationSupport,
                new CommonCodeSystemsTerminologyService(ctx),
                new InMemoryTerminologyServerValidationSupport(ctx),
                new DefaultProfileValidationSupport(ctx),
                new SnapshotGeneratingValidationSupport(ctx)
        );

        // Ask the context for a validator
        FhirValidator validator = ctx.newValidator();

        // Create a validation module and register it
        IValidatorModule module = new FhirInstanceValidator(validationSupportChain);
        validator.registerValidatorModule(module);

        List<FileValidationResult> validationResults;

        if (filesToValidate != null) {
            validationResults = filesToValidate.stream()
                    .map(path -> {
                        return validateResource(path, validator);
                    }).collect(Collectors.toList());
        } else {
            try (Stream<Path> paths = Files.walk(sushiGeneratedResourcesDir.resolve("resources"))) {
                validationResults = paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .map(path -> {
                            return validateResource(path, validator);
                        }).collect(Collectors.toList());
            }
        }

        int errorsCount = 0;
        int warningsCount = 0;
        for (FileValidationResult fileValidationResult: validationResults) {
            for (SingleValidationMessage message : fileValidationResult.validationResult.getMessages()) {
                if (message.getMessageId() != null && ignoreMessageIds.contains(message.getMessageId())) {
                    continue;
                }
                System.out.printf("[%s:(%d,%d): %S %s: %s]%n",
                        fileValidationResult.filePath,
                        message.getLocationLine(),
                        message.getLocationCol(),
                        message.getSeverity(),
                        message.getMessageId(),
                        message.getMessage()
                );
                if (message.getSeverity() == ResultSeverityEnum.ERROR) {
                    errorsCount++;
                } else if (message.getSeverity() == ResultSeverityEnum.WARNING) {
                    warningsCount++;
                }

            }
        }

        System.out.printf("Validated %d files. %d Errors. %d Warnings%n", validationResults.size(), errorsCount, warningsCount);

        return 0;
    }

    private class FileValidationResult {
        private Path filePath;
        private ValidationResult validationResult;

        public FileValidationResult(Path filePath, ValidationResult validationResult) {
            this.filePath = filePath;
            this.validationResult = validationResult;
        }
    }

    private FileValidationResult validateResource(Path jsonFile, FhirValidator validator) {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Validating: "+jsonFile);
            }
            String content = Files.readString(jsonFile);

            return new FileValidationResult(jsonFile, validator.validateWithResult(content));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
