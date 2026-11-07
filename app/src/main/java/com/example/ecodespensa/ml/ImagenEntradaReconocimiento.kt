package com.example.ecodespensa.ml

import android.graphics.Bitmap
import kotlin.math.min

/**
 * Imagen de entrada ya normalizada (orientación EXIF corregida y dimensiones acotadas)
 * lista para ser mostrada en la interfaz y procesada por un clasificador TensorFlow Lite.
 */
data class ImagenEntradaReconocimiento(
    val bitmap: Bitmap,
    val rutaArchivoTemporal: String,
    val anchoOriginalPx: Int,
    val altoOriginalPx: Int,
    val anchoProcesadoPx: Int,
    val altoProcesadoPx: Int,
    val rotacionAplicadaGrados: Int,
    val capturadaEnMs: Long = System.currentTimeMillis()
) {
    /**
     * Prepara un [Bitmap] en formato `ARGB_8888` con las dimensiones exactas que requiera el tensor
     * de entrada de un modelo `.tflite` (por ejemplo, `224x224` o `300x300`), realizando un recorte
     * central cuadrado opcional para evitar distorsión de relación de aspecto.
     */
    fun prepararBitmapEntradaTensor(
        anchoObjetivo: Int = 224,
        altoObjetivo: Int = 224,
        recorteCentralCuadrado: Boolean = true
    ): Bitmap {
        require(anchoObjetivo > 0 && altoObjetivo > 0) {
            "Las dimensiones del tensor de entrada deben ser mayores a cero."
        }

        val origenArgb = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        val fuenteRecortada = if (recorteCentralCuadrado && origenArgb.width != origenArgb.height) {
            val lado = min(origenArgb.width, origenArgb.height)
            val offsetX = (origenArgb.width - lado) / 2
            val offsetY = (origenArgb.height - lado) / 2
            Bitmap.createBitmap(origenArgb, offsetX, offsetY, lado, lado)
        } else {
            origenArgb
        }

        return if (fuenteRecortada.width == anchoObjetivo && fuenteRecortada.height == altoObjetivo) {
            fuenteRecortada
        } else {
            Bitmap.createScaledBitmap(fuenteRecortada, anchoObjetivo, altoObjetivo, true)
        }
    }
}

