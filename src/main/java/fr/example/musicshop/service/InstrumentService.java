package fr.example.musicshop.service;

import fr.example.musicshop.model.Instrument;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Catalogue en mémoire. Suffisant pour la démo Betterleaks.
 */
@Service
public class InstrumentService {

    private final ConcurrentHashMap<Long, Instrument> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public InstrumentService() {
        save(new Instrument(null, "Fender Stratocaster", "strings", 1299.0));
        save(new Instrument(null, "Yamaha P-125", "keyboards", 699.0));
        save(new Instrument(null, "Pearl Export", "percussion", 849.0));
    }

    public List<Instrument> findAll() {
        return List.copyOf(store.values());
    }

    public Optional<Instrument> findById(long id) {
        return Optional.ofNullable(store.get(id));
    }

    public Instrument save(Instrument instrument) {
        long id = instrument.id() != null ? instrument.id() : sequence.incrementAndGet();
        Instrument persisted = new Instrument(id, instrument.name(), instrument.family(), instrument.priceEuro());
        store.put(id, persisted);
        return persisted;
    }
}
