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

# Limit JVM heap so Render 512MB free tier doesn't OOM
ENV JAVA_OPTS_APPEND="-Xms64m -Xmx380m"

# Auto-import realm on startup (skip-if-exists so it won't overwrite on redeploy)
COPY fitness-app-realm.json /opt/keycloak/data/import/fitness-app-realm.json

# --http-host=0.0.0.0 required for Render — default binds to localhost only
ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized", "--http-host=0.0.0.0", "--import-realm"]
