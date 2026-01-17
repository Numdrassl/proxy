plugins {
    `java-library`
    `maven-publish`
}

group = "me.internalizable.numdrassl"
version = rootProject.version

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25

    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/HytaleServer.jar"))

    // Common module with SecretMessageUtil - will be bundled into JAR
    implementation(project(":common"))

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
}

tasks.jar {
    dependsOn(":common:jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Include common module classes in the JAR
    from(project(":common").sourceSets.main.get().output)

    from("src/main/resources") {
        include("manifest.json")
    }
}

tasks.test {
    useJUnitPlatform()
}
