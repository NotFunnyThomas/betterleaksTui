# betterleaks-tui-demo

Mini application Spring Boot (magasin d'instruments) qui illustre un TUI
Tamboui qui lance Betterleaks sur le dépôt et affiche les findings.

## API REST

- `GET  /api/instruments` : liste du catalogue
- `GET  /api/instruments/{id}` : détail d'un instrument
- `POST /api/instruments` : ajoute un instrument (JSON `{name, family, priceEuro}`)

Lancer l'application :

```bash
./gradlew bootRun
```

## TUI Betterleaks

```bash
./gradlew betterLeaks
./build/bin/betterleaks-tui          # scan le projet courant
./build/bin/betterleaks-tui /path  # scan un autre dépôt
```

Raccourcis : `/` recherche, `n` / `N` match suivant/précédent, `q` quitter.

Les fichiers `application.properties` et `IntegrationConfig.java` contiennent
des **secrets factices** destinés à faire réagir Betterleaks pendant la démo.

Exemlpe d'alias pour un lancement rapide :

```bash
alias betterleaks-tui='(cd ~/Projects/betterleaksTui && ./gradlew betterLeaks -q && ./build/bin/betterleaks-tui)'
```

