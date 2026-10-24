/**
 * Projectdomein: de gegevens waar een AI-assistent naar kan vragen.
 *
 * <p>Lagen (klassieke Spring-opzet):
 * <pre>
 *   ProjectController   (HTTP)
 *          |
 *   AiActionService     (welke actie)
 *          |
 *   ProjectService      (logica + data)
 *          |
 *   ProjectInfo         (onveranderbare data, record)
 * </pre>
 *
 * <p>Modern Java hier: record met compact constructor en flexible constructor body (Java 25),
 * sequenced collections ({@code getLast()}, Java 21) en stream gatherers (Java 24).
 */
package com.jrevolt.javaailab.project;
