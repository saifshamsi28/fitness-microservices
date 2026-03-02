# ── Keycloak with pre-built augmentation + auto realm import ──────────────────
# Stage 1: run heavy augmentation at BUILD time (not runtime) to save RAM
FROM quay.io/keycloak/keycloak:26.5.2 AS builder

ENV KC_DB=postgres
ENV KC_HTTP_ENABLED=true
ENV KC_HOSTNAME_STRICT=false

# Pre-build/augment Keycloak — this is the memory-heavy step, done once here
RUN /opt/keycloak/bin/kc.sh build

# Stage 2: lean runtime image
FROM quay.io/keycloak/keycloak:26.5.2

# Copy the pre-augmented build artifacts
COPY --from=builder /opt/keycloak/ /opt/keycloak/

# Copy the exported realm JSON — auto-imported on first start
COPY fitness-app-realm.json /opt/keycloak/data/import/fitness-app-realm.json

# Limit JVM heap so Render 512MB free tier doesn't OOM
ENV JAVA_OPTS_APPEND="-Xms64m -Xmx380m"

# --optimized = skip re-augmentation at runtime (already done above)
ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start-dev", "--optimized", "--import-realm"]
