#!/usr/bin/env bash
set -euo pipefail

echo "[post-create] Versions installées :"
python3 --version       || true
pip3 --version          || true
terraform --version     || true
make --version | head -n1 || true
aws --version           || true
kafkacat -V 2>&1 | head -n1 || true
java -version           || true
mvn -version | head -n1 || true
gradle -v | head -n5    || true

# Charger nvm
export NVM_DIR="$HOME/.nvm"
# shellcheck disable=SC1091
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"

# Installer Node LTS si absent (fait ici car le réseau du build peut échouer)
if command -v nvm >/dev/null 2>&1; then
  if ! nvm ls default >/dev/null 2>&1; then
    echo "[post-create] Installation de Node LTS via nvm..."
    nvm install --lts || echo "[post-create] WARN : échec nvm install, relance 'nvm install --lts' manuellement."
    nvm alias default 'lts/*' || true
  fi
  echo "nvm $(nvm --version)"
  command -v node && node --version || true
  command -v npm  && npm  --version || true
fi

# Rendre gradlew exécutable si présent
if [ -f "./gradlew" ]; then
  chmod +x ./gradlew
fi

cat <<'EOF'

[post-create] Prêt.

  ./gradlew bootRun                      # mode dev, logs attachés
  ./gradlew bootJar && \
    java -jar build/libs/*-0.1.0.jar     # jar packagé

API Spring Boot sur http://localhost:8080 (port forwardé par VS Code).

EOF
