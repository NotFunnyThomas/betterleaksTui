package fr.example.musicshop.controller;

import fr.example.musicshop.model.Instrument;
import fr.example.musicshop.service.InstrumentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * API REST du magasin d'instruments. Expose 3 endpoints :
 * <ul>
 *     <li>{@code GET /api/instruments} - liste du catalogue</li>
 *     <li>{@code GET /api/instruments/{id}} - détail d'un instrument</li>
 *     <li>{@code POST /api/instruments} - ajout d'un instrument</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentService service;

    public InstrumentController(InstrumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<Instrument> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Instrument> byId(@PathVariable long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Instrument> create(@Valid @RequestBody Instrument instrument) {
        Instrument saved = service.save(instrument);
        return ResponseEntity.created(URI.create("/api/instruments/" + saved.id())).body(saved);
    }
}
