plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

val hapiVersion: String by project

dependencies {
    implementation("ca.uhn.hapi.fhir:hapi-fhir-base:${hapiVersion}")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-validation:${hapiVersion}")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r4:${hapiVersion}")
    //implementation("ca.uhn.hapi.fhir:hapi-fhir-validation-resources-r4:${hapiVersion}")
    implementation("ch.qos.logback:logback-classic:1.4.6")
    // Pico CLI
    implementation("info.picocli:picocli:4.7.3")
    // Jackson YAML
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.1")
    // Use JUnit test framework.
    testImplementation("junit:junit:4.13.2")

    // This dependency is used by the application.
    implementation("com.google.guava:guava:31.1-jre")


    // Use JUnit test framework.
    testImplementation("junit:junit:4.13.2")
    // This dependency is used by the application.
    implementation("com.google.guava:guava:31.1-jre")
}

application {
    // Define the main class for the application.
    mainClass.set("de.gematik.fhir.validator.App")
}
