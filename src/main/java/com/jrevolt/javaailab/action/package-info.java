/**
 * The closed set of actions an AI assistant may perform on projects.
 *
 * <p><b>Idea:</b> an AI model should not be allowed to do "anything". It gets a fixed menu of
 * actions, each with typed input. This is exactly how <em>tool calling</em> and <em>MCP
 * tools</em> work: the model picks a tool and fills in its parameters, the application
 * executes it.
 *
 * <p>Modern Java techniques shown here:
 * <ul>
 *   <li>{@link com.jrevolt.javaailab.action.AiAction}: sealed interface (Java 17) with
 *       records as the permitted implementations.</li>
 *   <li>{@link com.jrevolt.javaailab.action.ActionResult}: sealed interface whose permitted
 *       records are nested inside it, so no {@code permits} clause is needed.</li>
 *   <li>{@link com.jrevolt.javaailab.action.AiActionService}: pattern matching for
 *       {@code switch} (Java 21), record patterns (Java 21), guards with {@code when},
 *       unnamed patterns {@code _} (Java 22) and virtual threads (Java 21).</li>
 * </ul>
 */
package com.jrevolt.javaailab.action;
