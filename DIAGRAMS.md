# Diagrams

Mermaid sources for the rendered diagrams in [`diagrams/`](diagrams/).
To regenerate, paste a block into https://mermaid.live and export it as PNG.

---

## 1. Arkitektur — `diagrams/arch.png`

```mermaid
classDiagram
    direction TB
    class GUI {
        <<component>>
        FadVindue
        DestilleringPane
        LagerstyringPane
        WhiskyPane
    }
    class Controller {
        <<service>>
        +createFad()$
        +omhældningAfDestillat()$
        +getFadeMedFærdigDestillat()$
    }
    class Storage {
        <<interface>>
        +getFade()
        +addFad()
        +getLagre()
    }
    class ListStorage {
        <<repository>>
        +loadStorage()
        +saveStorage()
    }

    GUI --> Controller : kalder
    Controller --> Storage : delegerer til
    Storage <|.. ListStorage : implementerer
```

---

## 2. Domænemodel — `diagrams/domain.png`

```mermaid
classDiagram
    direction LR
    class Korn {
        +String sort
        +String variant
        +String markNavn
    }
    class Destillering {
        +int newSpiritbatchNr
        +double alkoholProcent
        +double antalLiter
    }
    class Påfyldning {
        ~Påfyldning()
        +double literPåfyldt
    }
    class Destillat {
        +createPåfyldning() Påfyldning
        +destillatKlar() bool
        +omhældDestillat(Fad)
    }
    class ModningsHistorik {
        +LocalDate påfyldningsDato
        +LocalDate slutDato
    }
    class Fad {
        +String fadNr
        +int literKapacitet
    }
    class FadHistorik {
        +String tidligereIndhold
        +String leverandør
        +String land
    }
    class Lager {
        +String navn
    }
    class Reol {
        +int reolNummer
    }
    class Hylde {
        +int nummer
    }
    class FadTapning {
        ~FadTapning()
        +double literTappet
    }
    class WhiskyProdukt {
        +whiskyType() String
        +genererHistorie() String
    }
    class WhiskyFlaske {
        +int flaskeNr
        +String produktHistorie
    }

    Korn "1" <-- "1..*" Destillering
    Destillering "1" <-- "1..*" Påfyldning
    Destillat "1" *-- "1..*" Påfyldning
    Destillat "1" *-- "1..*" ModningsHistorik
    ModningsHistorik "1" --> "1" Fad
    Fad "1" *-- "1" FadHistorik
    Fad "1" o-- "0..1" Destillat
    Lager "1" *-- "1..*" Reol
    Reol "1" *-- "1..*" Hylde
    Hylde "1" o-- "0..1" Fad
    WhiskyProdukt "1" *-- "1..*" FadTapning
    FadTapning "1" --> "1" Fad
    FadTapning "1" --> "1" Destillat
    WhiskyProdukt "1" *-- "1..*" WhiskyFlaske
```

---

## 3. Sekvensdiagram: Påfyldning — `diagrams/seq-paafyldning.png`

```mermaid
sequenceDiagram
    actor Bruger
    participant GUI as PåfyldFad
    participant D as Destillat
    participant PF as Påfyldning
    participant DR as Destillering
    participant F as Fad
    participant MH as ModningsHistorik

    Bruger->>GUI: udfyld og klik Påfyld
    Note over GUI: Validerer input
    GUI->>D: new Destillat()
    Note over GUI,D: ⚠️ Direkte - ikke via Controller
    loop for hver Destillering
        GUI->>D: createPåfyldning(navn, liter, dest)
        activate D
        D->>D: validate()
        D->>PF: new Påfyldning(navn, liter, dest)
        Note over PF: package-private
        PF->>DR: fjernAntalLiter(liter)
        D->>D: udregnAlkoholprocent()
        deactivate D
    end
    GUI->>F: addDestillat(destillat)
    F->>D: setFad(this)
    D->>MH: new ModningsHistorik(fad, now)
    Note over MH: Modningsuret starter
```

---

## 4. Sekvensdiagram: Omhældning — `diagrams/seq-omhaeldning.png`

```mermaid
sequenceDiagram
    actor Bruger
    participant GUI as FlytFadWindow
    participant C as Controller
    participant D as Destillat
    participant FF as FadFra
    participant FT as FadTil
    participant MH as ModningsHistorik

    Bruger->>GUI: vælg fade → Omhæld
    GUI->>C: omhældningAfDestillat(fra, til)
    Note over GUI,C: ✅ Korrekt via Controller
    C->>D: omhældDestillat(fadTil)
    activate D
    D->>FF: removeDestillat()
    Note over FF: FadFra er tomt
    D->>FT: addDestillat(this)
    FT->>D: setFad(fadTil)
    activate D
    D->>MH: setSlutDato(now)
    Note over MH: Gammel periode lukkes
    D->>MH: new ModningsHistorik(fadTil, now)
    Note over MH: Ny periode begynder
    deactivate D
    deactivate D
```
