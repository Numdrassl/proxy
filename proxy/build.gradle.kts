plugins {
    `java-library`
    id("application")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

val nettyVersion = "4.1.114.Final"
val nettyQuicVersion = "0.0.66.Final"

dependencies {
    // Numdrassl API
    api(project(":api"))

    // Common module with SecretMessageUtil
    implementation(project(":common"))

    // Netty Core
    implementation("io.netty:netty-common:$nettyVersion")
    implementation("io.netty:netty-buffer:$nettyVersion")
    implementation("io.netty:netty-codec:$nettyVersion")
    implementation("io.netty:netty-handler:$nettyVersion")
    implementation("io.netty:netty-transport:$nettyVersion")
    implementation("io.netty:netty-transport-native-epoll:$nettyVersion:linux-x86_64")

    // Netty QUIC (incubator)
    implementation("io.netty.incubator:netty-incubator-codec-native-quic:$nettyQuicVersion:windows-x86_64")
    implementation("io.netty.incubator:netty-incubator-codec-native-quic:$nettyQuicVersion:linux-x86_64")
    implementation("io.netty.incubator:netty-incubator-codec-native-quic:$nettyQuicVersion:osx-x86_64")
    implementation("io.netty.incubator:netty-incubator-codec-native-quic:$nettyQuicVersion:osx-aarch_64")
    implementation("io.netty.incubator:netty-incubator-codec-classes-quic:$nettyQuicVersion")

    // Zstd compression (used by Hytale protocol)
    implementation("com.github.luben:zstd-jni:1.5.5-11")

    // BouncyCastle for certificate generation
    implementation("org.bouncycastle:bcpkix-jdk18on:1.77")
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")

    // JSR305 annotations (Nullable, Nonnull)
    implementation("com.google.code.findbugs:jsr305:3.0.2")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Guava for collections utilities
    implementation("com.google.guava:guava:32.1.3-jre")

    // Configuration (YAML)
    implementation("org.yaml:snakeyaml:2.2")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("me.internalizable.numdrassl.Main")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:-unchecked", "-Xlint:-deprecation"))
}

tasks.jar {
    dependsOn(":api:jar", ":common:jar")
    manifest {
        attributes["Main-Class"] = "me.internalizable.numdrassl.Main"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/MANIFEST.MF")
        exclude("META-INF/BCKEY.SF")
        exclude("META-INF/BCKEY.DSA")
        exclude("META-INF/BC1024KE.SF")
        exclude("META-INF/BC1024KE.DSA")
        exclude("META-INF/BC2048KE.SF")
        exclude("META-INF/BC2048KE.DSA")
    }
}

