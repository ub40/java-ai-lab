# Java AI Lab

Leerproject met **Java 25**, **Spring Boot 4.1** en **Spring AI 2.0**, gebouwd als voorbereiding op [SoJava 2026](https://www.sogeti.nl) (28 oktober 2026, Groningen).

Het project laat stap voor stap zien hoe je moderne Java combineert met AI: van virtual threads tot een lokaal taalmodel, structured output, tool calling en een MCP-server die door Claude Code en Codex gebruikt wordt. Alles op één gedeelde service-laag.

## Wat het kan

| Onderdeel | Endpoint | Wat het laat zien |
|---|---|---|
| **Virtual threads** | `/api/thread`, `/api/thread/slow` | Gemeten: **~9× meer doorvoer** bij wachtende requests (20 s → 2 s) |
| **Modern Java 21–25** | `/api/projects/**` | Records, sealed interfaces, pattern matching, record patterns, stream gatherers, flexible constructor bodies |
| **Lokaal taalmodel** | `/api/chat` | Spring AI + **Ollama** (llama3.2): de data blijft op de eigen machine |
| **Structured output** | `/api/projects/{id}/analysis` | Het model antwoordt als Java-record; **gevalideerd**, met retry en Ollama's native JSON-modus |
| **Tool calling** | `/api/assistant` | Het model roept zelf Java-methodes aan om echte data op te halen |
| **Security** | tool `requestProjectDeletion` | Het model kan een verwijdering alleen **aanvragen**: de code beslist, niet de prompt |
| **MCP-server** | `/mcp` | Dezelfde tools via het *Model Context Protocol*; getest met **Claude Code** en **Codex** |

## Architectuur

```
            REST-API             Eigen assistent            MCP-clients
         (/api/projects)     (/api/assistant + Ollama)   (Claude Code, Codex)
                │                       │                        │
                │                  tool calling              MCP (/mcp)
                │                       └──────────┬─────────────┘
                │                             ProjectTools
                └──────────────────┬───────────────┘
                            AiActionService      ← sealed AiAction + pattern matching
                                   │
                            ProjectService       ← records, Optional, gatherers
```

Eén service-laag, drie ingangen: de REST-API, tool calling voor de eigen lokale assistent, en MCP voor externe AI-clients.

## Tech stack

Java 25 · Spring Boot 4.1 · Spring AI 2.0 · Ollama (llama3.2) · Model Context Protocol · Maven · JUnit 5 · AssertJ

## Zelf draaien

```bash
ollama serve                              # lokaal taalmodel starten (eigen terminal)
ollama pull llama3.2                      # model downloaden (eenmalig)
./mvnw spring-boot:run                    # applicatie op http://localhost:8080
./mvnw verify                             # tests (Ollama niet nodig)
```

De volledige handleiding, met alle endpoints, verwachte resultaten, MCP-commando's en het koppelen van Claude Code en Codex, staat in **[INSTRUCTION.md](INSTRUCTION.md)**.

## Meer informatie

| Bestand | Inhoud |
|---|---|
| **[INSTRUCTION.md](INSTRUCTION.md)** | Stap voor stap: Ollama en de app starten, alle endpoints met verwachte resultaten, MCP vanuit de terminal, Claude Code en Codex koppelen |
| **[BEVINDINGEN.md](BEVINDINGEN.md)** | Wat ik gemeten en geleerd heb: hallucinaties, structured output die stilletjes faalde, tool-ontwerp, tool calling vs. MCP, llama3.2 vs. Claude Code vs. Codex, en privacy bij AI-codeerassistenten |

## Belangrijkste lessen

Uitgebreid, met tabellen en logs: [BEVINDINGEN.md](BEVINDINGEN.md).

| Les | Waar gezien |
|---|---|
| Virtual threads maken **wachten goedkoop**, niet code sneller | Belastingstest: 20 s → 2 s, maar elke request bleef 1 s |
| **Vertrouw AI-output nooit blind** | Het model gaf stilletjes een record met alleen `null`; nu gevalideerd met retry en een duidelijke 502 |
| **Vorm en inhoud zijn twee problemen** | Native JSON-modus loste de vorm op; de prompt en validatie bewaken de inhoud |
| **Tool-ontwerp is prompt engineering** | Een optionele parameter werd door het model met `""` gevuld; opgelost met twee kleine tools |
| **Beveilig in de server, niet in de prompt of de client** | Prompt injection en clients zonder toestemmingsvraag konden niets verwijderen |
| **Lokaal vs. cloud is een afweging** | llama3.2 houdt data lokaal maar maakt meer fouten; Claude en Codex zijn slimmer, maar de data gaat naar de aanbieder |

---

## Leernotities: onderwerp 1 (virtual threads)

```Curl Out put

1. {"id":83,"name":"tomcat-handler-9","virtual":true,"state":"RUNNABLE","description":"VirtualThread[#83,tomcat-handler-9]/runnable@ForkJoinPool-1-worker-5"}
```

```Vragen
1. Wat is het verschil tussen een platform thread en een virtual thread, qua geheugen en wie ze beheert?

Platform thread vs. virtual thread

┌─────────────────────────────────────┬────────────────────────────────────────────────┬───────────────────────────────────────────────────────────────────────┐                                        
│                                     │                Platform thread                 │                            Virtual thread                             │                                        
├─────────────────────────────────────┼────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────┤                                        
│ Wat is het?                         │ Een dun laagje om een echte OS-thread (1-op-1) │ Een Java-object dat de JVM beheert                                    │                                        
├─────────────────────────────────────┼────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────┤                                        
│ Wie plant het in?                   │ Het besturingssysteem                          │ De JVM                                                                │                                        
├─────────────────────────────────────┼────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────┤                                        
│ Geheugen                            │ Vaste stack, standaard ~1 MB gereserveerd      │ Stack staat op de heap en groeit en krimpt mee, vaak maar een paar KB │                                        
├─────────────────────────────────────┼────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────┤                                        
│ Hoeveel kunnen er tegelijk bestaan? │ Duizenden                                      │ Miljoenen                                                             │                                        
├─────────────────────────────────────┼────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────┤                                        
│ Aanmaken                            │ Duur, dus je gebruikt een pool                 │ Goedkoop, dus één per taak en niet poolen                             │

2. Wat is een carrier thread, en wat betekenen mount en unmount?
- Een carrier thread is een gewone platform thread uit de ForkJoinPool van de JVM. Alleen een carrier kan echt code uitvoeren op de CPU.
    - Mount: de JVM zet een virtual thread op een vrije carrier en voert zijn code uit.
    - Unmount: zodra de virtual thread moet wachten (sleep, database-call, HTTP-request, socket-I/O), slaat de JVM zijn stack op de heap op en maakt de carrier vrij. Als het wachten voorbij is, wordt hij
      weer gemount, op welke carrier dan ook vrij is. Daarom zie je in experiment 2 soms een andere worker.
    - Pinning (uitzondering): soms kan een VT niet unmounten en blijft hij aan de carrier "vastgepind". Dat gebeurt bijvoorbeeld in native code (JNI), en vóór Java 24 ook binnen een synchronized-blok. JEP
      491 (Java 24) heeft dat laatste opgelost. Jij gebruikt Java 25, dus daar heb je geen last meer van.

3. Hoeveel carriers zijn er standaard? sysctl -n hw.ncpu resultateert in 10.
   Evenveel als het aantal beschikbare CPU-cores (availableProcessors()). in mijn macbook 10 cores

```

```Experiement 3
Kijk of je de extra worker threads kan starten met:
 -Djdk.virtualThreadScheduler.parallelism=2                                                                                                                                                              
Zet dit in IntelliJ bij Run → Edit Configurations → VM options, herstart, en kijk of je alleen nog worker-1 en worker-2 ziet. Dat is meteen een leuk extra experiment voor je README.                   


## Stap 4: Blokkerend endpoint `/api/thread/slow`

**Doel:** een trage I/O-call (database, externe API) nabootsen met `Thread.sleep(ms)`, en zien wat er met de carrier thread gebeurt tijdens het wachten.

**Endpoint:** `GET /api/thread/slow?ms=1000` (standaard 1000 ms)

Het endpoint leest `Thread.currentThread().toString()` uit **vóór** en **na** de `sleep` en geeft beide terug (`before` / `after`), samen met de gewone thread-info.

### Resultaat (één request)

```bash
curl "http://localhost:8080/api/thread/slow?ms=2000"
```

```json
{
  "thread": {
    "id": 69,
    "name": "tomcat-handler-0",
    "virtual": true,
    "state": "RUNNABLE",
    "description": "VirtualThread[#69,tomcat-handler-0]/runnable@ForkJoinPool-1-worker-1"
  },
  "before": "VirtualThread[#69,tomcat-handler-0]/runnable@ForkJoinPool-1-worker-1",
  "after": "VirtualThread[#69,tomcat-handler-0]/runnable@ForkJoinPool-1-worker-1",
  "ms": 2000
}
```
```

### Uitleg

- `before` en `after` staan allebei op `worker-1`. Dat betekent **niet** dat de virtual thread niet is afgestapt (unmount). Tijdens de `sleep` was `worker-1` vrij.
- Er was maar **één request**: alle 10 carriers waren vrij, dus de JVM zette de virtual thread na het wachten weer op `worker-1`.
- Om een carrier-wissel te zien heb je **drukte** nodig, dus veel requests tegelijk:

```bash
for i in {1..30}; do curl -s -w "\n" "http://localhost:8080/api/thread/slow?ms=500" & done; wait
```

> TODO: regel toevoegen waarin `before` en `after` op een andere `worker-N` staan (bewijs van unmount/mount).

### Geleerd

- Een **record in een record** wordt automatisch geneste JSON.
- `InterruptedException` is een **checked exception**. Netjes afhandelen: `Thread.currentThread().interrupt()` (vlag herstellen) en daarna een exception gooien, niet alleen `printStackTrace()`.
- **DRY:** dubbele code verplaatst naar de hulpmethode `currentThreadInfo()`.

## Stap 5: Belastingstest, platform vs. virtual threads

**Doel:** met cijfers aantonen dat virtual threads veel meer *wachtende* requests tegelijk aankunnen.

**Test:** 200 requests, 100 tegelijk, elke request wacht 1 seconde.

```bash
hey -n 200 -c 100 "http://localhost:8080/api/thread/slow?ms=1000"
```

- **Meting A:** `spring.threads.virtual.enabled=false` + `server.tomcat.threads.max=10`
- **Meting B:** `spring.threads.virtual.enabled=true` + `server.tomcat.threads.max=10` (bewust laten staan)

### Resultaten

|                 | A: Platform (max 10) | B: Virtual   | Verschil          |
|-----------------|----------------------|--------------|-------------------|
| Total           | 20,18 s              | **2,14 s**   | ~9,4× sneller     |
| Requests/sec    | 9,9                  | **93,6**     | ~9,4× meer        |
| Average latency | 7,83 s               | **1,07 s**   |                   |
| Fastest         | 1,04 s               | 1,01 s       |                   |
| Slowest         | 10,12 s              | **1,13 s**   |                   |
| Size/request    | 252 bytes            | 343 bytes    | langere `toString()` van een virtual thread |

### Histogrammen

**A, platform threads:** trapjes van 10 (elke seconde komen er 10 klaar), en daarna **110 requests op ~10 s**.

```
  1.044 [1]     |
  1.951 [9]     |■■■
  2.858 [10]    |■■■■
  ...           (elke seconde 10)
  9.210 [10]    |■■■■
 10.117 [110]   |■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■
```

**B, virtual threads:** twee blokken van ~100, geen wachtrij.

```
  1.011 [1]     |
  1.022 [99]    |■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■
  ...
  1.114 [12]    |■■■■■
  1.126 [88]    |■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■
```

### Uitleg

**Meting A: thread starvation**
- 10 threads en 200 requests van 1 s: 200 ÷ 10 = **20 rondes, dus ~20 s**. Maximaal 10 requests per seconde.
- `hey -c 100` betekent 100 "gebruikers" die elk 2 requests sturen. De tweede request sluit **achteraan de wachtrij** aan, waar nog ~90 requests vóór staan. Daardoor duren bijna alle tweede requests ~10 s: dat zijn de 110 in het laatste blok.
- **Wet van Little:** wachttijd ≈ requests in het systeem ÷ doorvoer = 100 ÷ 10 per s = **10 s**.
- De server is niet traag (het werk is 1 s), maar requests **wachten op een vrije thread**. Dat heet *thread starvation*.

**Meting B: geen wachtrij**
- Alle 100 requests draaien **tegelijk**, elk op een eigen virtual thread. 200 ÷ 100 = **2 rondes, dus ~2 s**.
- Elke request duurt ~1 s, en dat is precies de `sleep`. Niemand wacht op een thread.
- Het tweede blok is ~0,1 s trager dan het eerste: een kleine overhead als 100 requests tegelijk binnenkomen (exacte oorzaak niet onderzocht).

### Vragen en antwoorden

1. **Klopte de voorspelling?**
   Ja. A: 20 rondes, dus ~20 s. B: 2 rondes, dus ~2 s.

2. **Waarom had `threads.max=10` geen effect bij B?**
   `server.tomcat.threads.max` stelt de pool met **platform threads** van Tomcat in. Met virtual threads aan gebruikt Tomcat die pool niet: hij maakt voor **elke request een nieuwe virtual thread** (`tomcat-handler-N`). De limiet is nu het maximale aantal verbindingen (`server.tomcat.max-connections`, standaard 8192).

3. **Waarom was Slowest bij A zo veel hoger dan Fastest?**
   Door de **wachtrij**. Het werk kost voor iedereen 1 s, maar sommige requests stonden tot 9 s te wachten op een vrije thread. Bij B is er geen wachtrij, dus liggen Fastest en Slowest dicht bij elkaar.

4. **Helpen virtual threads ook bij 1 seconde CPU-werk (geen sleep)?**
   **Nee.** Virtual threads geven de carrier alleen vrij tijdens het **wachten** (I/O). CPU-werk houdt de carrier bezig, en er zijn maar 10 cores en dus 10 carriers. Dan krijg je ongeveer dezelfde ~20 s als bij A.

### Conclusie

> **Virtual threads maken code niet sneller. Ze maken wachten goedkoop.**
> Bij I/O-zwaar werk (database, HTTP-calls) kan één server daardoor veel meer requests tegelijk aan, zonder grote thread pool. Bij CPU-zwaar werk helpen ze niet.

## Stap 6: Integratietests

**Doel:** automatisch bewijzen dat de endpoints werken en dat virtual threads aanstaan.

| Testklasse | Test | Controleert |
|---|---|---|
| `ThreadControllerTest` | `currentThreadRunsOnVirtualThread` | `/api/thread` draait op een virtual thread (`tomcat-handler-*`) |
| `ThreadControllerTest` | `slowThreadSleepsForRequestedTime` | `/api/thread/slow?ms=100` geeft `ms=100` terug, `before`/`after` zijn virtual threads, en het duurt minstens 100 ms |
| `ThreadControllerPlatformThreadTest` | `currentThreadRunsOnPlatformThreadWhenVirtualThreadsDisabled` | Tegenproef: met `spring.threads.virtual.enabled=false` draait het op een platform thread (`http-nio-*`) |

**Waarom `@SpringBootTest(webEnvironment = RANDOM_PORT)` en geen MockMvc?**
MockMvc roept de controller aan zonder echte server, op de thread van de test zelf. Dan is `virtual` altijd `false`. Met `RANDOM_PORT` start de echte Tomcat op een vrije poort, en `RestTestClient` stuurt echte HTTP-requests. Zo test je wat er in productie gebeurt.

**Draaien:**

```bash
./mvnw verify
```

```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in JavaAiLabApplicationTests
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in ThreadControllerPlatformThreadTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in ThreadControllerTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Er draait altijd een thread

Elke request heeft een thread nodig, anders kan er geen code uitgevoerd worden. De instelling `spring.threads.virtual.enabled` bepaalt alleen **welk soort**:

| Instelling | Wie handelt de request af?            | Naam                |
|------------|---------------------------------------|---------------------|
| `true`     | Een **virtual thread**                | `tomcat-handler-N`  |
| `false`    | Een **platform thread** uit Tomcat's pool | `http-nio-…-exec-N` |

`http-nio-8080-exec-N` is de naam die Tomcat aan zijn gewone threads geeft: `http` = HTTP-connector, `nio` = het connectortype (Java "New I/O"), `8080` = de poort, `exec-N` = werker N uit de thread pool.

Vóór Java 21 bestonden virtual threads niet, en draaide elke Spring-app op platform threads. Dat werkt prima, maar kan minder wachtende requests tegelijk aan (zie meting A in stap 5).

### Wat betekent "test geslaagd"?

Een test slaagt als **de verwachting klopt met wat er echt gebeurt**. Groen betekent dus niet "virtual threads werken", maar "wat ik verwachtte, kwam uit".

| Test                                        | Verwachting                       | Werkelijkheid   | Resultaat |
|---------------------------------------------|-----------------------------------|-----------------|-----------|
| Tegenproef (`false`)                        | "Ik verwacht een platform thread" | Platform thread | ✅ groen  |
| Test 1 (`true`)                             | "Ik verwacht een virtual thread"  | Virtual thread  | ✅ groen  |
| Test 1, met `false` in `application.properties` | "Ik verwacht een virtual thread"  | Platform thread | ❌ rood   |

De laatste regel heb ik bewust uitgeprobeerd, om te controleren dat de test echt kan falen:

```
[ERROR]   ThreadControllerTest.currentThreadRunsOnVirtualThread:29 Expecting value to be true but was false
[ERROR]   ThreadControllerTest.slowThreadSleepsForRequestedTime:50 Expecting value to be true but was false
[ERROR] Tests run: 4, Failures: 2, Errors: 0, Skipped: 0
```

De tegenproef blijft groen, omdat hij zelf `properties = "spring.threads.virtual.enabled=false"` in de testannotatie zet. Die waarde gaat vóór wat er in `application.properties` staat.

De tegenproef is dus groen **omdat** hij een platform thread verwacht en die ook krijgt. Hij bewijst niet dat virtual threads draaien, maar juist dat ze **uit** staan als de instelling uit staat.

### Samengevat

- **Test 1** bewijst: instelling aan, dan virtual threads.
- **Tegenproef** bewijst: instelling uit, dan platform threads (en de app werkt nog steeds).
- **Samen** bewijzen ze dat **de instelling** het verschil maakt, en dat virtual threads niet vanzelf aanstaan.

## Stap 7: Verdieping

### 7.1 JEP 444: Virtual Threads (openjdk.org/jeps/444)

**1. Sinds welke Java-versie zijn virtual threads definitief?**
Sinds **Java 21** (september 2023). Daarvoor waren ze *preview* in Java 19 en 20.

**2. Welk probleem met "thread-per-request" wilden ze oplossen?**
- *Thread-per-request* (elke request krijgt een eigen thread) is de makkelijkste manier om servercode te schrijven: gewone, leesbare code van boven naar beneden, die makkelijk te debuggen is.
- Maar platform threads zijn **duur** (één OS-thread per stuk), dus je kunt er maar een paar duizend hebben. Het **aantal threads** werd de grens voor hoeveel requests een server aankan, ook als de CPU niks staat te doen.
- Ontwikkelaars weken daarom uit naar **asynchrone/reactieve** code (`CompletableFuture`, WebFlux). Die schaalt beter, maar is veel moeilijker te lezen, te debuggen en te profilen.
- **Virtual threads geven allebei:** de eenvoud van thread-per-request én de schaalbaarheid van async code.
- De JEP gebruikt zelf de **wet van Little** om dit uit te leggen, dezelfde formule als bij meting A in stap 5.

**3. Wat zegt de JEP over de snelheid van code?**
In de sectie *Description* staat:
> *"Virtual threads are not faster threads — they do not run code any faster than platform threads. They exist to provide scale (higher throughput), not speed (lower latency)."*

**Klopt dat met mijn meting?** Ja. In beide metingen duurde één request ~1 s, want de `sleep` werd niet sneller. Wat veranderde, was de **doorvoer**: 9,9 → 93,6 requests/sec, omdat er geen wachtrij meer was.

### 7.2 Wanneer virtual threads NIET helpen

**a) De connection pool als nieuwe flessenhals**

Stel: 10.000 virtual threads willen tegelijk de database in, maar de pool (bijv. HikariCP) heeft 10 verbindingen.
- Er kunnen er maar **10 tegelijk** een query doen. De andere 9.990 **wachten op een vrije verbinding**. Duurt dat te lang, dan krijgen ze een timeout-fout (bij HikariCP standaard na 30 s).
- **Dat lijkt precies op meting A:** daar wachtten requests op een vrije *thread*, hier op een vrije *databaseverbinding*.
- **De les:** virtual threads **verplaatsen de wachtrij, ze halen hem niet weg**. De flessenhals wordt de schaarste resource (databaseverbindingen, een externe API met rate limit). Je moet dus nog steeds nadenken over limieten en pool-groottes.

**b) Virtual threads niet poolen**

- **Waarom niet?** Een pool bestaat om dure dingen te hergebruiken. Virtual threads zijn **goedkoop**: maak er één per taak, en gooi hem daarna weg. Een pool voegt alleen complexiteit toe. Bovendien kan een hergebruikte thread oude `ThreadLocal`-data van een vorige taak meenemen. De JEP zegt letterlijk: *"Virtual threads should never be pooled."*
- **Juiste manier:** `Executors.newVirtualThreadPerTaskExecutor()`, dus één nieuwe virtual thread per taak.
- **Hoe beperk je hoeveel er tegelijk iets doen?** Met een **`Semaphore`**: een teller met een vast aantal "vergunningen".
  - `semaphore.acquire()`: pak een vergunning, of wacht tot er een vrij is
  - `semaphore.release()`: geef hem terug, altijd in een `finally`-blok
  - Voorbeeld: `new Semaphore(10)` laat maximaal 10 virtual threads tegelijk de externe API aanroepen. De rest wacht, en dat is goedkoop, want het zijn virtual threads.
  - Spring Framework 7 heeft hier ook een annotatie voor: **`@ConcurrencyLimit`** (komt terug bij het onderwerp resilience).

**c) CPU-zwaar werk** (al gemeten in stap 5): virtual threads helpen niet. Er zijn maar 10 cores en dus 10 carriers.

### 7.3 Nieuw in Java 25

**Scoped Values (JEP 506), definitief in Java 25**

- **Wat is het?** Een manier om een waarde (bijv. de ingelogde gebruiker of een request-ID) **beschikbaar te maken voor alle code die je aanroept**, zonder die als parameter door te geven. De waarde is **onveranderbaar** en geldt alleen **binnen een afgebakend blok**:
  `ScopedValue.where(USER, "urfan").run(() -> ...)`. Buiten dat blok bestaat hij niet meer.
- **Waarom beter dan `ThreadLocal`, zeker met miljoenen virtual threads?**
  - `ThreadLocal` is **veranderbaar**: iedere methode kan hem overschrijven, en dat is lastig te volgen.
  - `ThreadLocal` leeft **zo lang als de thread**. Vergeet je `remove()`, dan krijg je een geheugenlek, of lekt data naar een volgende taak.
  - Elke thread heeft zijn **eigen kopie**. Bij miljoenen virtual threads zijn dat miljoenen kopieën, wat veel geheugen kost.
  - Een Scoped Value is onveranderbaar, ruimt zichzelf op aan het einde van het blok, en kan **goedkoop gedeeld** worden met child-threads.

**Structured Concurrency (JEP 505), preview in Java 25**

- **Het probleem:** stel, één request haalt tegelijk een *gebruiker* en een *bestelling* op, in 2 parallelle taken via een `ExecutorService`. Als de gebruiker-taak **faalt**, loopt de bestelling-taak gewoon door: verspild werk. Als de request zelf wordt afgebroken, blijven beide taken soms doorlopen (**thread leaks**). En in een thread dump zie je niet welke taken bij elkaar horen.
- **De oplossing:** met `StructuredTaskScope` behandel je parallelle subtaken als **één eenheid**, net als een gewoon blok code:
  - je start subtaken met `fork()` en wacht op allemaal met `join()`
  - faalt er één, dan worden de andere **automatisch geannuleerd**
  - als het blok eindigt, zijn **gegarandeerd alle subtaken klaar**, dus geen lekken
- Het past perfect bij virtual threads: elke subtaak krijgt goedkoop een eigen virtual thread.
- Omdat het nog **preview** is, moet je `--enable-preview` gebruiken, en kan de API nog veranderen.
