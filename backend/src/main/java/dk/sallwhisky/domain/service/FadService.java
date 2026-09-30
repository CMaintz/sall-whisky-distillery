package dk.sallwhisky.domain.service;

import dk.sallwhisky.api.dto.request.FlytFadRequest;
import dk.sallwhisky.api.dto.request.OpretFadRequest;
import dk.sallwhisky.api.dto.request.PaafyldFadRequest;
import dk.sallwhisky.api.dto.response.FadResponse;
import dk.sallwhisky.domain.entity.*;
import dk.sallwhisky.domain.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class FadService {

    private final FadRepository fadRepository;
    private final DestillatRepository destillatRepository;
    private final DestilleringRepository destilleringRepository;
    private final HyldeRepository hyldeRepository;

    public FadResponse opretFad(OpretFadRequest req) {
        long count = fadRepository.count();
        Fad fad = new Fad();
        fad.setFadNummer("F-%03d".formatted(count + 1));
        fad.setLiterKapacitet(req.literKapacitet());
        fad.setTidligereIndhold(req.tidligereIndhold());
        fad.setLand(req.land());
        fad.setFraAar(req.fraAar());
        fad.setLeverandoer(req.leverandoer());
        return toResponse(fadRepository.save(fad));
    }

    @Transactional(readOnly = true)
    public List<FadResponse> getAlleFade() {
        return fadRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<FadResponse> getTommeFade() {
        return fadRepository.findTommeFade().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<FadResponse> getFyldteFade() {
        return fadRepository.findFyldteFade().stream().map(this::toResponse).toList();
    }

    /**
     * Returns barrels whose destillat has been maturing for 3+ years - ready for tapping.
     * The 3-year cutoff is the legal minimum for Scotch-style whisky.
     */
    @Transactional(readOnly = true)
    public List<FadResponse> getFadeKlar() {
        LocalDate cutoff = LocalDate.now().minusYears(3);
        return fadRepository.findFadeKlar(cutoff).stream().map(this::toResponse).toList();
    }

    /**
     * Fills a barrel with new-make spirit from one or more distillation runs.
     * Creates a new Destillat, links Paafyldning records to it, then places it in the Fad.
     * A weighted average determines the combined ABV.
     */
    public FadResponse paafyldFad(UUID fadId, PaafyldFadRequest req) {
        Fad fad = fadRepository.findById(fadId)
                .orElseThrow(() -> new EntityNotFoundException("Fad not found: " + fadId));

        if (fad.harDestillat()) {
            throw new IllegalStateException("Barrel " + fad.getFadNummer() + " already contains a destillat");
        }

        Destillat destillat = new Destillat();
        destillat.setStartDato(LocalDate.now());

        for (PaafyldFadRequest.PaafyldningItem item : req.paafyldninger()) {
            Destillering destillering = destilleringRepository.findById(item.destilleringId())
                    .orElseThrow(() -> new EntityNotFoundException("Destillering not found: " + item.destilleringId()));

            Paafyldning pf = new Paafyldning(item.medarbejder(), item.liter(), destillering);
            destillat.tilfoejPaafyldning(pf);
        }

        destillatRepository.save(destillat);
        fad.setDestillat(destillat);

        ModningsHistorik historik = new ModningsHistorik(destillat, fad, destillat.getStartDato());
        destillat.getModningsHistorik().add(historik);

        return toResponse(fadRepository.save(fad));
    }

    /**
     * Moves a barrel to a different shelf in the warehouse.
     * Clears the old shelf assignment and sets the new one.
     */
    public FadResponse flytFad(UUID fadId, FlytFadRequest req) {
        Fad fad = fadRepository.findById(fadId)
                .orElseThrow(() -> new EntityNotFoundException("Fad not found: " + fadId));

        Hylde nyHylde = hyldeRepository.findById(req.hyldeId())
                .orElseThrow(() -> new EntityNotFoundException("Hylde not found: " + req.hyldeId()));

        if (!nyHylde.erLedig()) {
            throw new IllegalStateException("Shelf is already occupied");
        }

        if (fad.getHylde() != null) {
            fad.getHylde().setFad(null);
            hyldeRepository.saveAndFlush(fad.getHylde());
        }

        nyHylde.setFad(fad);
        fad.setHylde(nyHylde);
        hyldeRepository.save(nyHylde);

        return toResponse(fad);
    }

    public void sletFad(UUID fadId) {
        Fad fad = fadRepository.findById(fadId)
                .orElseThrow(() -> new EntityNotFoundException("Fad not found: " + fadId));
        if (fad.harDestillat()) {
            throw new IllegalStateException("Cannot delete a barrel that contains a destillat");
        }
        fadRepository.delete(fad);
    }

    /**
     * Re-barrels a destillat from one barrel into another.
     * Closes the current ModningsHistorik entry and opens a new one.
     */
    public void omhaeldDestillat(UUID fadFraId, UUID fadTilId) {
        Fad fadFra = fadRepository.findById(fadFraId)
                .orElseThrow(() -> new EntityNotFoundException("Source barrel not found"));
        Fad fadTil = fadRepository.findById(fadTilId)
                .orElseThrow(() -> new EntityNotFoundException("Target barrel not found"));

        if (!fadFra.harDestillat()) {
            throw new IllegalStateException("Source barrel is empty");
        }
        if (fadTil.harDestillat()) {
            throw new IllegalStateException("Target barrel is already occupied");
        }

        Destillat destillat = fadFra.getDestillat();

        destillat.getModningsHistorik().stream()
                .filter(h -> h.getSlutDato() == null)
                .forEach(h -> h.setSlutDato(LocalDate.now()));

        fadFra.setDestillat(null);
        fadTil.setDestillat(destillat);

        ModningsHistorik nyHistorik = new ModningsHistorik(destillat, fadTil, LocalDate.now());
        destillat.getModningsHistorik().add(nyHistorik);

        fadRepository.save(fadFra);
        fadRepository.save(fadTil);
    }

    public FadResponse toResponse(Fad fad) {
        FadResponse.DestillatSummary destillatSummary = null;
        if (fad.getDestillat() != null) {
            Destillat d = fad.getDestillat();
            List<FadResponse.ModningsHistorikItem> historik = d.getModningsHistorik().stream()
                    .map(h -> new FadResponse.ModningsHistorikItem(
                            h.getFad().getFadNummer(),
                            h.getFad().getTidligereIndhold(),
                            h.getPaafyldningsDato(),
                            h.getSlutDato()
                    ))
                    .toList();
            destillatSummary = new FadResponse.DestillatSummary(
                    d.getId(), d.getAntalLiter(), d.getAlkoholProcent(), d.getStartDato(), d.erKlar(), historik
            );
        }

        FadResponse.HyldeInfo hyldeInfo = null;
        if (fad.getHylde() != null) {
            Hylde h = fad.getHylde();
            hyldeInfo = new FadResponse.HyldeInfo(
                    h.getId(), h.getPosition(),
                    h.getReol().getReolNummer(),
                    h.getReol().getLager().getNavn()
            );
        }

        return new FadResponse(
                fad.getId(),
                fad.getFadNummer(),
                fad.getLiterKapacitet(),
                fad.getTidligereIndhold(),
                fad.getLand(),
                fad.getFraAar(),
                fad.getLeverandoer(),
                fad.getAlderAar(),
                destillatSummary,
                hyldeInfo,
                fad.erKlar()
        );
    }
}
