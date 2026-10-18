# Dev Container — MusicShop

Image Ubuntu 24.04 LTS avec Python, Terraform, Make, AWS CLI v2, kcat (kafkacat), Node/npm et OpenJDK 21.
Compatible IntelliJ (Gateway) et VS Code (Dev Containers).

## Prérequis
- Docker (Desktop ou Engine)
- IntelliJ IDEA Ultimate + plugin **Dev Containers**, ou JetBrains Gateway
- Optionnel : VS Code + extension **Dev Containers**

## Proxy d'entreprise
Les variables `HTTP_PROXY`, `HTTPS_PROXY` et `NO_PROXY` sont lues depuis l'environnement
local au moment du build et de l'exécution. Elles sont injectées dans :
- APT (`/etc/apt/apt.conf.d/95proxies`)
- Shell (`/etc/profile.d/proxy.sh`)
- `git`, `npm`, `pip` (config utilisateur)

Exporte-les avant d'ouvrir le projet, par exemple :
```bash
export HTTP_PROXY="http://user:pass@proxy.corp:8080"
export HTTPS_PROXY="$HTTP_PROXY"
export NO_PROXY="localhost,127.0.0.1,.corp,.local"
```

Évite d'écrire les identifiants en clair : préfère une variable d'environnement
système ou un gestionnaire de secrets. Ne commite jamais ces valeurs.

## Ouverture avec IntelliJ
1. `File` → `Remote Development` → `Dev Containers` → `New Dev Container`
2. Pointer vers ce dépôt / la branche courante
3. IntelliJ détecte `.devcontainer/devcontainer.json` et lance le build

## Ouverture avec VS Code
`Dev Containers: Reopen in Container` depuis la palette.

## Volumes montés
- Code projet → `/workspace`
- `~/.aws` de l'hôte → `/home/vscode/.aws` (profils AWS partagés en lecture/écriture)
