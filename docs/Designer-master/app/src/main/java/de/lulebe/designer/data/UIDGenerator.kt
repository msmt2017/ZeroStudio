package de.lulebe.designer.data

import java.util.*


object UIDGenerator {
    fun generateUID () : Long {
        return System.currentTimeMillis().shl(14).or(Random().nextInt(16184).plus(200).toLong())
    }
}