package net.rafkos.neuroshima.editor.publish

object MaskIdCodec {
    fun encode(maskId: Int): Int {
        require(maskId in 0..0xFFFFFE) { "maskId out of range: $maskId" }
        return 0xFFFFFF - maskId
    }
    fun decode(rgb24: Int): Int = 0xFFFFFF - (rgb24 and 0xFFFFFF)
}
