# Instructies: Java AI Lab zelf draaien en testen

Deze handleiding laat zien hoe je de applicatie samen met een lokaal taalmodel (Ollama) start, vragen stelt, en welke resultaten je kunt verwachten.

## Inhoud

1. [Benodigdheden](#1-benodigdheden)
2. [Ollama installeren en starten](#2-ollama-installeren-en-starten)
3. [De applicatie starten](#3-de-applicatie-starten)
4. [Handige terminal-commando's](#4-handige-terminal-commandos)
5. [Endpoints testen zonder AI](#5-endpoints-testen-zonder-ai)
6. [Endpoints testen met AI](#6-endpoints-testen-met-ai)
7. [Automatische tests](#7-automatische-tests)
8. [Problemen oplossen](#8-problemen-oplossen)
9. [Stoppen](#9-stoppen)

---

## 1. Benodigdheden

| Wat | Versie | Controleren |
|---|---|---|
| **Java (JDK)** | 25 | `java -version` |
| **Ollama** | recent | `ollama --version` |
| **curl** en **jq** | – | `curl --version`, `jq --version` |
| **Geheugen** | minstens 8 GB RAM (16 GB aanbevolen) | – |

Maven hoef je niet te installeren: het project gebruikt de Maven Wrapper (`./mvnw`).

**Gebruikt je terminal een andere Java-versie?** Zet dan op macOS (bijv. in `~/.zshrc`):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

---

## 2. Ollama installeren en starten

[Ollama](https://ollama.com) draait taalmodellen lokaal. Er gaat geen data naar het internet en je hebt geen API-key nodig.

**Installeren (macOS):**

```bash
brew install ollama
```

Voor Linux en Windows: zie [ollama.com/download](https://ollama.com/download).

**Server starten** (laat dit terminalvenster openstaan):

```bash
ollama serve
```

Of op de achtergrond, ook na een herstart van je Mac:

```bash
brew services start ollama
```

**Model downloaden** (± 2 GB, eenmalig), in een tweede terminal:

```bash
ollama pull llama3.2
```

**Controleren:**

```bash
curl http://localhost:11434/api/tags
```

✅ Verwacht: JSON met `"name": "llama3.2:latest"` in de lijst `models`.

---

## 3. De applicatie starten

Open een nieuwe terminal in de map van het project:

```bash
./mvnw spring-boot:run
```

Of in IntelliJ: open `JavaAiLabApplication` en klik op de groene ▶.

✅ Verwacht in de console: `Tomcat started on port 8080`.

De instellingen staan in `src/main/resources/application.properties`:

| Instelling | Waarde | Betekenis |
|---|---|---|
| `spring.threads.virtual.enabled` | `true` | Requests draaien op virtual threads |
| `spring.ai.ollama.base-url` | `http://localhost:11434` | Waar Ollama draait |
| `spring.ai.ollama.chat.model` | `llama3.2` | Welk model |
| `spring.ai.ollama.chat.temperature` | `0.2` | Laag: voorspelbare antwoorden |

---

## 4. Handige terminal-commando's

Met deze functies stel je vragen zonder steeds de hele `curl` te typen. Plak ze in je terminal, of zet ze in `~/.zshrc` om ze blijvend te hebben:

```bash
ask_chat()      { curl -s -G localhost:8080/api/chat      --data-urlencode "question=$*" | jq -r .answer; }
ask_assistant() { curl -s -G localhost:8080/api/assistant --data-urlencode "question=$*" | jq -r .answer; }
```

Gebruik:

```bash
ask_chat What are virtual threads in Java
ask_assistant What is the status of project 2
```

Typ de vraag **achter** het commando. Een vraag zonder `ask_chat` of `ask_assistant` ervoor ziet de terminal als een (onbekend) commando.

---

## 5. Endpoints testen zonder AI

Deze endpoints werken ook als Ollama **niet** draait. De resultaten zijn altijd hetzelfde.

### Virtual threads

```bash
curl http://localhost:8080/api/thread
```

✅ Verwacht: `"virtual": true` en een naam als `"tomcat-handler-0"`.

```bash
curl "http://localhost:8080/api/thread/slow?ms=1000"
```

✅ Verwacht: na ± 1 seconde JSON met `before` en `after`: de thread vóór en na het wachten.

**Carrier-wissel zien** (30 requests tegelijk):

```bash
for i in {1..30}; do curl -s -w "\n" "http://localhost:8080/api/thread/slow?ms=500" & done; wait
```

Zoek een regel waarin `before` en `after` op een andere `ForkJoinPool-1-worker-N` staan: dezelfde virtual thread ging na het wachten verder op een andere carrier.

### Projecten

De projecten staan in het geheugen (`ProjectService`), er is nog geen database.

| Request | Verwacht resultaat |
|---|---|
| `curl http://localhost:8080/api/projects` | 6 projecten |
| `curl "http://localhost:8080/api/projects?status=IN_PROGRESS"` | project 1 (*Java AI Lab*) en 5 (*Resilience Patterns*) |
| `curl http://localhost:8080/api/projects/1` | `{"project":{"id":1,"name":"Java AI Lab","status":"IN_PROGRESS"}}` |
| `curl -i http://localhost:8080/api/projects/999` | **404** Not Found |
| `curl http://localhost:8080/api/projects/2/summary` | `"Project 'Virtual Threads Benchmark' has been completed."` (± 300 ms) |
| `curl http://localhost:8080/api/projects/summaries` | 6 samenvattingen, `durationMs` ± 300 (parallel op virtual threads, i.p.v. 6 × 300 ms) |
| `curl http://localhost:8080/api/projects/newest` | `{"id":6,"name":"OpenAPI Documentation","status":"PLANNED"}` |
| `curl "http://localhost:8080/api/projects/batches?size=4"` | 2 groepen: 4 projecten en 2 projecten |
| `curl -i "http://localhost:8080/api/projects/batches?size=0"` | **400** Bad Request (validatie) |

**De 6 projecten:**

| id | Naam | Status |
|---|---|---|
| 1 | Java AI Lab | IN_PROGRESS |
| 2 | Virtual Threads Benchmark | COMPLETED |
| 3 | Spring AI with Ollama | PLANNED |
| 4 | MCP Server | PLANNED |
| 5 | Resilience Patterns | IN_PROGRESS |
| 6 | OpenAPI Documentation | PLANNED |

---

## 6. Endpoints testen met AI

**Ollama moet draaien** (zie stap 2). De eerste vraag duurt langer, omdat Ollama het model dan in het geheugen laadt. Daarna blijft het 5 minuten geladen.

> ⚠️ **AI-antwoorden zijn niet voorspelbaar.** De formulering verschilt per keer, en een klein model als llama3.2 kan fouten maken of feiten verzinnen. Hieronder staat wat je **ongeveer** kunt verwachten.

### 6.1 Chat: vrije vragen, zonder tools

```bash
ask_chat What are virtual threads in Java
```

✅ Verwacht: een uitleg in een paar zinnen.
⚠️ Let op: llama3.2 noemde hier eens *"Java 11"*, terwijl virtual threads pas sinds **Java 21** definitief zijn. Dat is **hallucinatie**: het klinkt zeker, maar het klopt niet.

```bash
ask_chat What is the status of project 2
```

✅ Verwacht: *"I don't have any information about project 2…"*. Zonder tools kent het model jouw projecten niet.

Het volledige antwoord, met duur en thread:

```bash
curl -s -G localhost:8080/api/chat --data-urlencode "question=What are virtual threads" | jq
```

### 6.2 Structured output: risicoanalyse als JSON

```bash
curl http://localhost:8080/api/projects/1/analysis
curl http://localhost:8080/api/projects/2/analysis
```

✅ Verwacht: JSON in een vaste vorm, bijvoorbeeld:

```json
{
  "risk": "LOW",
  "summary": "The project has been completed, indicating that the delivery risk is low.",
  "recommendedAction": "No further analysis required"
}
```

- `risk` is altijd `LOW`, `MEDIUM` of `HIGH`.
- `summary` is maximaal 300 tekens.
- Een afgerond project (2) krijgt meestal een lager risico dan een lopend project (1).

```bash
curl -i http://localhost:8080/api/projects/999/analysis
```

✅ Verwacht: direct **404**, zonder op het model te wachten.

**Kan ook:** **502 Bad Gateway**. Het model gaf dan twee keer een ongeldig antwoord. In de console zie je regels als `WARN ... Attempt 1/2: model returned invalid analysis ...`. Dat is bewust: liever een duidelijke fout dan stil lege data.

### 6.3 Assistent met tools

De assistent beslist **zelf** welke Java-methode (tool) hij aanroept. Elke tool-call zie je in de console van de applicatie, bijvoorbeeld:

```
INFO ... c.j.javaailab.tools.ProjectTools : Model called tool getProject(projectId=2)
```

| Vraag | Verwachte tool-call(s) | Verwacht antwoord |
|---|---|---|
| `ask_assistant What is the status of project 2` | `getProject(projectId=2)` | *COMPLETED* |
| `ask_assistant Tell me about project 1 and project 5` | `getProject(1)` **en** `getProject(5)` | *Java AI Lab* en *Resilience Patterns*, allebei in progress |
| `ask_assistant Which projects are in progress` | `searchProjects(status=IN_PROGRESS)` | project 1 en 5 |
| `ask_assistant Which projects are planned` | `searchProjects(status=PLANNED)` | project 3, 4 en 6 |
| `ask_assistant How many projects are there in total` | `listAllProjects()` | 6 |
| `ask_assistant What is the capital of France` | **geen** | *Paris* (hiervoor is geen tool nodig) |

**Vergelijk met en zonder tools:**

```bash
ask_chat What is the status of project 2
ask_assistant What is the status of project 2
```

Alleen de assistent kan het juiste antwoord geven, omdat alleen hij via een tool bij de projectdata kan.

> ⚠️ Een klein model kiest soms **geen** of de **verkeerde** tool. Kijk in de console welke tool-calls er echt gebeurden.

---

## 7. Automatische tests

```bash
./mvnw verify
```

✅ Verwacht: `Tests run: 34, Failures: 0` en `BUILD SUCCESS`.

De tests hebben **geen** Ollama nodig: ze testen virtual threads, de projecten en de acties, niet de antwoorden van het model.

---

## 8. Problemen oplossen

| Melding | Oorzaak | Oplossing |
|---|---|---|
| `Connection refused` bij een AI-endpoint | Ollama draait niet | `ollama serve` |
| `model "llama3.2" not found` | Model niet gedownload | `ollama pull llama3.2` |
| `release version 25 not supported` | Terminal gebruikt een oudere Java | `JAVA_HOME` naar Java 25 (zie stap 1) |
| `Port 8080 was already in use` | De app draait al (bijv. in IntelliJ) | Stop de andere instantie |
| **502 Bad Gateway** bij `/analysis` | Het model gaf twee keer een ongeldig antwoord | Opnieuw proberen; zie de `WARN`-regels in de console |
| `WARNING: A restricted method ... netty` | Melding van Java 24+ over een library | Onschuldig, de app werkt gewoon |
| `ERROR ... MacOSDnsServerAddressStreamProvider` | Netty mist een macOS-onderdeel voor DNS | Onschuldig, Ollama draait op `localhost` |
| `What: is: No such file or directory` | Vraag getypt zonder `ask_chat`/`ask_assistant` ervoor | Gebruik de functies uit stap 4 |

---

## 9. Stoppen

- **Applicatie:** `Ctrl+C` in de terminal, of de rode ■ in IntelliJ.
- **Ollama:** `Ctrl+C` in het venster van `ollama serve`, of `brew services stop ollama`.
