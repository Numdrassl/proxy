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
    // Latest version can be found here:
    // Release: https://maven.hytale.com/release/com/hypixel/hytale/Server/maven-metadata.xml
    // Pre-Release: https://maven.hytale.com/pre-release/com/hypixel/hytale/Server/maven-metadata.xml
    compileOnly("com.hypixel.hytale:Server:2026.01.24-6e2d4fc36")

    implementation("org.javassist:javassist:3.30.2-GA")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from({
        configurations.runtimeClasspath.get().map { file ->
            if (file.isDirectory) file else zipTree(file)
        }
    })
}

tasks.test {
    useJUnitPlatform()
}
