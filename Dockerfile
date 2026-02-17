FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build

COPY . .
RUN ./gradlew --no-daemon :proxy:jar
RUN cp proxy/build/libs/*.jar /build/numdrassl.jar

FROM eclipse-temurin:25-jre AS runtime

RUN usermod -l numdrassl -d /home/numdrassl -m ubuntu && \
    groupmod -n numdrassl ubuntu

COPY --from=builder --chown=numdrassl:numdrassl /build/numdrassl.jar /home/numdrassl/numdrassl.jar
RUN mkdir -p /home/numdrassl/proxy && chown numdrassl:numdrassl /home/numdrassl/proxy

USER numdrassl
WORKDIR /home/numdrassl/proxy

ENTRYPOINT ["java", "-jar", "/home/numdrassl/numdrassl.jar"]
