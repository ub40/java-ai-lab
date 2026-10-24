package com.jrevolt.javaailab.analysis;

import com.jrevolt.javaailab.project.ProjectInfo;
import com.jrevolt.javaailab.project.ProjectService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Asks a language model to assess the delivery risk of a project, and returns the answer as a
 * typed {@link ProjectAnalysis} record instead of free text.
 *
 * <p><b>Technique: structured output.</b> A language model always produces text. To let
 * application code work with the answer (for example {@code analysis.risk() == Risk.HIGH}),
 * the model is asked to answer in JSON that matches a Java record, and the JSON is converted
 * into that record. Spring AI does both steps in {@code .entity(ProjectAnalysis.class)}.
 *
 * <p>Flow of one call:
 * <pre>
 *   analyze(1)
 *     -> ProjectService.getById(1)           (404 if the project does not exist)
 *     -> prompt: system + user text with the project data
 *     -> Spring AI adds JSON format instructions for ProjectAnalysis
 *     -> Ollama / llama3.2 answers with JSON
 *     -> Spring AI converts the JSON into a ProjectAnalysis record
 * </pre>
 *
 * <p>The model only knows what is in the prompt. It has no access to {@link ProjectService},
 * so the project data must be sent along explicitly.
 *
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/chatclient.html">Spring AI reference: ChatClient API</a>
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html">Spring AI reference: Structured Output Converter</a>
 */
@Service
public class ProjectAnalysisService {

    private final ChatClient chatClient;
    private final ProjectService projectService;

    /**
     * Constructor injection: Spring passes in both dependencies.
     *
     * <p>{@link ChatClient.Builder} is created by Spring AI's auto-configuration from
     * {@code application.properties} ({@code spring.ai.ollama.base-url},
     * {@code spring.ai.ollama.chat.model}, {@code spring.ai.ollama.chat.temperature}).
     * No default system prompt is set here, because {@link #askModel(ProjectInfo)} sets its
     * own system prompt per call.
     *
     * @param builder        pre-configured by Spring AI, used to create the {@link ChatClient}
     * @param projectService provides the project data that is sent to the model
     * @see <a href="https://docs.spring.io/spring-ai/reference/api/chatclient.html">ChatClient API: creating a ChatClient</a>
     */
    public ProjectAnalysisService(ChatClient.Builder builder, ProjectService projectService) {
        this.chatClient = builder.build();
        this.projectService = projectService;
    }

    /**
     * Analyzes the project with the given id.
     *
     * <p>The project is looked up <em>before</em> the model is called. If it does not exist,
     * {@link com.jrevolt.javaailab.project.ProjectNotFoundException} is thrown (HTTP 404) and
     * no time is spent waiting for the model.
     *
     * @param projectId id of the project to analyze
     * @return the model's assessment as a typed record
     * @throws com.jrevolt.javaailab.project.ProjectNotFoundException if no project has this id
     */
    public ProjectAnalysis analyze(long projectId) {
        ProjectInfo project = projectService.getById(projectId);
        return askModel(project);
    }

    /**
     * Sends one prompt to the model and converts the answer into a {@link ProjectAnalysis}.
     *
     * <p>What each call in the chain does:
     * <ul>
     *   <li>{@code prompt()}: starts a new request to the model.</li>
     *   <li>{@code system(...)}: the <em>system prompt</em>, the role and rules for the model.</li>
     *   <li>{@code user(u -> u.text(...).param(...))}: the <em>user prompt</em>, written as a
     *       <b>prompt template</b>. The placeholders {@code {name}} and {@code {status}} are
     *       replaced by the values passed to {@code param(...)}, so no strings are glued
     *       together with {@code +}.</li>
     *   <li>{@code call()}: sends the request and <em>waits</em> for the complete answer. On a
     *       virtual thread this waiting is cheap.</li>
     *   <li>{@code entity(ProjectAnalysis.class)}: instead of returning text (like
     *       {@code content()}), Spring AI's {@code BeanOutputConverter} generates a JSON schema
     *       from the record, appends "answer only in JSON matching this schema" to the prompt,
     *       and converts the JSON answer into the record. For the enum {@link Risk} the schema
     *       lists the only allowed values: {@code LOW}, {@code MEDIUM}, {@code HIGH}.</li>
     * </ul>
     *
     * <p>The prompt text is a <b>text block</b> ({@code """ ... """}, Java 15): multi-line text
     * without {@code \n} or {@code +}.
     *
     * <p><b>Note:</b> the model can still return invalid JSON or empty fields. That is handled
     * by validation and a retry (step 3 of topic 4.4).
     *
     * @param project the project to analyze; its name and status are sent to the model
     * @return the converted answer
     * @see <a href="https://docs.spring.io/spring-ai/reference/api/chatclient.html">ChatClient API: prompt(), call(), content() and entity()</a>
     * @see <a href="https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html">Structured Output Converter: BeanOutputConverter and entity()</a>
     */
    private ProjectAnalysis askModel(ProjectInfo project) {
        return chatClient.prompt()
                .system("You are a software project risk analyst. Be concise.")
                .user(u -> u.text("""
                        Analyze this software project and assess its delivery risk.
                        Name: {name}
                        Status: {status}
                        """)
                        .param("name", project.name())
                        .param("status", project.status().name()))
                .call()
                .entity(ProjectAnalysis.class);
    }
}
