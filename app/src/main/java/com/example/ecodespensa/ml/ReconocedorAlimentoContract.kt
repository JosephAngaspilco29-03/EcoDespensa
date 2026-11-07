package com.example.ecodespensa.ml

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Contrato desacoplado para el reconocimiento de alimentos a partir de una imagen capturada.
 *
 * Permite que la cámara (`CapturaAlimentoCameraScreen`) y el formulario (`AgregarAlimentoScreen`)
 * funcionen desacoplados del intérprete de TensorFlow Lite (LiteRT).
 */
interface ReconocedorAlimentoContract {
    /**
     * Indica si existe un modelo `.tflite` válido configurado y listo para ejecutar inferencia.
     */
    fun estaModeloDisponible(context: Context): Boolean

    /**
     * Recibe la [ImagenEntradaReconocimiento] (con orientación EXIF corregida) y ejecuta
     * la inferencia real devolviendo un [ResultadoReconocimientoAlimento].
     */
    suspend fun analizarImagen(
        context: Context,
        entrada: ImagenEntradaReconocimiento
    ): ResultadoReconocimientoAlimento

    /**
     * Libera el intérprete nativo de TensorFlow Lite cuando ya no se necesite.
     */
    fun liberarRecursos()
}

private data class ClaseAlimentoMapeada(
    val nombreEspanol: String,
    val categoriaSugerida: String,
    val etiquetaOriginal: String
)

/**
 * Implementación real de [ReconocedorAlimentoContract] con TensorFlow Lite (LiteRT) para la Etapa 7B.
 *
 * Carga `model/FruitsClassifier.tflite` y `model/labels_alimentos.txt` desde `assets/`:
 * - Inspecciona dinámicamente la forma y tipo de los tensores de entrada (`float32[1, 150, 150, 3]`)
 *   y salida (`float32[1, 6]`).
 * - Aplica exactamente el preprocesamiento del modelo entrenado: redimensionado al ancho/alto
 *   del tensor de entrada (`150×150` px) y normalización RGB `FLOAT32` en `[0.0f, 1.0f]` (`canal / 255.0f`).
 * - Conserva internamente hasta 3 predicciones con sus probabilidades reales para tomar decisiones,
 *   o devuelve [ResultadoReconocimientoAlimento.BajaConfianza]
 *   ("No se pudo identificar el alimento. Ingresa sus datos manualmente") si la probabilidad
 *   máxima no alcanza el [umbralMinimoConfianza] configurable.
 */
class ReconocedorAlimentoTFLite(
    private val umbralMinimoConfianza: Float = UMBRAL_MINIMO_CONFIANZA_DEFECTO
) : ReconocedorAlimentoContract {

    private val mutex = Mutex()
    private var interpreter: Interpreter? = null
    private var clases: List<ClaseAlimentoMapeada> = emptyList()

    override fun estaModeloDisponible(context: Context): Boolean {
        return runCatching {
            val archivosModel = context.assets.list(CARPETA_ASSETS_MODEL).orEmpty()
            archivosModel.any { it.equals(NOMBRE_ARCHIVO_MODELO, ignoreCase = true) }
        }.getOrDefault(false)
    }

    override suspend fun analizarImagen(
        context: Context,
        entrada: ImagenEntradaReconocimiento
    ): ResultadoReconocimientoAlimento = withContext(Dispatchers.Default) {
        if (entrada.bitmap.isRecycled || entrada.anchoProcesadoPx <= 0 || entrada.altoProcesadoPx <= 0) {
            return@withContext ResultadoReconocimientoAlimento.Error(
                "La fotografía capturada no es válida para reconocimiento."
            )
        }

        if (!estaModeloDisponible(context)) {
            return@withContext ResultadoReconocimientoAlimento.SinModeloConfigurado(
                "No se encontró el archivo $RUTA_MODELO_ASSETS en assets."
            )
        }

        mutex.withLock {
            runCatching {
                val tflite = obtenerOInicializarInterpreter(context.applicationContext)
                val listaClases = if (clases.isNotEmpty()) {
                    clases
                } else {
                    cargarClasesDesdeAssets(context.applicationContext).also { clases = it }
                }

                // 1. Inspeccionar dinámicamente el tensor de entrada real del modelo
                val inputTensor = tflite.getInputTensor(0)
                val inputShape = inputTensor.shape() // Esperado: [1, 150, 150, 3]
                val inputDataType = inputTensor.dataType() // Esperado: FLOAT32

                require(inputShape.size == 4 && inputShape[3] == 3) {
                    "Forma de tensor de entrada no compatible: ${inputShape.contentToString()}"
                }
                val altoEntrada = inputShape[1]
                val anchoEntrada = inputShape[2]
                val canalesEntrada = inputShape[3]

                // 2. Inspeccionar dinámicamente el tensor de salida real del modelo
                val outputTensor = tflite.getOutputTensor(0)
                val outputShape = outputTensor.shape() // Esperado: [1, 6]
                val outputDataType = outputTensor.dataType() // Esperado: FLOAT32

                require(outputShape.size >= 2) {
                    "Forma de tensor de salida no compatible: ${outputShape.contentToString()}"
                }
                val numClasesSalida = outputShape.last()
                require(numClasesSalida > 0 && listaClases.size >= numClasesSalida) {
                    "El número de salidas del modelo ($numClasesSalida) no coincide con las etiquetas (${listaClases.size})."
                }

                // 3. Preprocesar la imagen exactamente al tamaño requerido por el tensor de entrada (150x150)
                // En FruitClassifier.kt y el notebook original se usa reescalado directo a 150x150 y RGB / 255.0f
                val bitmapEscalado = entrada.prepararBitmapEntradaTensor(
                    anchoObjetivo = anchoEntrada,
                    altoObjetivo = altoEntrada,
                    recorteCentralCuadrado = false
                )

                val inputBuffer = convertirBitmapAByteBufferSegunTensor(
                    bitmap = bitmapEscalado,
                    ancho = anchoEntrada,
                    alto = altoEntrada,
                    canales = canalesEntrada,
                    dataType = inputDataType
                )

                // 4. Ejecutar inferencia y leer probabilidades reales
                val inicioMs = System.currentTimeMillis()
                val probabilidades: FloatArray = when (outputDataType) {
                    DataType.FLOAT32 -> {
                        val salidaFloat = Array(1) { FloatArray(numClasesSalida) }
                        tflite.run(inputBuffer, salidaFloat)
                        salidaFloat[0]
                    }
                    DataType.UINT8 -> {
                        val salidaByte = Array(1) { ByteArray(numClasesSalida) }
                        tflite.run(inputBuffer, salidaByte)
                        FloatArray(numClasesSalida) { idx ->
                            (salidaByte[0][idx].toInt() and 0xFF) / 255.0f
                        }
                    }
                    else -> throw IllegalStateException("Tipo de tensor de salida no soportado: $outputDataType")
                }
                val duracionMs = (System.currentTimeMillis() - inicioMs).coerceAtLeast(1L)

                // 5. Ordenar las predicciones de mayor a menor probabilidad y tomar hasta 3
                val prediccionesOrdenadas = probabilidades.indices
                    .map { idx ->
                        val infoClase = listaClases[idx]
                        PrediccionAlimento(
                            etiqueta = infoClase.nombreEspanol,
                            confianza = probabilidades[idx].coerceIn(0f, 1f),
                            categoriaSugerida = infoClase.categoriaSugerida,
                            etiquetaOriginalModelo = infoClase.etiquetaOriginal
                        )
                    }
                    .sortedByDescending { it.confianza }

                val topTres = prediccionesOrdenadas.take(MAX_SUGERENCIAS)
                val mejor = topTres.firstOrNull()
                    ?: return@runCatching ResultadoReconocimientoAlimento.BajaConfianza()

                val umbralEfectivo = umbralMinimoConfianza.coerceIn(0f, 1f)

                // Si la confianza máxima es inferior al umbral configurable, no autocompletar con predicciones dudosas
                if (mejor.confianza < umbralEfectivo) {
                    ResultadoReconocimientoAlimento.BajaConfianza(
                        mensaje = ResultadoReconocimientoAlimento.MENSAJE_BAJA_CONFIANZA_DEFECTO,
                        candidatos = topTres,
                        tiempoInferenciaMs = duracionMs
                    )
                } else {
                    ResultadoReconocimientoAlimento.Exito(
                        prediccionPrincipal = mejor,
                        alternativas = topTres.drop(1),
                        tiempoInferenciaMs = duracionMs,
                        descripcionTensor = "${anchoEntrada}×${altoEntrada} ${inputDataType.name}"
                    )
                }
            }.getOrElse { error ->
                ResultadoReconocimientoAlimento.Error(
                    "Error al ejecutar el reconocimiento TensorFlow Lite: ${error.localizedMessage ?: "error desconocido"}"
                )
            }
        }
    }

    override fun liberarRecursos() {
        runCatching {
            interpreter?.close()
        }
        interpreter = null
    }

    private fun obtenerOInicializarInterpreter(context: Context): Interpreter {
        interpreter?.let { return it }
        val bufferModelo = cargarBufferModeloDesdeAssets(context, RUTA_MODELO_ASSETS)
        val opciones = Interpreter.Options().apply {
            setNumThreads(2)
        }
        return Interpreter(bufferModelo, opciones).also { interpreter = it }
    }

    private fun cargarBufferModeloDesdeAssets(context: Context, rutaAsset: String): ByteBuffer {
        return runCatching {
            context.assets.openFd(rutaAsset).use { afd ->
                FileInputStream(afd.fileDescriptor).channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                }
            }
        }.getOrElse {
            val bytes = context.assets.open(rutaAsset).use { it.readBytes() }
            ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
        }
    }

    private fun cargarClasesDesdeAssets(context: Context): List<ClaseAlimentoMapeada> {
        val lineas = runCatching {
            context.assets.open(RUTA_LABELS_ASSETS).bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
            }
        }.getOrDefault(emptyList())

        if (lineas.isNotEmpty()) {
            return lineas.map { linea ->
                val partes = linea.split("|")
                val nombreEs = partes.getOrNull(0)?.trim().orEmpty().ifEmpty { "Alimento" }
                val categoria = partes.getOrNull(1)?.trim().orEmpty().ifEmpty { "Otro" }
                val original = partes.getOrNull(2)?.trim().orEmpty().ifEmpty { nombreEs }
                ClaseAlimentoMapeada(
                    nombreEspanol = nombreEs,
                    categoriaSugerida = categoria,
                    etiquetaOriginal = original
                )
            }
        }

        // Respaldo exacto del orden de entrenamiento (flow_from_directory alfabético)
        return listOf(
            ClaseAlimentoMapeada("Manzana", "Fruta", "Apple"),
            ClaseAlimentoMapeada("Plátano", "Fruta", "Banana"),
            ClaseAlimentoMapeada("Limón", "Fruta", "Lemon"),
            ClaseAlimentoMapeada("Cebolla", "Vegetal", "Onion"),
            ClaseAlimentoMapeada("Papa", "Verdura", "Potato"),
            ClaseAlimentoMapeada("Sandía", "Fruta", "Watermelon")
        )
    }

    private fun convertirBitmapAByteBufferSegunTensor(
        bitmap: Bitmap,
        ancho: Int,
        alto: Int,
        canales: Int,
        dataType: DataType
    ): ByteBuffer {
        val bytesPorCanal = when (dataType) {
            DataType.FLOAT32 -> 4
            DataType.UINT8 -> 1
            else -> throw IllegalArgumentException("Tipo de tensor de entrada no soportado: $dataType")
        }

        val buffer = ByteBuffer.allocateDirect(1 * alto * ancho * canales * bytesPorCanal)
        buffer.order(ByteOrder.nativeOrder())

        val pixeles = IntArray(ancho * alto)
        bitmap.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)

        for (pixel in pixeles) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            when (dataType) {
                DataType.FLOAT32 -> {
                    // Normalización exacta [0.0f, 1.0f] verificada en ImageDataGenerator(rescale=1./255) y FruitClassifier.kt
                    buffer.putFloat(r / 255.0f)
                    buffer.putFloat(g / 255.0f)
                    buffer.putFloat(b / 255.0f)
                }
                DataType.UINT8 -> {
                    buffer.put(r.toByte())
                    buffer.put(g.toByte())
                    buffer.put(b.toByte())
                }
                else -> Unit
            }
        }

        buffer.rewind()
        return buffer
    }

    companion object {
        private const val CARPETA_ASSETS_MODEL = "model"
        private const val NOMBRE_ARCHIVO_MODELO = "FruitsClassifier.tflite"
        private const val RUTA_MODELO_ASSETS = "model/FruitsClassifier.tflite"
        private const val RUTA_LABELS_ASSETS = "model/labels_alimentos.txt"
        private const val MAX_SUGERENCIAS = 3
        const val UMBRAL_MINIMO_CONFIANZA_DEFECTO = 0.60f
    }
}

/**
 * Alias de compatibilidad con la Etapa 7A que delega directamente en [ReconocedorAlimentoTFLite].
 */
typealias ReconocedorAlimentoTFLitePreparado = ReconocedorAlimentoTFLite
