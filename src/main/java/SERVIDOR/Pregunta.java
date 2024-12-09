
package SERVIDOR;

import java.util.List;

/**
 *
 * @author aespa
 */
public class Pregunta {
    private final int id;
    private final String enunciado;
    private final String codigo;
    private final List<String> opciones;
    private final int respuestaCorrecta;
    
    public Pregunta(int id, String enunciado, String codigo, List<String> opciones, int respuestaCorrecta) {
        this.id = id;
        this.enunciado = enunciado;
        this.codigo = codigo;
        this.opciones = opciones;
        this.respuestaCorrecta = respuestaCorrecta;
    }
    
    public int getId() { return id; }
    public String getEnunciado() { return enunciado; }
    public String getCodigo() { return codigo; }
    public List<String> getOpciones() { return opciones; }
    public int getRespuestaCorrecta() { return respuestaCorrecta; }
    
    public boolean esRespuestaCorrecta(String respuesta) {
        int indiceRespuesta = respuesta.charAt(0) - 'A';
        return indiceRespuesta == respuestaCorrecta;
    }
}
