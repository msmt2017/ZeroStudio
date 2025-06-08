package de.lulebe.designer

import android.app.Application
import de.lulebe.designer.data.objects.BoardObject


class Designer : Application() {
    val boards = mutableMapOf<Int, BoardObject>()
}