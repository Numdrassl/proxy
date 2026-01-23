plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "me.internalizable.numdrassl"
version = "1.0.1"  // API version - independent of proxy version

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // Annotations
    compileOnlyApi("com.google.code.findbugs:jsr305:3.0.2")

    // Logging (API only)
    api("org.slf4j:slf4j-api:2.0.9")

    // Gson for JSON handling
    api("com.google.code.gson:gson:2.10.1")

    // Guava for utilities
    api("com.google.guava:guava:32.1.3-jre")

    // Event system annotations
    compileOnlyApi("com.google.auto.service:auto-service-annotations:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
}

tasks {
    jar {
        manifest {
            attributes["Automatic-Module-Name"] = "me.internalizable.numdrassl.api"
        }
    }

    withType<Javadoc> {
        val o = options as StandardJavadocDocletOptions
        o.encoding = "UTF-8"
        o.source = "17"
        o.use()
        o.addStringOption("Xdoclint:none", "-quiet")
    }

    withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xlint:-unchecked", "-Xlint:-deprecation"))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            artifactId = "numdrassl-api"

            pom {
                name.set("Numdrassl API")
                description.set("Plugin API for Numdrassl, a high-performance QUIC proxy for Hytale servers supporting cross-proxy messaging, events, and commands")
                url.set("https://github.com/Numdrassl/proxy")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("internalizable")
                        name.set("Internalizable")
                    }
                }

                scm {
                    url.set("https://github.com/Numdrassl/proxy")
                    connection.set("scm:git:git://github.com/Numdrassl/proxy.git")
                    developerConnection.set("scm:git:ssh://git@github.com/Numdrassl/proxy.git")
                }
            }
        }
    }

    repositories {
        maven {
            name = "Local"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

signing {
    // Use GPG command-line tool with key ID from gradle.properties
    // Set signing.gnupg.keyName in ~/.gradle/gradle.properties
    useGpgCmd()
    sign(publishing.publications["maven"])
}

// Task to create a bundle ZIP for Central Portal upload
tasks.register<Zip>("createCentralBundle") {
    dependsOn("publishMavenPublicationToLocalRepository")

    archiveFileName.set("central-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("central"))

    from(layout.buildDirectory.dir("staging-deploy"))
}

