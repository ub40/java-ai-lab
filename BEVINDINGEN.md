# Bevindingen: Java AI Lab

Wat ik tijdens het bouwen van dit project heb gemeten, gezien en geleerd. Alle resultaten komen uit mijn eigen tests met de applicatie, met de logs als bewijs. Hoe je de applicatie zelf draait en test, staat in [INSTRUCTION.md](INSTRUCTION.md).

**Opzet:** Java 25 · Spring Boot 4.1 · Spring AI 2.0.2 · Ollama met llama3.2 (3B, lokaal op een Apple M1 Pro, 16 GB) · MCP-clients Claude Code 2.1.285 en Codex 0.164.0.

> ⚠️ AI-antwoorden zijn niet voorspelbaar: dezelfde vraag kan een volgende keer anders uitpakken. De bevindingen beschrijven wat ik zag, niet wat altijd gebeurt.

## Inhoud

1. [Lokaal taalmodel en structured output (llama3.2)](#1-lokaal-taalmodel-en-structured-output-llama32)
2. [Van LLM naar agent: theorie](#2-van-llm-naar-agent-theorie)
3. [Tool calling (llama3.2)](#3-tool-calling-llama32)
4. [Tool calling vs. MCP](#4-tool-calling-vs-mcp)
5. [Claude Code als MCP-client](#5-claude-code-als-mcp-client)
6. [Codex als MCP-client, en drie modellen vergeleken](#6-codex-als-mcp-client-en-drie-modellen-vergeleken)
7. [Privacy: kan een AI-client aan mijn code werken zonder dat die mijn machine verlaat?](#7-privacy-kan-een-ai-client-aan-mijn-code-werken-zonder-dat-die-mijn-machine-verlaat)

---

## 1. Lokaal taalmodel en structured output (llama3.2)


**Bevinding 1: hallucinatie, het model klinkt zeker maar heeft het fout**

Vraag aan `/api/chat`: *"What are virtual threads in Java"*. Het antwoord klonk overtuigend, maar bevatte vier fouten:

| Het model zegt | De waarheid (mijn README, stap 7) |
|---|---|
| "Available in Java 11 and later" | ❌ Definitief sinds **Java 21** (JEP 444) |
| "Shares the same underlying thread's stack" | ❌ Elke virtual thread heeft een **eigen stack, op de heap** |
| "No thread switching" | ❌ Er is wél **mount/unmount** op carriers. Dat heb ik zelf gemeten |
| "Useful for GUI applications" | ❌ Vooral voor **I/O-zware servers** |

Waarom? llama3.2 is een klein model (3 miljard parameters, 2 GB). Het klinkt overtuigend, maar weet veel minder dan grote cloudmodellen. Dat is het nadeel "minder slim" uit vraag 11 in mijn `TODO.md`.

**Bevinding 2: structured output, het model gaf stilletjes een leeg resultaat**

Met `.entity(ProjectAnalysis.class)` vraag ik het model om een analyse als Java-record (`risk`, `summary`, `recommendedAction`).

| Request | Resultaat |
|---|---|
| `GET /api/projects/1/analysis` | ✅ `{"risk":"MEDIUM","summary":"Delays in AI model development and integration","recommendedAction":"Re-evaluate project timeline and resource allocation"}` |
| `GET /api/projects/2/analysis` | ❌ `{"risk":null,"summary":null,"recommendedAction":null}`, met HTTP **200** en **geen foutmelding** |

Wat er gebeurde:
- Het model gaf wel JSON terug, maar **niet in de vorm van mijn record**. Kleine modellen geven bijvoorbeeld het schema zelf terug, of verpakken het antwoord in een extra laag (`{"analysis": {...}}`).
- Jackson vond de verwachte velden niet, **negeerde de onbekende velden stilletjes**, en maakte een record met alleen `null`.
- Mijn code dacht dus dat er een geldig antwoord was. **Dat is gevaarlijker dan een crash**: een fout die je niet ziet, komt in je data of bij de gebruiker terecht.

**De les: vertrouw AI-output nooit blind.**
- Behandel het antwoord van een model als **input van een onbekende gebruiker**: altijd controleren voordat je ermee verder gaat.
- Daarom heeft `ProjectAnalysis` validatie-annotaties (`@NotNull`, `@NotBlank`, `@Size`). Maar annotaties doen niets vanzelf: een `Validator` moet ze **echt controleren**.
- Mijn oplossing (stap 3): na elk antwoord valideren, bij een fout **één keer opnieuw proberen**, en daarna een duidelijke **502 Bad Gateway** in plaats van stil `null`.
- Een `enum` voor `risk` (in plaats van een `String`) beperkt het model tot `LOW`, `MEDIUM` en `HIGH`.

**Bevinding 3: in de logs gezien waarom het misging, en opgelost**

Met `SimpleLoggerAdvisor` en `DEBUG`-logging zag ik wat Spring AI naar het model stuurde, en wat het model echt antwoordde.

*Wat Spring AI meestuurde:* mijn prompt **plus** een instructie *"Your response should be in JSON format… Here is the JSON Schema instance your output must adhere to"*, met het volledige JSON-schema van `ProjectAnalysis`.

*Wat llama3.2 antwoordde bij project 2:*

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "recommendedAction": "No action required",
    "risk": "LOW",
    "summary": "Project completed without any issues"
  },
  "required": ["recommendedAction", "risk", "summary"]
}
```

**Het model heeft het schema overgeschreven en zijn antwoorden ín `properties` gezet.** De waarden waren eigenlijk goed (`LOW` past bij een afgerond project), maar ze zaten een laag te diep. Jackson zocht `risk` op het hoogste niveau, vond alleen `$schema`, `type` en `properties`, en gaf `null` terug. Kleine modellen **kopiëren** vaak het voorbeeld in plaats van het te volgen.

**Twee oplossingen, en wat ze opleverden** (getest met dezelfde prompt, project 2):

| Aanpak | Vorm van het antwoord | Inhoud | Geldig? |
|---|---|---|---|
| Standaard Spring AI (schema als tekst in de prompt) | ❌ Schema overgeschreven, waarden in `properties` | Waarden zelf goed | ❌ 0 van 2 (alles `null`) |
| Alleen Ollama's **native JSON-modus** (`format` = schema) | ✅ Juiste vorm | ❌ `summary` van meer dan 1000 tekens (faalt op `@Size(max = 300)`) en **verzonnen feiten** (*"thorough testing was not performed"*), terwijl het model alleen naam en status kent | ❌ |
| Native JSON-modus **+ betere prompt** (*"at most two short sentences"*, *"do not invent facts"*) | ✅ Juiste vorm | ✅ Kort en bij de feiten (131–180 tekens) | ✅ 3 van 3 |

Alleen een betere prompt (zonder native JSON-modus) heb ik niet getest.

**Wat de twee oplossingen doen:**
- **Native JSON-modus (*constrained decoding*):** Ollama krijgt het schema mee als `format`-optie, en dwingt het model om **alleen** JSON in die vorm te maken. Het model kán het schema dan niet meer overschrijven. In Spring AI: `.options(OllamaChatOptions.builder().format(new BeanOutputConverter<>(ProjectAnalysis.class).getJsonSchemaMap()))`.
- **Prompt engineering:** de vorm afdwingen is niet genoeg. De **inhoud** moet je sturen met duidelijke instructies (lengte, alleen gegeven feiten gebruiken).
- **Validatie en retry blijven staan als vangnet.** Zonder `@Size(max = 300)` had ik de te lange, deels verzonnen samenvatting niet eens opgemerkt.

**De les:**
1. **Kijk in de logs naar het ruwe antwoord.** Zonder `SimpleLoggerAdvisor` had ik alleen `null` gezien, zonder te weten waarom.
2. **Vorm en inhoud zijn twee verschillende problemen.** De native JSON-modus lost de vorm op; de prompt en de validatie bewaken de inhoud.
3. **Meerdere verdedigingslagen:** native JSON-modus → betere prompt → validatie → retry → duidelijke 502.


## 2. Van LLM naar agent: theorie


### Van LLM naar agent: vier niveaus

| Niveau | Wie bepaalt de volgende stap? | In mijn project |
|---|---|---|
| **1. LLM** | Niemand: vraag in, tekst uit | `/api/chat`: *"I don't have any information about project 2"* |
| **2. LLM + tools** | Het model kiest **één of enkele** tool-calls | `/api/assistant`: roept `getProject(2)` aan |
| **3. Agentic workflow** | **De code** bepaalt de stappen; het model doet een deel van het werk binnen elke stap | `ProjectAnalysisService`: de code haalt het project op → vraagt het model → valideert → probeert opnieuw → 502 |
| **4. Agent** | **Het model** bepaalt in een loop zelf de volgende stap, tot het doel bereikt is | Bijna: bij *"project 1 en 5"* koos het model zelf **2** tool-calls |

**Kern:** hoe hoger het niveau, hoe meer het **model** beslist en hoe minder **jouw code**.

### Agentic workflow vs. agent

- **Workflow:** de stappen staan vast in code. Het model is een slim onderdeel *binnen* een stap (samenvatten, classificeren, analyseren).
  - Bekende patronen: **prompt chaining** (stap na stap, de output van de ene is de input van de volgende), **routing** (het model kiest welke vaste route), **parallelisatie** (meerdere calls tegelijk, zoals mijn `GenerateAllSummaries`), **evaluator-optimizer** (een tweede call beoordeelt en verbetert de eerste).
- **Agent:** het model krijgt een **doel** en tools, en beslist zelf: *denken → tool kiezen → uitvoeren → resultaat bekijken → volgende stap → …* tot het klaar is.

**De belangrijkste vraag uit mijn `TODO.md`:** *wanneer een vaste workflow, en wanneer laat je het model beslissen?*

| Kies een **workflow** (code beslist) als… | Kies een **agent** (model beslist) als… |
|---|---|
| de stappen bekend en steeds hetzelfde zijn | de stappen vooraf niet te voorspellen zijn |
| er regels, geld of wetgeving in het spel zijn | het om open, verkennend werk gaat (zoeken, onderzoeken) |
| het testbaar en voorspelbaar moet zijn | een fout goedkoop en te herstellen is |
| je kosten en tijd strak wilt begrenzen | flexibiliteit belangrijker is dan voorspelbaarheid |

**Meer autonomie is niet automatisch beter.** Elke stap die het model zelf kiest, is een stap die je minder goed kunt testen, voorspellen en uitleggen.

### Autonome systemen: hoeveel vrijheid geef je?

Autonomie is geen aan/uit-knop, maar een schaal:

1. **Voorstellen:** het model stelt iets voor, een mens doet het. *(Code-suggesties in je IDE.)*
2. **Uitvoeren na goedkeuring:** het model wil iets doen, een mens zegt ja of nee. *(Claude Code vraagt toestemming voor een commando.)*
3. **Uitvoeren en melden:** het model doet het, en laat achteraf zien wat het deed.
4. **Volledig autonoom:** het model doet alles zelf, zonder mens.

**Risico's van veel autonomie:**
- **Oneindige loops:** de agent blijft tools aanroepen zonder klaar te komen. Dat kost tijd en geld.
- **Onomkeerbare acties:** iets verwijderen, betalen, versturen of pushen.
- **Opgestapelde fouten:** een fout in stap 2 wordt in stap 3, 4 en 5 als waarheid gebruikt (vergelijk mijn hallucinatie-bevinding).
- **Prompt injection:** tekst in een tool-resultaat (een e-mail, een webpagina) die het model opdrachten geeft.

**Grenzen die je instelt:**
- maximaal aantal stappen of tool-calls, plus een timeout en een kostenbudget
- **least privilege:** de agent krijgt alleen de tools die hij echt nodig heeft (mijn `ProjectTools` kan alleen *lezen*, niets wijzigen)
- logging van elke tool-call (*observability*: mijn logregel `Model called tool getProject(...)`)

### Human-in-the-loop

Een **mens beslist** op de momenten die ertoe doen; het model doet de rest.

**Wanneer:**
- vóór **onomkeerbare** acties (verwijderen, betalen, deployen, pushen)
- als het model **onzeker** is, of iets buiten de regels valt
- bij beslissingen met **juridische of ethische** gevolgen

**Hoe:**
- **Goedkeuren:** de agent pauzeert en wacht op *ja/nee* (zoals de toestemmingsvraag van Claude Code)
- **Reviewen:** de mens controleert de output vóór die gebruikt wordt (code review van AI-code)
- **Escaleren:** de agent geeft het werk door aan een mens als hij er niet uitkomt

**Mijn eigen ervaring:** tijdens dit project wilde ik **zelf** committen en pushen, en niet de AI. Dat is human-in-the-loop: de AI schrijft en stelt voor, maar de onomkeerbare, zichtbare stap (wat er onder mijn naam op GitHub komt) neem ik zelf.


## 3. Tool calling (llama3.2)


**Bevinding 4: met tools gebruikt het model echte data, zonder tools niet**

| Vraag: *"What is the status of project 2?"* | Antwoord |
|---|---|
| `/api/chat` (zonder tools) | *"I don't have any information about a specific project 2…"* (eerlijk, niets verzonnen) |
| `/api/assistant` (met tools) | *"The status of project 2 is COMPLETED."* ✅ via `getProject(2)` |

- Bij *"Tell me about project 1 and project 5"* riep het model **zelf twee keer** `getProject` aan: het eerste agent-gedrag.
- Bij *"What is the capital of France"* gebruikte het **geen** tool: het model kiest zelf wanneer een tool zinvol is.

**Bevinding 5: een optionele tool-parameter ging mis**

Mijn eerste `searchProjects`-tool had een **optionele** `status` met de beschrijving *"leave the status empty to list all projects"*.

| Vraag | Resultaat |
|---|---|
| *"Which projects are in progress?"* | ✅ `searchProjects(IN_PROGRESS)` → project 1 en 5 |
| *"Which projects are planned?"* | ✅ `searchProjects(PLANNED)` → project 3, 4 en 6 |
| *"What is the status of project 3?"* | ✅ `getProject(3)` → PLANNED |
| *"How many projects are there in total?"* | ❌ *"I was unable to retrieve the total number of projects… The tool returned no data"* |

**Wat er gebeurde:** het model stuurde `{"status": ""}`, een **lege tekst**, in plaats van de status weg te laten. Het nam *"leave empty"* letterlijk. `""` is geen geldige `ProjectStatus`, dus de omzetting faalde **voordat mijn methode werd aangeroepen**. Spring AI gaf de fout terug aan het model, en dat zei eerlijk dat er geen data was (de system prompt *"if a tool returns no data, say so"* werkte).

**De oplossing: één tool, één duidelijke taak.**
- `searchProjects(status)`: status is nu **verplicht**
- `listAllProjects()`: **zonder** parameters, dus niets om fout in te vullen

Getest (elke vraag 2×): `listAllProjects` 2/2 ✅, `searchProjects(IN_PROGRESS)` 2/2 ✅, `getProject(3)` 2/2 ✅, maar `searchProjects(PLANNED)` 1/2 ⚠️: één keer gebruikte het model **helemaal geen tool**. Een klein model blijft onvoorspelbaar.

**De lessen:**
1. **Tool-ontwerp is ook prompt engineering.** De beschrijving van een tool stuurt het model, letterlijk.
2. **"Weglaten", `null` en `""` zijn voor een model niet hetzelfde.** Vermijd optionele parameters bij kleine modellen.
3. **Liever kleine tools met één taak** dan één tool die twee dingen doet.
4. **Ook een goed ontworpen agent kiest soms geen of de verkeerde tool.** Waar het ertoe doet, hoort een vaste workflow (code beslist), niet het model.


## 4. Tool calling vs. MCP


**Begrippen:** `AssistantService` is geen server, maar een klasse **binnen mijn eigen app** die met Ollama praat. **Ollama** is de server waar het taalmodel draait.

### Wat ik tot nu toe gebouwd heb

Elke laag bouwt op de vorige:

| # | Wat | Endpoint / klasse | Wat het laat zien |
|---|---|---|---|
| 1 | **Virtual threads** | `/api/thread`, `/api/thread/slow` | Wachten is goedkoop: 20 s → 2 s |
| 2 | **Modern Java + services** | `ProjectService`, `AiActionService`, sealed `AiAction` | Een vaste set acties met records, pattern matching en Java 25 |
| 3 | **LLM-chat** (Spring AI + Ollama) | `/api/chat` → `ChatService` | Het model beantwoordt vragen uit eigen kennis, en hallucineert soms |
| 4 | **Structured output** | `/api/projects/{id}/analysis` | Het model antwoordt in een vast **record**, gevalideerd, met native JSON-modus |
| 5 | **Tool calling** | `/api/assistant` → `AssistantService` + `ProjectTools` | Het model roept **mijn Java-methodes** aan, en gebruikt echte data |
| 6 | **Security** | `requestProjectDeletion` | Het model **vraagt**, mijn code **beslist** |
| 7 | **MCP-server** | `/mcp` → `McpServerConfig` + **dezelfde** `ProjectTools` | **Andere AI-applicaties** kunnen mijn tools gebruiken |

**De rode draad:** `ProjectTools` roept `AiActionService` aan, en die gebruikt `ProjectService`. Alle lagen gebruiken **dezelfde** logica.

### Tool calling: mijn app is de baas

```
Gebruiker
   │  ask_assistant "status van project 2?"
   ▼
┌──────────────── mijn app (java-ai-lab) ────────────────┐
│  AssistantController → AssistantService                │
│        │  vraag + tool-beschrijvingen                  │
│        ▼                                               │
│     Ollama / llama3.2  ── "roep getProject(2) aan" ──┐ │
│        ▲                                           │   │
│        └──── resultaat ──── ProjectTools.getProject ◀┘ │
└────────────────────────────────────────────────────────┘
```

- **Mijn app** kiest het model (llama3.2), de system prompt en de tools.
- De tools zijn **alleen** beschikbaar voor **mijn eigen** assistent.

### MCP: mijn app is de leverancier van tools

```
Claude Code / Claude Desktop / IDE      (MCP-client, met een eigen model)
   │
   │  MCP: tools/list, tools/call  (JSON-RPC over HTTP)
   ▼
┌──────────────── mijn app (java-ai-lab) ────────────────┐
│  /mcp → McpServerConfig → ProjectTools.getProject      │
└────────────────────────────────────────────────────────┘
```

- **Een andere applicatie** (met een eigen model, bijv. Claude) is de baas.
- **Mijn app** levert alleen de tools. Er zit **geen LLM in mijn app** voor deze route. Met `mcp_call` in de terminal was ik zelf de client, zonder model.

### Naast elkaar

| | Tool calling (`/api/assistant`) | MCP (`/mcp`) |
|---|---|---|
| **Wie heeft het model?** | Mijn app (llama3.2 via Ollama) | De client (bijv. Claude in Claude Code) |
| **Wie gebruikt de tools?** | Alleen mijn eigen assistent | **Elke** applicatie die MCP spreekt |
| **Hoe worden tools aangeboden?** | Intern, via `.defaultTools(...)` | Via een **standaardprotocol** over HTTP |
| **Wie bepaalt prompt en gedrag?** | Ik | De client |
| **Ollama nodig?** | Ja | Nee |
| **Code die ik schreef** | `AssistantService` + `ProjectTools` | `McpServerConfig` (~15 regels) + **dezelfde** `ProjectTools` |

### Waarom MCP? Het probleem dat het oplost

**Zonder MCP:** 5 AI-applicaties (Claude, ChatGPT, een IDE, een chatbot, een agent) en 10 systemen (mijn projecten, Jira, GitHub, een database…). Elke applicatie heeft voor **elk** systeem een eigen koppeling nodig: 5 × 10 = **50 koppelingen**.

**Met MCP:** elk systeem maakt **één** MCP-server, en elke applicatie spreekt **één** protocol: 5 + 10 = **15**.

Dezelfde gedachte als **USB**: één aansluiting, en elk apparaat past.

### Wat MCP mij concreet oplevert

| Verbetering | Zonder MCP (alleen tool calling) | Met MCP |
|---|---|---|
| **Hergebruik** | Mijn tools werken alleen in mijn eigen assistent | Claude Code, Claude Desktop, IDE's… kunnen ze gebruiken, zonder dat ik code voor hen schrijf |
| **Keuze van model** | Vast aan llama3.2 (klein, maakt fouten) | De client kiest. Claude is veel sterker dan llama3.2, dus minder fouten bij het kiezen van tools |
| **Ontkoppeling** | Mijn app moet zelf een LLM, prompts en chatlogica beheren | Mijn app levert alleen **mogelijkheden**. De AI-kant ligt bij de client |
| **Standaard** | Spring AI-specifiek | Een open protocol, met dezelfde berichten (`tools/list`, `tools/call`) voor iedereen |
| **Ontdekbaar** | Een client moet mijn tools vooraf kennen | `tools/list`: een client vraagt zelf wat er is |

### Wat MCP níet oplost

| Punt | Uitleg |
|---|---|
| **Beveiliging blijft mijn taak** | Elke client kan mijn tools aanroepen. Daarom blijft `requestProjectDeletion` (niets verwijderen zonder mens) belangrijk. In productie is **authenticatie** nodig: mijn `/mcp` staat nu open voor iedereen op het netwerk |
| **Privacy** | De tool-resultaten gaan naar het model van de client. Bij Claude Code dus naar de servers van Anthropic |
| **Het protocol verandert snel** | Zelf gezien: zonder `spring.ai.mcp.server.protocol=STREAMABLE` startte de server in de oudere **SSE**-modus (`/sse`), en gaf `/mcp` een **404**, terwijl de documentatie in de library *streamable* als standaard noemde |

### Hebben beide nog zin? Ja, ze vullen elkaar aan

| Route | Wanneer |
|---|---|
| **`/api/assistant`** (tool calling) | Ik wil alles zelf bepalen: model, prompt, privacy. Bijv. als de data mijn eigen omgeving niet uit mag (lokaal model) |
| **`/mcp`** (MCP) | Mijn tools moeten bruikbaar zijn in AI-tools die mensen al gebruiken, zonder dat ik zelf een chat-app bouw |

### Mijn architectuur in één zin

> *"Ik heb één service-laag met een sealed set acties. Die bied ik aan via een REST-API, via tool calling aan mijn eigen lokale assistent, en via MCP aan externe AI-clients. Allemaal met dezelfde Java-code."*


## 5. Claude Code als MCP-client


**Bevinding 6: Claude Code gebruikt mijn Java-tools via MCP, zonder één regel extra code**

Koppeling: `claude mcp add --transport http java-ai-lab http://localhost:8080/mcp` → in `/mcp`: `✔ java-ai-lab · 4 tools`.

**Resultaten** (zelfde vragen als bij mijn eigen assistent met llama3.2):

| Vraag | Tool-call (uit mijn Java-log) | Antwoord van Claude | llama3.2 (`ask_assistant`) |
|---|---|---|---|
| *Which projects are in progress?* | `searchProjects(status=IN_PROGRESS)` | ✅ project 1 en 5 | ✅ |
| *Which projects are planned?* | `searchProjects(status=PLANNED)` | ✅ project 3, 4 en 6 | ⚠️ 1 van 2 keer geen tool |
| *How many projects are there in total?* | `listAllProjects()` | ✅ 6, netjes uitgesplitst per status | ✅ (na het opsplitsen van de tools) |
| *Tell me about project 1 and project 5* | `getProject(projectId=1)` + `getProject(projectId=5)` | ✅ Java AI Lab en Resilience Patterns | ✅ |
| *What is the status of project 999?* | `getProject(projectId=999)` → *"Project 999 not found"* | ✅ *"bestaat niet"*, niets verzonnen | niet getest |
| *Delete project 4* | `requestProjectDeletion(projectId=4)` → `WARN … NOT executed` | ✅ *"niet verwijderd"*, citeert mijn tool letterlijk | ✅ maar verzon een "project manager" |

**Het bewijs in mijn eigen log** (de client zegt alleen *"Called java-ai-lab"*; wat er echt gebeurde, staat bij mij):

```
Client initialize request - Protocol: 2025-11-25 ... Info: Implementation[name=claude-code, version=2.1.285 ...]
Model called tool searchProjects(status=IN_PROGRESS)
Model called tool searchProjects(status=PLANNED)
Model called tool listAllProjects()
Model called tool getProject(projectId=1)
Model called tool getProject(projectId=5)
Model called tool getProject(projectId=999)
WARN  Model requested deletion of project 4: NOT executed, human approval required
```

**Wat opvalt:**

| Punt | Uitleg |
|---|---|
| **Betere toolkeuze dan llama3.2** | Ook bij *"planned"*. Dit is het voordeel *"keuze van model"*: de server blijft hetzelfde, de client brengt een sterker model mee |
| **Eerlijk over de grenzen** | *"De tool geeft alleen id, naam en status terug… daarover kan ik niets zeggen zonder te gokken."* Geen hallucinatie |
| **Geen verzonnen details** | Bij de delete citeert Claude mijn tool-resultaat letterlijk; llama3.2 verzon er een "project manager" bij |
| **Meedenken met context** | Claude merkte op dat project 4 *"MCP Server"* nog op PLANNED staat, terwijl ik die al bouw (branch `feature/mcp-server`). De client combineert tool-data met context uit mijn projectmap |
| **Nettere foutafhandeling** | `ProjectNotFoundException` kwam als nette foutmelding bij Claude aan, niet als crash |
| **Nieuwere protocolversie** | Claude Code verbond met protocol **`2025-11-25`**; mijn eigen `curl`-test gebruikte `2025-06-18`. Mijn server accepteerde allebei. *MCP evolution* in de praktijk |

**Bevinding 7: Claude Code vroeg géén toestemming voor de tools**

Ik verwachtte een *"Yes"*-vraag vóór elke tool-call (zeker bij `requestProjectDeletion`), maar die kwam niet. Waarschijnlijk door de **permissie-instellingen van mijn Claude Code** (permissiemodus of een toestemmingsregel; te controleren met `/permissions`).

**De les:**

> **De goedkeuring in de client is geen beveiliging voor mijn server.** Of een mens "Yes" moet zeggen, bepaalt de **gebruiker van de client** met zijn eigen instellingen, niet ik. Daarom is mijn ontwerp goed: `requestProjectDeletion` verwijdert **nooit** iets, welke client de tool ook aanroept en hoe die ook is ingesteld. **Beveilig in de server, niet in de client.**

**Bijzaak: Tomcat-fouten in de log.** Bij nieuwe verbindingen verscheen `ERROR … NioEndpoint : Error setting socket options … java.net.SocketException: Invalid argument … setSoLinger`. De tool-calls werkten daarna gewoon, dus het lijkt onschuldig, maar de oorzaak heb ik nog niet uitgezocht.


## 6. Codex als MCP-client, en drie modellen vergeleken


**Bevinding 8: Codex gebruikt dezelfde server, maar kiest soms een andere route**

Koppeling: `codex mcp add java-ai-lab --url http://localhost:8080/mcp` → in `/mcp`: `java-ai-lab: connected (4 tools)`.

**Uit mijn Java-log:**

```
Client initialize request - Protocol: 2025-06-18 ... Info: Implementation[name=codex-mcp-client, title=Codex, version=0.164.0 ...]
Model called tool searchProjects(status=IN_PROGRESS)
Model called tool searchProjects(status=PLANNED)
Model called tool searchProjects(status=COMPLETED)       ← bij "How many projects in total?"
Model called tool getProject(projectId=1)
Model called tool getProject(projectId=5)
Model called tool getProject(projectId=999)
WARN  Model requested deletion of project 4: NOT executed, human approval required
```

**Wat opvalt bij Codex:**

| Punt | Uitleg |
|---|---|
| **Eerst zelf rondkijken** | Bij de eerste vraag doorzocht Codex eerst mijn **projectmap** (`rg --files … README, TODO, pom.xml`) voordat het de MCP-tool gebruikte. Agent-gedrag: het kiest zelf zijn aanpak, en leest daarbij lokale bestanden (privacy!) |
| **"Total" via een omweg** | Codex riep **niet** `listAllProjects()` aan, maar alleen `searchProjects(COMPLETED)`, en telde dat op bij de resultaten van de **vorige vragen** (3 planned + 2 in progress). Het antwoord (6) klopte, maar was deels gebaseerd op **oude gegevens uit het gesprek**. Als de data intussen veranderd was, was het antwoord fout geweest |
| **Fouten zichtbaar** | Bij project 999 toonde Codex `Failed java-ai-lab.getProject → Project 999 not found`: mijn server meldt een fout netjes als fout (`isError`) |
| **Geen verzonnen details** | Bij de delete: *"has not been deleted… requires human approval"*. Correct, net als Claude |
| **Ook geen toestemmingsvraag** | Net als bij Claude Code vroeg Codex niet om toestemming. Dit bevestigt bevinding 7: **beveilig in de server, niet in de client** |
| **Oudere protocolversie** | Codex verbond met **`2025-06-18`**, Claude Code met **`2025-11-25`**. Mijn server werkte met allebei |

**Bevinding 9: dezelfde server, drie modellen**

| Vraag | Juiste tool | llama3.2 (eigen assistent) | Claude Code | Codex |
|---|---|---|---|---|
| In progress | `searchProjects(IN_PROGRESS)` | ✅ | ✅ | ✅ (na eerst de projectmap te doorzoeken) |
| Planned | `searchProjects(PLANNED)` | ⚠️ 1 van 2 keer geen tool | ✅ | ✅ |
| Total | `listAllProjects()` | ✅ | ✅ | ⚠️ `searchProjects(COMPLETED)` + oude resultaten uit het gesprek |
| Project 1 en 5 | 2× `getProject` | ✅ | ✅ | ✅ |
| Project 999 | `getProject(999)` → fout | niet getest | ✅ | ✅ |
| Delete project 4 | `requestProjectDeletion(4)` | ✅ maar verzon een "project manager" | ✅ | ✅ |
| Toestemming gevraagd? | – | n.v.t. | ❌ nee | ❌ nee |
| Protocolversie | – | n.v.t. (geen MCP) | `2025-11-25` | `2025-06-18` |
| Waar de data heen gaat | – | **blijft lokaal** (Ollama) | Anthropic | OpenAI |

**De lessen:**

1. **Eén MCP-server, meerdere clients:** zonder extra code werkten Claude Code en Codex allebei met mijn Java-tools. Dat is de belofte van MCP, en het klopt.
2. **Grotere modellen kiezen beter**, maar **niet perfect en niet hetzelfde**: Codex nam bij "total" een slimme maar riskante omweg via oude gesprekscontext.
3. **Agents doen meer dan je vraagt:** Codex doorzocht eerst mijn projectmap. Handig, maar het betekent ook dat lokale bestanden in de context van het model (en dus bij de aanbieder) terechtkomen.
4. **Beveiliging en controle horen in de server:** geen van beide clients vroeg om toestemming. Mijn `requestProjectDeletion` hield het toch veilig.
5. **Observability aan de serverkant is onmisbaar:** alleen in mijn eigen log zag ik dat Codex `searchProjects(COMPLETED)` gebruikte in plaats van `listAllProjects()`. Het antwoord zelf liet dat niet zien.
6. **Afweging lokaal vs. cloud:** llama3.2 maakt meer fouten, maar de data blijft op mijn machine. Claude en Codex zijn slimmer, maar de data gaat naar de aanbieder.


## 7. Privacy: kan een AI-client aan mijn code werken zonder dat die mijn machine verlaat?


**Mijn vraag:** kan ik Claude Code of Codex features laten bouwen, services laten schrijven of bugs laten zoeken, **zonder** dat mijn code naar hun servers gaat?

**Kort antwoord: nee, niet met een cloudmodel.** Om code te schrijven of een bug te vinden, moet het model de relevante code **zien**, en dat model draait op de servers van de aanbieder.

### Waarom MCP daar niets aan verandert

```
Claude / Codex  = het "brein" (het model, op hun servers)
      │  vraagt om tools
      ▼
MCP-server (mijn app)  = de "handen"
      │  geeft resultaten terug
      ▼
Claude / Codex  ← alles wat een tool teruggeeft, gaat het model in, dus naar hun servers
```

| Punt | Uitleg |
|---|---|
| **llama3.2 zit niet in deze keten** | llama3.2 gebruikt alleen mijn eigen `/api/assistant`. Bij MCP is Claude of Codex het brein |
| **Tool-resultaten gaan naar het model** | Een tool als `readFile` of `getCode` stuurt mijn code dus alsnog naar Anthropic of OpenAI |
| **Code verbeteren = code lezen** | Zonder mijn code te zien, kan het model mijn code niet verbeteren |
| **Agents lezen ook zelf** | Codex doorzocht zelf mijn projectmap vóór het mijn MCP-tool gebruikte (bevinding 8) |

**MCP bepaalt *hoe* data bij het model komt, niet *of*.**

### Waarom de code bij een cloudmodel de machine moet verlaten

| Reden | Uitleg |
|---|---|
| **Het model staat niet op mijn machine** | Claude en de modellen achter Codex draaien op veel krachtige GPU's in de datacenters van de aanbieder |
| **Het model wordt niet uitgedeeld** | De *gewichten* van deze modellen zijn niet te downloaden. Mijn Mac zou er ook niet genoeg geheugen voor hebben |
| **Het model moet de tekst zien** | Het kan alleen werken met tekst die het ontvangt. Dus moet mijn code naar de plek waar het model draait |
| **Versleutelen helpt niet** | Het model moet de code in leesbare vorm verwerken |

**Een contract voorkomt dus niet dat de code mijn machine verlaat.** Het regelt alleen wat er **daarna** gebeurt: hoe lang het bewaard wordt, of het voor training gebruikt mag worden, en wie erbij kan. Wil ik dat de code **echt binnen blijft**, dan moet het model **naar de code toe komen**, in plaats van andersom.

### Waar draait het model, en waar gaat mijn code heen?

| Waar draait het model? | Verlaat de code… | Voorbeeld |
|---|---|---|
| **Op mijn eigen laptop** | ❌ niets, alles blijft op mijn machine | Ollama + een open model, zoals mijn llama3.2 |
| **Op een server van mijn eigen bedrijf** (*self-hosted / on-premise*) | mijn laptop wel, maar **niet het bedrijfsnetwerk** | Een open model op een eigen GPU-server, binnen het netwerk van het bedrijf |
| **Bij een cloudaanbieder** | ✅ ja, de code gaat naar de aanbieder, ook met een zakelijk contract | Claude Code, Codex |

Lokaal of self-hosted kan alleen met **open modellen** (*open-weight*: de gewichten zijn vrij te downloaden, zoals Llama). Claude en de modellen achter Codex kun je niet zelf hosten.

### Wat ik wél kan doen: de opties

| Optie | Hoe | Blijft mijn code binnen? | Kwaliteit |
|---|---|---|---|
| **1. Lokaal model als brein** | Een coding-agent met een model via **Ollama** in plaats van Claude/Codex (Codex heeft hiervoor `codex --oss`; niet zelf getest) | ✅ **Ja, volledig** | ❌ Veel lager. Zelf gezien met llama3.2. Voor coderen zijn grotere modellen nodig, en 16 GB RAM is daarvoor krap |
| **2. Self-hosted open model** | Een groter open model op een GPU-server **van het bedrijf** | ✅ **Ja, binnen het bedrijfsnetwerk** | 🟡 Beter dan lokaal, maar meestal lager dan de beste cloudmodellen. Kost hardware en beheer |
| **3. Minder data blootstellen** | MCP-tools die alleen **resultaten** teruggeven, niet de code. Bijv. `runTests` → *"3 tests failed: …"*, of lokaal statische analyse draaien (SpotBugs, SonarQube) en alleen de bevindingen teruggeven | 🟡 **Deels** | 🟡 Goed om problemen te *vinden*. Om ze te *repareren* moet het model de code alsnog zien |
| **4. Toegang in de client beperken** | In Claude Code met `/permissions` regels instellen dat bepaalde bestanden niet gelezen mogen worden (bijv. `.env`, geheimen) | 🟡 **Deels**: wat wel gelezen mag, gaat nog steeds mee | ✅ Hoog |
| **5. Contractuele afspraken** | Zakelijk abonnement, of een cloudmodel via de cloud van het bedrijf, met afspraken over bewaren en niet trainen | ❌ **Nee**, de code verlaat de machine, maar wordt **onder contract** verwerkt | ✅ Hoog |

Wat er precies bewaard of gebruikt wordt, verschilt per aanbieder en abonnement. Bij een werkgever of klant: altijd **het AI-beleid van het bedrijf** volgen.

### Het dilemma

| | Lokaal / self-hosted model | Cloudmodel (Claude/Codex) |
|---|---|---|
| **Privacy** | ✅ Code blijft op mijn machine of binnen het bedrijf | ❌ Code gaat naar de aanbieder |
| **Kwaliteit** | ❌ Lager (zelf gezien: hallucinaties, verkeerde tools) | ✅ Hoog (zelf gezien: 6 van 6 goed bij Claude) |
| **Hardware en beheer** | ❌ Zware machine of server nodig, zelf beheren | ✅ Niets nodig |

**Je kunt niet allebei maximaal hebben.** In de praktijk per situatie:

| Situatie | Gebruikelijke keuze |
|---|---|
| Openbare of niet-gevoelige code (zoals mijn `java-ai-lab`) | Cloudmodel: de beste kwaliteit |
| Klantcode bij een bedrijf | Zakelijk abonnement met afspraken (optie 5) plus beperkingen in de client (optie 4), **of** self-hosted (optie 2) als de code het bedrijf niet uit mag |
| Zeer gevoelige code (overheid, medisch, defensie) | Alleen lokaal of self-hosted (optie 1 of 2), en een lagere kwaliteit accepteren |

### Welke modellen kun je zelf hosten?

Modellen die je zelf mag draaien heten **open-weight-modellen**: de gewichten zijn te downloaden en draaien op eigen hardware. Mijn llama3.2 is er één.

**Bekende families:**

| Maker | Modellen | Sterk in | Opmerking |
|---|---|---|---|
| **Meta** | Llama | Algemeen | Gebruik ik al (llama3.2). Eigen licentie met voorwaarden |
| **Alibaba** | Qwen, **Qwen-Coder** | **Code**, meertalig | De Coder-varianten zijn speciaal voor programmeren |
| **DeepSeek** | DeepSeek, **DeepSeek-Coder** | **Code**, redeneren | De grootste versies zijn erg groot |
| **Mistral** (Frans) | Mistral, Mixtral, **Codestral**, **Devstral** | **Code**, Europees | Licenties verschillen per model |
| **Google** | Gemma | Algemeen, klein | Goed op een laptop |
| **Microsoft** | Phi | Klein en efficiënt | Goed op een laptop |
| **OpenAI** | gpt-oss | Algemeen, redeneren | Het open model van OpenAI; dit gebruikt `codex --oss` |
| **IBM** | Granite | Zakelijk, code | Gericht op bedrijven |

**Software om ze te draaien:**

| Tool | Waarvoor |
|---|---|
| **Ollama** | Makkelijk, op een laptop of server. Gebruik ik al |
| **LM Studio** | Hetzelfde, maar met een grafische interface |
| **llama.cpp** | De techniek onder veel van deze tools. Licht en snel |
| **vLLM** | Voor **servers**: veel gebruikers tegelijk, hoge doorvoer. Wat bedrijven gebruiken bij self-hosting |

**Hardware: hoe groot is groot?** Modellen worden gemeten in **parameters** (B = miljard). Hoe meer, hoe slimmer, en hoe meer geheugen nodig.

| Grootte | Voorbeeld | Waar het draait | Kwaliteit voor code |
|---|---|---|---|
| **1–8B** | llama3.2 (3B) | Laptop, ook mijn M1 Pro (16 GB) | ❌ Zelf gezien: hallucinaties, verkeerde tools |
| **14–32B** | middelgrote Coder-modellen | Laptop met 32–64 GB, of één goede GPU | 🟡 Bruikbaar voor eenvoudiger codeerwerk |
| **70B en groter** | grote Llama-, Qwen- en DeepSeek-versies | Server met een of meer zware GPU's | ✅ Kan in de buurt komen van cloudmodellen |

**Kanttekeningen:**

| Punt | Uitleg |
|---|---|
| **Nieuwe versies** | Er komen steeds nieuwe modellen uit. Actueel overzicht: `ollama.com/library` of Hugging Face |
| **Licentie** | Altijd lezen, zeker voor zakelijk gebruik. Niet elk "open" model mag vrij commercieel gebruikt worden |
| **Kwaliteit** | Ook het beste open model is meestal net minder goed dan de beste cloudmodellen (Claude, GPT), zeker bij complexe codeertaken. Het verschil wordt wel kleiner |
