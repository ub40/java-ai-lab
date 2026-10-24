package com.jrevolt.javaailab.analysis;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Het taalmodel gaf geen geldig antwoord, ook niet na een nieuwe poging.
 *
 * <p>Wordt HTTP <b>502 Bad Gateway</b>: de app zelf is in orde, maar de server erachter (het
 * model) gaf een ongeldig antwoord. Beter een duidelijke fout dan stil {@code null}-data.
 */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class AiResponseException extends RuntimeException {

    /**
     * @param message wat er mis ging, voor de log
     */
    public AiResponseException(String message) {
        super(message);
    }
}
