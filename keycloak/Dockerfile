# ── Keycloak with auto realm import ───────────────────────────────────────────
# Build: docker build -t fitness-keycloak .
# This image imports fitness-app-realm.json on every fresh start.
# If KC_DB already has the realm, Keycloak skips the import automatically.
FROM quay.io/keycloak/keycloak:26.5.2

# Copy the exported realm JSON into the import folder
# Keycloak reads everything in /opt/keycloak/data/import/ on startup
# when started with --import-realm flag
COPY fitness-app-realm.json /opt/keycloak/data/import/fitness-app-realm.json

# start-dev = development mode (no HTTPS required, fine for Render free tier)
# --import-realm = import JSON files from the import directory on first start
ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start-dev", "--import-realm"]
