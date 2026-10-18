# Ubuntu LTS (24.04 Noble) comme base
FROM ubuntu:24.04

# Arguments proxy (passés depuis devcontainer.json). Vides par défaut :
# si rien n'est défini côté hôte, aucune config proxy n'est appliquée.
ARG HTTP_PROXY=""
ARG HTTPS_PROXY=""
ARG NO_PROXY=""

# Versions des outils (modifiables au build)
ARG GRADLE_VERSION=8.13
ARG NVM_VERSION=v0.40.1
ARG NODE_VERSION=--lts

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

# Config APT via proxy uniquement si une valeur est fournie
RUN if [ -n "$HTTP_PROXY" ]; then \
      { \
        echo "Acquire::http::Proxy \"${HTTP_PROXY}\";" ; \
        [ -n "$HTTPS_PROXY" ] && echo "Acquire::https::Proxy \"${HTTPS_PROXY}\";" ; \
      } > /etc/apt/apt.conf.d/95proxies ; \
    fi

# Paquets de base + outils demandés (dont maven)
RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      wget \
      gnupg \
      lsb-release \
      software-properties-common \
      sudo \
      git \
      make \
      unzip \
      zip \
      jq \
      vim \
      bash-completion \
      openssh-client \
      python3 \
      python3-pip \
      python3-venv \
      openjdk-21-jdk \
      kafkacat \
      maven \
    && rm -rf /var/lib/apt/lists/*

# Gradle (binaire officiel, pour avoir une version alignée avec le wrapper)
RUN export http_proxy="${HTTP_PROXY}" https_proxy="${HTTPS_PROXY}" no_proxy="${NO_PROXY}" \
    && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s "/opt/gradle-${GRADLE_VERSION}" /opt/gradle \
    && ln -s /opt/gradle/bin/gradle /usr/local/bin/gradle \
    && rm -f /tmp/gradle.zip

# HashiCorp Terraform (dépôt officiel)
RUN export http_proxy="${HTTP_PROXY}" https_proxy="${HTTPS_PROXY}" no_proxy="${NO_PROXY}" \
    && curl -fsSL https://apt.releases.hashicorp.com/gpg | gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" \
       > /etc/apt/sources.list.d/hashicorp.list \
    && apt-get update && apt-get install -y --no-install-recommends terraform \
    && rm -rf /var/lib/apt/lists/*

# AWS CLI v2
RUN export http_proxy="${HTTP_PROXY}" https_proxy="${HTTPS_PROXY}" no_proxy="${NO_PROXY}" \
    && ARCH="$(uname -m)" \
    && if [ "$ARCH" = "aarch64" ]; then AWS_ARCH="aarch64"; else AWS_ARCH="x86_64"; fi \
    && curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-${AWS_ARCH}.zip" -o /tmp/awscliv2.zip \
    && unzip -q /tmp/awscliv2.zip -d /tmp \
    && /tmp/aws/install \
    && rm -rf /tmp/aws /tmp/awscliv2.zip

# Utilisateur non-root compatible IntelliJ Gateway / VS Code Remote
ARG USERNAME=vscode
ARG USER_UID=1001
ARG USER_GID=1001
RUN groupadd --gid ${USER_GID} ${USERNAME} \
    && useradd  --uid ${USER_UID} --gid ${USER_GID} -m -s /bin/bash ${USERNAME} \
    && echo "${USERNAME} ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/${USERNAME} \
    && chmod 0440 /etc/sudoers.d/${USERNAME}

# Config proxy niveau système : fichier créé uniquement si un proxy est défini
RUN if [ -n "$HTTP_PROXY" ]; then \
      { \
        echo "export HTTP_PROXY=\"${HTTP_PROXY}\"" ; \
        echo "export HTTPS_PROXY=\"${HTTPS_PROXY}\"" ; \
        echo "export NO_PROXY=\"${NO_PROXY}\"" ; \
        echo "export http_proxy=\"${HTTP_PROXY}\"" ; \
        echo "export https_proxy=\"${HTTPS_PROXY}\"" ; \
        echo "export no_proxy=\"${NO_PROXY}\"" ; \
      } > /etc/profile.d/proxy.sh \
      && chmod 0644 /etc/profile.d/proxy.sh ; \
    fi

USER ${USERNAME}
WORKDIR /workspace

# nvm (gestionnaire de versions Node). L'installation de Node est faite
# dans post-create.sh pour éviter les coupures réseau pendant le build.
ENV NVM_DIR=/home/${USERNAME}/.nvm
RUN export http_proxy="${HTTP_PROXY}" https_proxy="${HTTPS_PROXY}" no_proxy="${NO_PROXY}" \
    && mkdir -p "${NVM_DIR}" \
    && git clone --depth 1 --branch "${NVM_VERSION}" https://github.com/nvm-sh/nvm.git "${NVM_DIR}" \
    && . "${NVM_DIR}/nvm.sh"

# Chargement automatique de nvm dans les shells interactifs
RUN { \
      echo 'export NVM_DIR="$HOME/.nvm"' ; \
      echo '[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"' ; \
      echo '[ -s "$NVM_DIR/bash_completion" ] && \. "$NVM_DIR/bash_completion"' ; \
    } >> /home/${USERNAME}/.bashrc

# npm, pip, git, maven : config utilisateur uniquement si proxy fourni
RUN if [ -n "$HTTP_PROXY" ]; then \
      . "${NVM_DIR}/nvm.sh" \
      && npm config set proxy "$HTTP_PROXY" \
      && { [ -n "$HTTPS_PROXY" ] && npm config set https-proxy "$HTTPS_PROXY" || true; } \
      && git config --global http.proxy  "$HTTP_PROXY" \
      && { [ -n "$HTTPS_PROXY" ] && git config --global https.proxy "$HTTPS_PROXY" || true; } \
      && mkdir -p ~/.pip \
      && printf '[global]\nproxy = %s\n' "${HTTPS_PROXY:-$HTTP_PROXY}" > ~/.pip/pip.conf \
      && mkdir -p ~/.m2 \
      && PROXY_HOST="$(echo "$HTTP_PROXY" | sed -E 's#^https?://([^@/]+@)?##; s#:[0-9]+/?$##')" \
      && PROXY_PORT="$(echo "$HTTP_PROXY" | sed -E 's#^.*:([0-9]+)/?$#\1#')" \
      && printf '%s\n' \
           '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">' \
           '  <proxies>' \
           '    <proxy>' \
           '      <id>corp-http</id>' \
           '      <active>true</active>' \
           '      <protocol>http</protocol>' \
           "      <host>${PROXY_HOST}</host>" \
           "      <port>${PROXY_PORT}</port>" \
           "      <nonProxyHosts>${NO_PROXY//,/|}</nonProxyHosts>" \
           '    </proxy>' \
           '  </proxies>' \
           '</settings>' > ~/.m2/settings.xml ; \
    fi

CMD ["sleep", "infinity"]
