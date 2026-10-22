--Curl Out put--

1. {"id":83,"name":"tomcat-handler-9","virtual":true,"state":"RUNNABLE","description":"VirtualThread[#83,tomcat-handler-9]/runnable@ForkJoinPool-1-worker-5"}
--Curl Out put--

--Vragen--
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

--Vragen--

--Experiement 3--
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
