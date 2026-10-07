package fr.example.musicshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée Spring Boot du magasin d'instruments.
 */
@SpringBootApplication
public class MusicShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicShopApplication.class, args);
    }
}
