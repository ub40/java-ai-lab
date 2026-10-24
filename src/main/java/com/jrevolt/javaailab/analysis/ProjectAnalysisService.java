package com.jrevolt.javaailab.analysis;

import com.jrevolt.javaailab.project.ProjectInfo;
import com.jrevolt.javaailab.project.ProjectService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Laat een taalmodel het leveringsrisico van een project beoordelen, en geeft het antwoord
 * terug als {@link ProjectAnalysis}-record in plaats van vrije tekst.
 *
 * <p><b>Techniek: structured output.</b> Het model antwoordt in JSON volgens het schema van het
 * record, en Spring AI zet die JSON om naar het record ({@code .entity(...)}).
 *
 * <p>Verdedigingslagen tegen foute AI-output:
 * <ol>
 *   <li>native JSON-modus van Ollama: dwingt de vorm van het antwoord af</li>
 *   <li>duidelijke prompt: kort antwoord, geen verzonnen feiten</li>
 *   <li>Bean Validation op het record</li>
 *   <li>één retry, daarna HTTP 502</li>
 * </ol>
 *
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/chatclient.html">Spring AI: ChatClient API</a>
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html">Spring AI: Structured Output Converter</a>
 */
@Service
public class ProjectAnalysisService {

    private static final int MAX_ATTEMPTS = 2;
    private static final Logger log = LoggerFactory.getLogger(ProjectAnalysisService.class);

    private final ChatClient chatClient;
    private final ProjectService projectService;
    private final Validator validator;

    /**
     * Constructor-injectie. De {@link ChatClient.Builder} maakt Spring AI aan met de instellingen
     * uit {@code application.properties} (URL, model, temperature).
     *
     * @param builder        door Spring AI geconfigureerd, maakt de {@link ChatClient}
     * @param projectService levert de projectgegevens voor de prompt
     * @param validator      controleert de validatie-annotaties op {@link ProjectAnalysis}
     */
    public ProjectAnalysisService(ChatClient.Builder builder, ProjectService projectService, Validator validator) {
        this.chatClient = builder.build();
        this.projectService = projectService;
        this.validator = validator;
    }

    /**
     * Analyseert een project, met maximaal {@value #MAX_ATTEMPTS} pogingen.
     *
     * <p>Het project wordt eerst opgehaald, zodat een onbekend id direct een 404 geeft zonder op
     * het model te wachten. Per poging: onleesbare JSON wordt opgevangen, en een ongeldig record
     * (bijv. {@code risk = null}) wordt door de {@link Validator} afgekeurd. AI-output wordt dus
     * nooit blind vertrouwd.
     *
     * @param projectId id van het project
     * @return een geldige analyse
     * @throws com.jrevolt.javaailab.project.ProjectNotFoundException als het project niet bestaat (404)
     * @throws AiResponseException als het model na alle pogingen geen geldig antwoord gaf (502)
     */
    public ProjectAnalysis analyze(long projectId) {
        ProjectInfo project = projectService.getById(projectId);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ProjectAnalysis analysis;
            try {
                analysis = askModel(project);
            } catch (RuntimeException e) {
                log.warn("Attempt {}/{}: model returned unreadable output: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                continue;
            }

            if (analysis != null) {
                Set<ConstraintViolation<ProjectAnalysis>> errors = validator.validate(analysis);
                if (errors.isEmpty()) {
                    return analysis;
                }
                log.warn("Attempt {}/{}: model returned invalid analysis {}: {}", attempt, MAX_ATTEMPTS, analysis, errors);
            }
        }

        throw new AiResponseException("No valid analysis from the model for project " + projectId
                + " after " + MAX_ATTEMPTS + " attempts");
    }

    /**
     * Stuurt één prompt naar het model en zet het antwoord om naar een {@link ProjectAnalysis}.
     *
     * <ul>
     *   <li>{@code options(...format(...))}: <b>native JSON-modus</b> van Ollama. Het schema van
     *       het record gaat als {@code format} mee, zodat het model alleen JSON in die vorm kan
     *       maken. Zonder dit schreef llama3.2 het schema over en kwam alles als {@code null} terug.</li>
     *   <li>{@code system(...)}: de rol van het model.</li>
     *   <li>{@code user(u -> u.text(...).param(...))}: prompt-template; {@code {name}} en
     *       {@code {status}} worden ingevuld met de projectgegevens.</li>
     *   <li>{@code call()}: verstuurt de vraag en wacht op het antwoord (goedkoop op een virtual thread).</li>
     *   <li>{@code entity(...)}: zet de JSON om naar het record.</li>
     * </ul>
     *
     * @param project het project; naam en status gaan mee in de prompt
     * @return het omgezette antwoord, nog niet gevalideerd
     */
    private ProjectAnalysis askModel(ProjectInfo project) {
        return chatClient.prompt()
                .options(OllamaChatOptions.builder()
                        .format(new BeanOutputConverter<>(ProjectAnalysis.class).getJsonSchemaMap()))
                .system("You are a software project risk analyst. Be concise.")
                .user(u -> u.text("""
                        Analyze this software project and assess its delivery risk.
                        Name: {name}
                        Status: {status}

                        Keep the summary to at most two short sentences.
                        Only use the information given above; do not invent facts.
                        """)
                        .param("name", project.name())
                        .param("status", project.status().name()))
                .call()
                .entity(ProjectAnalysis.class);
    }
}
