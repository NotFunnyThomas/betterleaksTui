package fr.example.musicshop.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Instrument de musique proposé au catalogue.
 *
 * @param id          identifiant interne
 * @param name        nom commercial de l'instrument
 * @param family      famille (strings, winds, keyboards, percussion...)
 * @param priceEuro   prix en euros TTC
 */
public record Instrument(
        Long id,
        @NotBlank String name,
        @NotBlank String family,
        @Positive double priceEuro) {
}
