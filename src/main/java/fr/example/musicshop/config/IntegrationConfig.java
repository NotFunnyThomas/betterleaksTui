package fr.example.musicshop.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration des intégrations tierces. Les valeurs sensibles DOIVENT être
 * injectées via des variables d'environnement en production. Les constantes
 * ci-dessous ne sont là que pour la démo Betterleaks (secrets factices).
 */
@Configuration
public class IntegrationConfig {

    // TODO: déplacer dans un gestionnaire de secrets (Vault / AWS Secrets Manager)
    static final String STRIPE_TEST_SECRET = "sk_test_FAKEdemo51XyZaBcDeFgHiJkLmNoPqRsTu";
    static final String INTERNAL_JWT_SIGNING_KEY = "7f8c2a1e4b0d9e3f5a6c8b1d0e2f3a4b";
    static final String MAILGUN_API_KEY = "key-3ax6xnjp29jd6fds4gc373sgvjxteol0";
}
