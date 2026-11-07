package com.example.ecodespensa.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Gestiona el almacenamiento privado temporal de capturas (`context.cacheDir/capturas_alimentos`)
 * y normaliza la orientación EXIF y las dimensiones de la fotografía antes de mostrarla o
 * entregarla al componente de reconocimiento TensorFlow Lite.
 *
 * No requiere permisos de almacenamiento externo (`READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`).
 */
object ProcesadorImagenCamara {

    private const val CARPETA_CAPTURAS = "capturas_alimentos"
    private const val LADO_MAXIMO_DEFECTO_PX = 1080
    private const val CALIDAD_JPEG_NORMALIZADO = 90

    /**
     * Crea un archivo temporal `.jpg` dentro del directorio privado `cacheDir/capturas_alimentos`.
     */
    fun crearArchivoTemporalCaptura(context: Context): File {
        val directorio = File(context.cacheDir, CARPETA_CAPTURAS).apply {
            if (!exists()) mkdirs()
        }
        return File(directorio, "captura_alimento_${UUID.randomUUID()}.jpg")
    }

    /**
     * Decodifica el archivo capturado por CameraX, corrige su rotación/espejo según los metadatos
     * EXIF, ajusta sus dimensiones máximas para evitar `OutOfMemoryError` y guarda la versión
     * normalizada en el mismo archivo privado temporal.
     */
    suspend fun procesarCapturaTemporal(
        archivoCaptura: File,
        maxLadoPx: Int = LADO_MAXIMO_DEFECTO_PX
    ): Result<ImagenEntradaReconocimiento> = withContext(Dispatchers.IO) {
        runCatching {
            require(archivoCaptura.exists() && archivoCaptura.length() > 0L) {
                "El archivo de la fotografía capturada está vacío o no existe."
            }

            // 1. Leer dimensiones originales sin cargar el bitmap completo en memoria
            val opcionesBounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(archivoCaptura.absolutePath, opcionesBounds)
            val anchoOriginal = opcionesBounds.outWidth
            val altoOriginal = opcionesBounds.outHeight
            require(anchoOriginal > 0 && altoOriginal > 0) {
                "No se pudieron leer las dimensiones de la imagen capturada."
            }

            // 2. Leer orientación EXIF real del archivo capturado
            val exif = ExifInterface(archivoCaptura.absolutePath)
            val orientacionExif = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotacionGrados = when (orientacionExif) {
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_TRANSPOSE -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSVERSE -> 270
                else -> 0
            }

            // 3. Calcular inSampleSize seguro para decodificar en ARGB_8888
            val opcionesDecodificacion = BitmapFactory.Options().apply {
                inSampleSize = calcularInSampleSize(anchoOriginal, altoOriginal, maxLadoPx)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmapDecodificado = BitmapFactory.decodeFile(
                archivoCaptura.absolutePath,
                opcionesDecodificacion
            ) ?: throw IllegalStateException("No se pudo decodificar la fotografía capturada.")

            // 4. Aplicar transformación de rotación/espejo EXIF y escalado acotado
            val matriz = crearMatrizExif(orientacionExif)
            val bitmapOrientado = if (!matriz.isIdentity) {
                val transformado = Bitmap.createBitmap(
                    bitmapDecodificado,
                    0,
                    0,
                    bitmapDecodificado.width,
                    bitmapDecodificado.height,
                    matriz,
                    true
                )
                if (transformado !== bitmapDecodificado) {
                    bitmapDecodificado.recycle()
                }
                transformado
            } else {
                bitmapDecodificado
            }

            val bitmapEscalaFinal = acotarDimensionesMaximas(bitmapOrientado, maxLadoPx)
            if (bitmapEscalaFinal !== bitmapOrientado) {
                bitmapOrientado.recycle()
            }

            val bitmapArgb = if (bitmapEscalaFinal.config == Bitmap.Config.ARGB_8888) {
                bitmapEscalaFinal
            } else {
                val copia = bitmapEscalaFinal.copy(Bitmap.Config.ARGB_8888, false)
                bitmapEscalaFinal.recycle()
                copia
            }

            // 5. Sobrescribir el archivo temporal con la imagen ya enderezada y orientación normal
            FileOutputStream(archivoCaptura).use { out ->
                bitmapArgb.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG_NORMALIZADO, out)
                out.flush()
            }
            runCatching {
                val exifNormalizado = ExifInterface(archivoCaptura.absolutePath)
                exifNormalizado.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString()
                )
                exifNormalizado.saveAttributes()
            }

            ImagenEntradaReconocimiento(
                bitmap = bitmapArgb,
                rutaArchivoTemporal = archivoCaptura.absolutePath,
                anchoOriginalPx = anchoOriginal,
                altoOriginalPx = altoOriginal,
                anchoProcesadoPx = bitmapArgb.width,
                altoProcesadoPx = bitmapArgb.height,
                rotacionAplicadaGrados = rotacionGrados
            )
        }
    }

    /**
     * Elimina de forma segura un archivo temporal específico del almacenamiento privado.
     */
    fun eliminarArchivoTemporalSeguro(rutaArchivo: String?) {
        if (rutaArchivo.isNullOrBlank()) return
        runCatching {
            val archivo = File(rutaArchivo)
            if (archivo.exists() && archivo.isFile) {
                archivo.delete()
            }
        }
    }

    /**
     * Limpia las capturas temporales del directorio privado `cacheDir/capturas_alimentos`,
     * conservando opcionalmente [conservarRuta] si sigue en uso en el formulario actual.
     */
    fun limpiarCapturasTemporales(context: Context, conservarRuta: String? = null) {
        runCatching {
            val directorio = File(context.cacheDir, CARPETA_CAPTURAS)
            if (!directorio.exists()) return
            val rutaProtegida = conservarRuta?.let { File(it).canonicalPath }
            directorio.listFiles()?.forEach { archivo ->
                if (archivo.isFile) {
                    val mismaRuta = rutaProtegida != null &&
                        runCatching { archivo.canonicalPath == rutaProtegida }.getOrDefault(false)
                    if (!mismaRuta) {
                        archivo.delete()
                    }
                }
            }
        }
    }

    private fun calcularInSampleSize(ancho: Int, alto: Int, maxLado: Int): Int {
        var inSampleSize = 1
        val ladoMayor = max(ancho, alto)
        if (ladoMayor > maxLado) {
            val mitadLado = ladoMayor / 2
            while ((mitadLado / inSampleSize) >= maxLado) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun crearMatrizExif(orientacionExif: Int): Matrix {
        val matrix = Matrix()
        when (orientacionExif) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        return matrix
    }

    private fun acotarDimensionesMaximas(bitmap: Bitmap, maxLadoPx: Int): Bitmap {
        val ancho = bitmap.width
        val alto = bitmap.height
        val ladoMayor = max(ancho, alto)
        if (ladoMayor <= maxLadoPx || maxLadoPx <= 0) return bitmap

        val factor = maxLadoPx.toFloat() / ladoMayor.toFloat()
        val nuevoAncho = (ancho * factor).toInt().coerceAtLeast(1)
        val nuevoAlto = (alto * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, nuevoAncho, nuevoAlto, true)
    }
}

