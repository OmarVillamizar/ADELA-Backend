package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import com.adela.dto.CapsulaActualizarDTO;
import com.adela.dto.CapsulaCrearDTO;
import com.adela.dto.CapsulaDTO;
import com.adela.dto.CapsulaReporteDTO;
import com.adela.dto.CapsulaReporteDTO.CategoriaReporteDTO;
import com.adela.dto.PuntajeRespuestaCapsulaDTO;
import com.adela.dto.RespuestaCapsulaDTO;
import com.adela.dto.ResultadoCapsulaDTO;
import com.adela.entities.Capsula;
import com.adela.entities.Categoria;
import com.adela.entities.RespuestaCapsula;
import com.adela.entities.Cuestionario;
import com.adela.entities.ModoIdentificacion;
import com.adela.entities.Profesor;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.CapsulaRepository;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.RespuestaCapsulaRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * Cápsulas. Del lado del profesor protege el control de propiedad: una cápsula
 * ajena se trata como inexistente, igual que un grupo ajeno. Del lado público,
 * las reglas que deciden qué se guarda de una persona sin cuenta.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapsulaServiceTest {

    @Mock
    private CapsulaRepository capsulaRepository;

    @Mock
    private RespuestaCapsulaRepository respuestaCapsulaRepository;

    @Mock
    private CuestionarioRepository cuestionarioRepository;

    @Mock
    private EvaluacionRespuestas evaluacionRespuestas;

    @InjectMocks
    private CapsulaService service;

    private Profesor propietario;
    private Profesor intruso;
    private Cuestionario cuestionario;
    private Capsula capsula;

    @BeforeEach
    void preparar() {
        propietario = profesor("propietario@ufps.edu.co");
        intruso = profesor("intruso@ufps.edu.co");
        cuestionario = new Cuestionario();
        cuestionario.setId(1L);
        cuestionario.setNombre("VARK");
        cuestionario.setSiglas("VARK");

        capsula = new Capsula();
        capsula.setId(5L);
        capsula.setCodigo("ABC234");
        capsula.setNombre("Charla");
        capsula.setProfesor(propietario);
        capsula.setCuestionario(cuestionario);
        capsula.setModoIdentificacion(ModoIdentificacion.NOMBRE);

        when(capsulaRepository.findByProfesorAndId(propietario, 5L)).thenReturn(Optional.of(capsula));
        when(capsulaRepository.findByProfesorAndId(intruso, 5L)).thenReturn(Optional.empty());
        when(capsulaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cuestionarioRepository.findById(1L)).thenReturn(Optional.of(cuestionario));

        when(capsulaRepository.findByCodigo("ABC234")).thenReturn(Optional.of(capsula));
        when(respuestaCapsulaRepository.findByCapsulaAndIntento(any(), any())).thenReturn(Optional.empty());
        when(respuestaCapsulaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(evaluacionRespuestas.validarSeleccion(any(), any())).thenReturn(List.of());
        when(evaluacionRespuestas.puntuar(any(), any())).thenReturn(List.of());
    }

    private static RespuestaCapsulaDTO envio(UUID intento, String nombre) {
        return new RespuestaCapsulaDTO(intento, nombre, List.of(11L));
    }

    @Test
    @DisplayName("Una cápsula cerrada no se abre ni recibe respuestas")
    void capsulaCerradaRechaza() {
        capsula.setAbierta(false);

        AppException abrir = assertThrows(AppException.class, () -> service.paraResponder("abc-234"));
        assertEquals(ErrorCode.CAPSULA_CERRADA, abrir.getCode());
        AppException enviar = assertThrows(AppException.class,
                () -> service.responder("ABC234", envio(UUID.randomUUID(), "Ana")));
        assertEquals(ErrorCode.CAPSULA_CERRADA, enviar.getCode());
        verify(respuestaCapsulaRepository, never()).save(any());
    }

    @Test
    @DisplayName("En modo NOMBRE el nombre es obligatorio")
    void modoNombreExigeNombre() {
        AppException e = assertThrows(AppException.class,
                () -> service.responder("ABC234", envio(UUID.randomUUID(), "  ")));
        assertEquals(ErrorCode.VALIDACION, e.getCode());
        assertTrue(e.getFields().containsKey("nombre"));
    }

    @Test
    @DisplayName("En modo ANONIMO el nombre enviado se descarta")
    void modoAnonimoDescartaNombre() {
        capsula.setModoIdentificacion(ModoIdentificacion.ANONIMO);

        ResultadoCapsulaDTO r = service.responder("ABC234", envio(UUID.randomUUID(), "Ana"));

        ArgumentCaptor<RespuestaCapsula> guardada = ArgumentCaptor.forClass(RespuestaCapsula.class);
        verify(respuestaCapsulaRepository).save(guardada.capture());
        assertNull(guardada.getValue().getNombre());
        assertNull(r.nombre());
        assertEquals(CapsulaService.LONGITUD_CODIGO_RESULTADO, r.codigo().length());
    }

    @Test
    @DisplayName("Reenviar el mismo intento devuelve el resultado guardado sin crear otro")
    void intentoRepetidoEsIdempotente() {
        UUID intento = UUID.randomUUID();
        RespuestaCapsula previa = new RespuestaCapsula();
        previa.setCapsula(capsula);
        previa.setCodigo("PREVIA234567");
        previa.setIntento(intento);
        when(respuestaCapsulaRepository.findByCapsulaAndIntento(capsula, intento)).thenReturn(Optional.of(previa));

        ResultadoCapsulaDTO r = service.responder("ABC234", envio(intento, "Ana"));

        assertEquals("PREVIA234567", r.codigo());
        verify(respuestaCapsulaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un código con longitud imposible es 404 sin consultar la base de datos")
    void codigoImposibleNoConsulta() {
        assertThrows(EntityNotFoundException.class, () -> service.paraResponder("ABC"));
        assertThrows(EntityNotFoundException.class, () -> service.resultado("ABC234"));
        verify(capsulaRepository, never()).findByCodigo("ABC");
        verify(respuestaCapsulaRepository, never()).findByCodigo(any());
    }

    /**
     * Visual escala 0..10, Auditivo 0..20. Respuesta 1: Visual 6 (0.6) vs
     * Auditivo 9 (0.45) → Visual, aunque el valor crudo sea menor. Respuesta 2:
     * 5 y 10 → empate (0.5). Respuesta 3: sin puntos → sin predominante.
     */
    private void prepararReporte() {
        Categoria visual = categoria(1L, "Visual", 10d);
        Categoria auditivo = categoria(2L, "Auditivo", 20d);
        cuestionario.getCategorias().add(visual);
        cuestionario.getCategorias().add(auditivo);

        when(respuestaCapsulaRepository.findByCapsulaOrderByRespondidaEn(capsula))
                .thenReturn(List.of(respuesta(101L, "Ana"), respuesta(102L, "Luis"), respuesta(103L, "Eva")));
        when(respuestaCapsulaRepository.puntajesPorCategoria(capsula)).thenReturn(List.of(
                new PuntajeRespuestaCapsulaDTO(101L, 1L, 6d), new PuntajeRespuestaCapsulaDTO(101L, 2L, 9d),
                new PuntajeRespuestaCapsulaDTO(102L, 1L, 5d), new PuntajeRespuestaCapsulaDTO(102L, 2L, 10d)));
    }

    @Test
    @DisplayName("El reporte promedia y cuenta el predominante normalizado, con empates")
    void reporteNormalizaYCuentaEmpates() {
        prepararReporte();

        CapsulaReporteDTO r = service.reporte(5L, propietario);

        assertEquals(3, r.totalRespuestas());
        CategoriaReporteDTO visual = r.categorias().get(0);
        CategoriaReporteDTO auditivo = r.categorias().get(1);
        assertEquals(11d / 3, visual.promedio(), 1e-9);
        assertEquals(19d / 3, auditivo.promedio(), 1e-9);
        assertEquals(2, visual.predominantes());
        assertEquals(1, auditivo.predominantes());
        assertEquals(List.of("Visual"), r.participantes().get(0).predominantes());
        assertEquals(List.of(), r.participantes().get(2).predominantes());
    }

    @Test
    @DisplayName("En modo ANONIMO el reporte no lista participantes")
    void reporteAnonimoSinParticipantes() {
        prepararReporte();
        capsula.setModoIdentificacion(ModoIdentificacion.ANONIMO);

        assertNull(service.reporte(5L, propietario).participantes());
    }

    @Test
    @DisplayName("Un reporte sin respuestas da ceros, no NaN, y uno ajeno es 404")
    void reporteVacioYAjeno() {
        cuestionario.getCategorias().add(categoria(1L, "Visual", 10d));
        when(respuestaCapsulaRepository.findByCapsulaOrderByRespondidaEn(capsula)).thenReturn(List.of());

        assertEquals(0d, service.reporte(5L, propietario).categorias().get(0).promedio());
        assertThrows(EntityNotFoundException.class, () -> service.reporte(5L, intruso));
    }

    private static Categoria categoria(Long id, String nombre, double maximo) {
        Categoria c = new Categoria();
        c.setId(id);
        c.setNombre(nombre);
        c.setValorMinimo(0d);
        c.setValorMaximo(maximo);
        return c;
    }

    private static RespuestaCapsula respuesta(Long id, String nombre) {
        RespuestaCapsula r = new RespuestaCapsula();
        r.setId(id);
        r.setNombre(nombre);
        return r;
    }

    @Test
    @DisplayName("El nombre se limpia de espacios repetidos y caracteres invisibles")
    void nombreSeLimpia() {
        assertEquals("Ana María",
                CapsulaService.nombreValido(ModoIdentificacion.NOMBRE, "  Ana \t​ María\u0007 "));
    }

    @Test
    @DisplayName("Una cápsula de otro profesor no se consulta, edita ni elimina")
    void capsulaAjenaEsInexistente() {
        assertThrows(EntityNotFoundException.class, () -> service.consultar(5L, intruso));
        assertThrows(EntityNotFoundException.class,
                () -> service.actualizar(5L, new CapsulaActualizarDTO(null, false), intruso));
        assertThrows(EntityNotFoundException.class, () -> service.eliminar(5L, intruso));
        verify(capsulaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Crear asigna un código del alfabeto público y la deja abierta")
    void crearAsignaCodigo() {
        CapsulaDTO dto = service.crear(new CapsulaCrearDTO("  Charla  ", 1L, ModoIdentificacion.ANONIMO),
                propietario);

        assertEquals(CapsulaService.LONGITUD_CODIGO_CAPSULA, dto.codigo().length());
        assertTrue(dto.codigo().chars().allMatch(c -> CodigoAleatorio.ALFABETO.indexOf(c) >= 0));
        assertEquals("Charla", dto.nombre());
        assertTrue(dto.abierta());
    }

    @Test
    @DisplayName("Si otra creación simultánea toma el mismo código, se reintenta con otro")
    void choqueDeCodigoSeReintenta() {
        // El primer guardado choca con el unique; el código ya existe en ese momento.
        when(capsulaRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key capsula_codigo_key"))
                .thenAnswer(inv -> inv.getArgument(0));
        when(capsulaRepository.existsByCodigo(any())).thenReturn(false, true, false);

        CapsulaDTO dto = service.crear(new CapsulaCrearDTO("Charla", 1L, ModoIdentificacion.ANONIMO),
                propietario);

        assertEquals(CapsulaService.LONGITUD_CODIGO_CAPSULA, dto.codigo().length());
        verify(capsulaRepository, times(2)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Una violación que no es el código no se reintenta")
    void otraViolacionSube() {
        when(capsulaRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("otra restriccion"));
        when(capsulaRepository.existsByCodigo(any())).thenReturn(false);

        assertThrows(DataIntegrityViolationException.class, () -> service.crear(
                new CapsulaCrearDTO("Charla", 1L, ModoIdentificacion.ANONIMO), propietario));
        verify(capsulaRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Renombrar con un nombre vacío es VALIDACION")
    void nombreVacioEsValidacion() {
        AppException e = assertThrows(AppException.class,
                () -> service.actualizar(5L, new CapsulaActualizarDTO("   ", null), propietario));
        assertEquals(ErrorCode.VALIDACION, e.getCode());
        assertTrue(e.getFields().containsKey("nombre"));
    }

    @Test
    @DisplayName("Un código en uso se descarta y se genera otro")
    void codigoEnUsoSeReintenta() {
        int[] llamadas = { 0 };
        String codigo = CapsulaService.codigoLibre(8, c -> llamadas[0]++ == 0);
        assertEquals(2, llamadas[0]);
        assertEquals(8, codigo.length());
    }

    @Test
    @DisplayName("El código se acepta con minúsculas, guiones y espacios")
    void codigoSeNormaliza() {
        assertEquals("K7QM2XPA9DTR", CodigoAleatorio.normalizar(" k7qm-2xpa 9dtr "));
    }

    private static Profesor profesor(String email) {
        Profesor p = new Profesor();
        p.setEmail(email);
        return p;
    }
}
