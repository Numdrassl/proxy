plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "me.internalizable.numdrassl"
version = findProperty("version") ?: "1.0-SNAPSHOT"

// Check if this is a release version (no -SNAPSHOT suffix)
val isRelease = !version.toString().endsWith("-SNAPSHOT")

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Configure publishing for all subprojects that produce publishable artifacts
    afterEvaluate {
        if (plugins.hasPlugin("maven-publish")) {
            configureMavenPublishing()
        }
    }
}

// Extension function to configure Maven publishing consistently
fun Project.configureMavenPublishing() {
    configure<PublishingExtension> {
        repositories {
            maven {
                name = "OSSRH"
                val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                url = if (version.toString().endsWith("-SNAPSHOT")) snapshotsUrl else releasesUrl

                credentials {
                    username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                    password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
                }
            }

            // Local Maven repository for testing
            maven {
                name = "Local"
                url = uri(rootProject.layout.buildDirectory.dir("local-repo"))
            }
        }
    }

    // Configure signing for release versions
    configure<SigningExtension> {
        val signingKeyId = findProperty("signing.keyId") as String? ?: System.getenv("SIGNING_KEY_ID")
        val signingKey = findProperty("signing.key") as String? ?: System.getenv("SIGNING_KEY")
        val signingPassword = findProperty("signing.password") as String? ?: System.getenv("SIGNING_PASSWORD")

        if (signingKey != null && signingPassword != null) {
            useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        }

        // Only sign release versions
        setRequired { !version.toString().endsWith("-SNAPSHOT") && gradle.taskGraph.hasTask("publish") }

        sign(extensions.getByType<PublishingExtension>().publications)
    }
}

