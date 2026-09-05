# The jar is built on the runner and only copied in here. A Raspberry Pi has better things
# to do than compile Spring Boot, and a build stage would have to run under emulation for
# the arm64 image.
FROM eclipse-temurin:25-jre

# The glob takes the one jar the build produced; the .jar.original beside it is not matched.
COPY --chown=1000:1000 target/greenroom-*.jar /app/greenroom.jar

# A numeric user, so the image needs no RUN at all — and without a RUN, buildx assembles
# both architectures from their base images without emulating anything. What the
# application keeps is in Postgres; the one thing it writes is the warning log, and only
# where LOGGING_FILE_NAME names a mounted directory that user 1000 may write to.
USER 1000:1000

# The application, and the actuator under /mgmt.
EXPOSE 8383 8382

ENTRYPOINT ["java", "-jar", "/app/greenroom.jar"]
