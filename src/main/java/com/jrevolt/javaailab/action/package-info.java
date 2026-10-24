/**
 * De vaste set acties die een AI-assistent op projecten mag uitvoeren.
 *
 * <p>Een model mag niet "alles" doen: het krijgt een vast menu met getypeerde invoer. Zo werken
 * <em>tool calling</em> en <em>MCP-tools</em>: het model kiest een tool, de app voert hem uit.
 *
 * <p>Modern Java hier: sealed interfaces met records (Java 17), pattern matching voor
 * {@code switch} en record patterns (Java 21), {@code when}-guards, unnamed patterns {@code _}
 * (Java 22) en virtual threads (Java 21).
 */
package com.jrevolt.javaailab.action;
