package xyz.babyplatipus.ptunnel.vpn

import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

/** Обёртки над Kotlin-итераторами для gomobile. */

class StringArray(private val iterator: Iterator<String>) : StringIterator {
    override fun len(): Int = 0
    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): String = iterator.next()
}

class InterfaceArray(
    private val iterator: Iterator<LibboxNetworkInterface>
) : NetworkInterfaceIterator {
    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): LibboxNetworkInterface = iterator.next()
}