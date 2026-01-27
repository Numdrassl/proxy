plugins {
    `java-library`
    `maven-publish`
}

group = "me.internalizable.numdrassl"
version = rootProject.version

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    // No javadoc/sources jars for bridge plugin - it's a runtime artifact only
}

repositories {
    mavenCentral()
    maven {
        name = "hytale-release"
        url = uri("https://maven.hytale.com/release")
    }
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:2026.01.24-6e2d4fc36")

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
