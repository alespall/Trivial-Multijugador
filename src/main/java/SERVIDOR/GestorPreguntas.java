package SERVIDOR;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GestorPreguntas {
      private static final GestorPreguntas instancia = new GestorPreguntas();
    private final List<Pregunta> preguntas;
    
    private GestorPreguntas() {
        this.preguntas = cargarPreguntas();
    }
    public static GestorPreguntas getInstance() {
        return instancia;
    }
    private List<Pregunta> cargarPreguntas() {
        List<Pregunta> preguntasCargadas = new ArrayList<>();
        try {
            String contenido = new String(Files.readAllBytes(Paths.get("src/main/resources/preguntas.json")));
            JSONObject json = new JSONObject(contenido);
            JSONArray preguntasJson = json.getJSONArray("preguntas");
            
            for (int i = 0; i < preguntasJson.length(); i++) {
                JSONObject preguntaJson = preguntasJson.getJSONObject(i);
                Pregunta pregunta = new Pregunta(
                    preguntaJson.getInt("id"),
                    preguntaJson.getString("pregunta"),
                    preguntaJson.optString("codigo"),
                    preguntaJson.getJSONArray("opciones").toList().stream()
                        .map(Object::toString)
                        .collect(Collectors.toList()),
                    preguntaJson.getInt("respuesta_correcta")
                );
                preguntasCargadas.add(pregunta);
            }
        } catch (IOException | JSONException e) {
            System.err.println("Error al cargar las preguntas: " + e.getMessage());
        }
        return preguntasCargadas;
    }
    public List<Pregunta> obtenerPreguntas() {
        return new ArrayList<>(preguntas);
    }
}
