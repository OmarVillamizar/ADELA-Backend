package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;

import com.adela.calificacion.EscalaBanda;
import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.TipoEstilo;
import com.adela.dto.InterpretacionDTO;
import com.adela.dto.InterpretacionDTO.BandaDTO;
import com.adela.dto.InterpretacionDTO.EscalonDTO;
import com.adela.dto.InterpretacionDTO.EstiloLecturaDTO;
import com.adela.dto.InterpretacionDTO.PlanoDTO;
import com.adela.entities.Cuestionario;
import com.adela.entities.Estilo;
import com.adela.entities.PlanoCuadrantes;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.BandaInterpretacionRepository;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.EscalonRelativoRepository;
import com.adela.repositories.PlanoCuadrantesRepository;

class InterpretacionServiceTest {

    private final CuestionarioRepository cuestionarios = mock(CuestionarioRepository.class);
    private final BandaInterpretacionRepository bandas = mock(BandaInterpretacionRepository.class);
    private final EscalonRelativoRepository escalones = mock(EscalonRelativoRepository.class);
    private final PlanoCuadrantesRepository planos = mock(PlanoCuadrantesRepository.class);
    private final InterpretacionService service = new InterpretacionService(cuestionarios, bandas, escalones,
            planos);

    private Cuestionario chaea;

    @BeforeEach
    void preparar() {
        chaea = new Cuestionario();
        chaea.setId(1L);
        Estilo activo = new Estilo();
        activo.setId(10L);
        activo.setNombre("Activo");
        chaea.getEstilos().add(activo);
        Estilo reflexivo = new Estilo();
        reflexivo.setId(11L);
        reflexivo.setNombre("Reflexivo");
        reflexivo.setTipo(TipoEstilo.COMPUESTO);
        chaea.getEstilos().add(reflexivo);
        when(cuestionarios.findById(1L)).thenReturn(Optional.of(chaea));
    }

    private static BandaDTO banda(String estilo, double li, double ls, String etiqueta, int orden) {
        return new BandaDTO(estilo, EscalaBanda.BRUTO, li, ls, etiqueta, orden);
    }

    private Map<String, String> errores(InterpretacionDTO dto) {
        AppException e = assertThrows(AppException.class, () -> service.guardar(1L, dto));
        assertEquals(ErrorCode.VALIDACION, e.getCode());
        return e.getFields();
    }

    @Test
    @DisplayName("Guarda el baremo: borra lo anterior antes de insertar y actualiza el esquema")
    void guardaBaremo() {
        InterpretacionDTO dto = new InterpretacionDTO(EsquemaInterpretacion.BAREMO, null, true,
                List.of(banda("Activo", 0, 6, "Muy baja", 1), banda("Activo", 7, 8, "Baja", 2)), null);

        service.guardar(1L, dto);

        InOrder orden = inOrder(bandas);
        orden.verify(bandas).borrarDeCuestionario(chaea);
        orden.verify(bandas).saveAll(org.mockito.ArgumentMatchers.argThat(l -> ((List<?>) l).size() == 2));
        assertEquals(EsquemaInterpretacion.BAREMO, chaea.getEsquemaInterpretacion());
        assertEquals(10d, chaea.getDeltaRelativo());
        assertTrue(chaea.isEsIpsativo());
    }

    @Test
    @DisplayName("Rechaza un estilo que no es del cuestionario")
    void estiloAjeno() {
        assertTrue(errores(new InterpretacionDTO(EsquemaInterpretacion.BAREMO, null, false,
                List.of(banda("Visual", 0, 5, "Baja", 1)), null)).containsKey("bandas[0]"));
    }

    @Test
    @DisplayName("Rechaza bandas que se solapan o repiten orden")
    void solapes() {
        assertTrue(errores(new InterpretacionDTO(EsquemaInterpretacion.BAREMO, null, false,
                List.of(banda("Activo", 0, 7, "Baja", 1), banda("Activo", 7, 10, "Alta", 2)), null))
                .get("bandas").contains("solapan"));
        assertTrue(errores(new InterpretacionDTO(EsquemaInterpretacion.BAREMO, null, false,
                List.of(banda("Activo", 0, 5, "Baja", 1), banda("Activo", 6, 10, "Alta", 1)), null))
                .get("bandas").contains("orden"));
    }

    @Test
    @DisplayName("En POMP las bandas pueden compartir el límite; no se superponen")
    void pompContiguas() {
        List<BandaDTO> contiguas = List.of(new BandaDTO("Activo", EscalaBanda.POMP, 0d, 33.3, "Bajo", 1),
                new BandaDTO("Activo", EscalaBanda.POMP, 33.3, 66.7, "Medio", 2),
                new BandaDTO("Activo", EscalaBanda.POMP, 66.7, 100d, "Alto", 3));
        service.guardar(1L, new InterpretacionDTO(EsquemaInterpretacion.BAREMO, null, null, contiguas, null));

        assertTrue(errores(new InterpretacionDTO(EsquemaInterpretacion.BAREMO, null, null,
                List.of(new BandaDTO("Activo", EscalaBanda.POMP, 0d, 40d, "Bajo", 1),
                        new BandaDTO("Activo", EscalaBanda.POMP, 30d, 100d, "Alto", 2)),
                null)).get("bandas").contains("solapan"));
    }

    @Test
    @DisplayName("Sin esIpsativo se conserva el valor del cuestionario")
    void ipsativoNuloConserva() {
        chaea.setEsIpsativo(true);
        service.guardar(1L, new InterpretacionDTO(EsquemaInterpretacion.NINGUNA, null, null, null, null));
        assertTrue(chaea.isEsIpsativo());

        service.guardar(1L, new InterpretacionDTO(EsquemaInterpretacion.NINGUNA, null, false, null, null));
        assertEquals(false, chaea.isEsIpsativo());
    }

    @Test
    @DisplayName("La pregunta de preferencia solo queda activa con el esquema escalonado")
    void preguntaPreferenciaSoloEscalonado() {
        List<EscalonDTO> escalones = List.of(new EscalonDTO(1d, 64d, 4d));
        service.guardar(1L, new InterpretacionDTO(EsquemaInterpretacion.RELATIVO_ESCALONADO, null, null, null,
                escalones, null, null, true));
        assertTrue(chaea.isPreguntaPreferencia());

        service.guardar(1L, new InterpretacionDTO(EsquemaInterpretacion.NINGUNA, null, null, null, null, null,
                null, true));
        assertEquals(false, chaea.isPreguntaPreferencia());
    }

    @Test
    @DisplayName("El esquema escalonado exige escalones válidos y sin solape")
    void escalonado() {
        assertTrue(errores(new InterpretacionDTO(EsquemaInterpretacion.RELATIVO_ESCALONADO, null, false, null,
                null)).containsKey("escalones"));
        assertTrue(errores(new InterpretacionDTO(EsquemaInterpretacion.RELATIVO_ESCALONADO, null, false, null,
                List.of(new EscalonDTO(0d, 16d, 1d), new EscalonDTO(16d, 22d, 2d)))).containsKey("escalones"));
    }

    private static PlanoDTO plano(String ejeX, String ejeY, String a, String b, String c, String d) {
        return new PlanoDTO(ejeX, ejeY, 6d, 7d, a, b, c, d, true, false);
    }

    private static InterpretacionDTO cuadrantes(PlanoDTO plano) {
        return new InterpretacionDTO(EsquemaInterpretacion.CUADRANTES, null, null, null, null, plano, null);
    }

    @Test
    @DisplayName("Cuadrantes exige el plano")
    void cuadrantesSinPlano() {
        assertTrue(errores(cuadrantes(null)).containsKey("plano"));
    }

    @Test
    @DisplayName("Los ejes del plano existen en el cuestionario y son distintos")
    void ejesDelPlano() {
        Map<String, String> e = errores(cuadrantes(plano("Visual", null, "A", "B", "C", "D")));
        assertTrue(e.containsKey("plano.ejeX"));
        assertTrue(e.containsKey("plano.ejeY"));
        assertTrue(errores(cuadrantes(plano("Activo", "Activo", "A", "B", "C", "D"))).containsKey("plano.ejeY"));
    }

    @Test
    @DisplayName("Los nombres de las esquinas no pueden estar vacíos, pasar de 60 ni repetirse")
    void esquinasDelPlano() {
        Map<String, String> e = errores(cuadrantes(plano("Activo", "Reflexivo", " ", "x".repeat(61), "Igual", " igual ")));
        assertTrue(e.containsKey("plano.xAltoYAlto"));
        assertTrue(e.containsKey("plano.xBajoYAlto"));
        assertTrue(!e.containsKey("plano.xBajoYBajo"));
        assertTrue(e.containsKey("plano.xAltoYBajo"));
    }

    @Test
    @DisplayName("Los cortes deben ser finitos; sin corte vale 0")
    void cortesDelPlano() {
        Map<String, String> e = errores(cuadrantes(new PlanoDTO("Activo", "Reflexivo", Double.NaN,
                Double.POSITIVE_INFINITY, "A", "B", "C", "D", null, null)));
        assertTrue(e.containsKey("plano.corteX"));
        assertTrue(e.containsKey("plano.corteY"));

        InterpretacionDTO guardado = service.guardar(1L, cuadrantes(new PlanoDTO("Activo", "Reflexivo", null, null,
                "A", "B", "C", "D", null, null)));
        assertEquals(false, guardado.plano().invertirX());
        assertEquals(0d, guardado.plano().corteX());
        assertEquals(0d, guardado.plano().corteY());
    }

    @Test
    @DisplayName("Guarda el plano con los nombres sin espacios y el GET lo devuelve con los estilos")
    void idaYVueltaDelPlano() {
        PlanoDTO enviado = plano("Activo", "Reflexivo", " Convergente ", "Asimilador", "Divergente", "Acomodador");
        service.guardar(1L, cuadrantes(enviado));

        ArgumentCaptor<PlanoCuadrantes> guardado = ArgumentCaptor.forClass(PlanoCuadrantes.class);
        verify(planos).save(guardado.capture());
        PlanoCuadrantes e = guardado.getValue();
        assertEquals(1L, e.getCuestionarioId());
        assertEquals(10L, e.getEjeX().getId());
        assertEquals(11L, e.getEjeY().getId());
        assertEquals("Convergente", e.getXAltoYAlto());
        assertEquals(7d, e.getCorteY());
        assertTrue(e.isInvertirX());
        assertTrue(!e.isInvertirY());
        verify(planos, never()).deleteById(1L);

        when(planos.findById(1L)).thenReturn(Optional.of(e));
        InterpretacionDTO leido = service.obtener(1L);
        assertEquals(EsquemaInterpretacion.CUADRANTES, leido.esquema());
        assertEquals(plano("Activo", "Reflexivo", "Convergente", "Asimilador", "Divergente", "Acomodador"),
                leido.plano());
        assertEquals(List.of(new EstiloLecturaDTO("Activo", TipoEstilo.PRIMARIO), new EstiloLecturaDTO("Reflexivo",
                TipoEstilo.COMPUESTO)), leido.estilos());
    }

    @Test
    @DisplayName("Con otro esquema el plano se borra y no se lee")
    void otroEsquemaBorraElPlano() {
        service.guardar(1L, new InterpretacionDTO(EsquemaInterpretacion.NINGUNA, null, null, null, null));

        verify(planos).deleteById(1L);
        verify(planos, never()).save(any());
        assertNull(service.obtener(1L).plano());
    }

    @Test
    @DisplayName("Sin esquema o con delta fuera de [0, 100] es VALIDACION")
    void esquemaYDelta() {
        Map<String, String> e = errores(new InterpretacionDTO(null, 150d, false, null, null));
        assertTrue(e.containsKey("esquema"));
        assertTrue(e.containsKey("delta"));
    }
}
