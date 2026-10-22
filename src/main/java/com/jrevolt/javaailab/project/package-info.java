/**
 * Project domain: the data an AI assistant can ask about.
 *
 * <p>Layering used in this package (the classic Spring setup):
 * <pre>
 *   ProjectController   (HTTP: URLs, parameters, status codes)
 *          |
 *   AiActionService     (decides WHAT to do for an action)
 *          |
 *   ProjectService      (business logic + data access)
 *          |
 *   ProjectInfo         (immutable data, a record)
 * </pre>
 *
 * <p>Modern Java techniques shown here:
 * <ul>
 *   <li>{@link com.jrevolt.javaailab.project.ProjectInfo}: record with a compact constructor
 *       (validation) and a flexible constructor body (Java 25, JEP 513).</li>
 *   <li>{@link com.jrevolt.javaailab.project.ProjectService}: sequenced collections
 *       ({@code getLast()}, Java 21) and stream gatherers ({@code Gatherers.windowFixed}, Java 24).</li>
 * </ul>
 */
package com.jrevolt.javaailab.project;
