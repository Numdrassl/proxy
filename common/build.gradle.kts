plugins {
    `java-library`
}

group = "me.internalizable.numdrassl"
version = rootProject.version

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
}

dependencies {
    // Netty for ByteBuf
    api("io.netty:netty-buffer:4.1.100.Final")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

