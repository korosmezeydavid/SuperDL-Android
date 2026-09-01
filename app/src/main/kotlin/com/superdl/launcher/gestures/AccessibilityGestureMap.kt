package com.superdl.launcher.gestures

import android.accessibilityservice.AccessibilityService as A

/**
 * A FELÜLET ELFORGATÁSA A KÉPERNYŐOLVASÓBAN.
 *
 * MIÉRT KELL KÜLÖN: a képernyőolvasó nem a SwipeGestureListener-t használja,
 * hanem a rendszer kisegítő-gesztusait (onGesture). Ha csak a SuperDL saját
 * felülete fordulna el, a felhasználónak külső alkalmazásban vissza kellene
 * váltania a régi mozdulatokra. Az ujj nem tud két szabályt egyszerre.
 *
 * MIT CSINÁL: a beérkező FIZIKAI gesztust átfordítja arra, amit a program
 * alap kezelésben ugyanabban a jelentésben várna. Így a ScreenReaderService
 * `when` blokkja változatlan maradhat — ott továbbra is "lefelé söprés =
 * következő elem" áll, csak épp nem biztos, hogy az ujj lefelé mozdult.
 *
 * AZ ÖSSZETETT GESZTUSOK IS FORDULNAK (jobbra-majd-fel = hangtérkép és
 * társai). Mindkét felüket külön leképezzük, aztán újra összerakjuk.
 * Ha ezt nem tennénk, elforgatva két különböző gesztus ugyanoda futna —
 * és az egyik funkció némán elérhetetlenné válna.
 */
object AccessibilityGestureMap {

    /** A beérkező gesztus átfordítása alap-kezelésbelire. */
    fun toNormal(gestureId: Int): Int {
        val parts = decompose(gestureId) ?: return gestureId
        val (first, second) = parts
        val firstLogical = GestureOrientation.logicalOf(first)
        if (second == null) {
            return normalIdOf(firstLogical) ?: gestureId
        }
        val secondLogical = GestureOrientation.logicalOf(second)
        return compose(normalPhysical(firstLogical), normalPhysical(secondLogical)) ?: gestureId
    }

    /** Egy gesztus felbontása egy vagy két irányra. Null, ha nem söprés. */
    private fun decompose(id: Int): Pair<GestureOrientation.Physical, GestureOrientation.Physical?>? =
        when (id) {
            A.GESTURE_SWIPE_UP -> GestureOrientation.Physical.UP to null
            A.GESTURE_SWIPE_DOWN -> GestureOrientation.Physical.DOWN to null
            A.GESTURE_SWIPE_LEFT -> GestureOrientation.Physical.LEFT to null
            A.GESTURE_SWIPE_RIGHT -> GestureOrientation.Physical.RIGHT to null

            A.GESTURE_SWIPE_UP_AND_DOWN ->
                GestureOrientation.Physical.UP to GestureOrientation.Physical.DOWN
            A.GESTURE_SWIPE_UP_AND_LEFT ->
                GestureOrientation.Physical.UP to GestureOrientation.Physical.LEFT
            A.GESTURE_SWIPE_UP_AND_RIGHT ->
                GestureOrientation.Physical.UP to GestureOrientation.Physical.RIGHT
            A.GESTURE_SWIPE_DOWN_AND_UP ->
                GestureOrientation.Physical.DOWN to GestureOrientation.Physical.UP
            A.GESTURE_SWIPE_DOWN_AND_LEFT ->
                GestureOrientation.Physical.DOWN to GestureOrientation.Physical.LEFT
            A.GESTURE_SWIPE_DOWN_AND_RIGHT ->
                GestureOrientation.Physical.DOWN to GestureOrientation.Physical.RIGHT
            A.GESTURE_SWIPE_LEFT_AND_UP ->
                GestureOrientation.Physical.LEFT to GestureOrientation.Physical.UP
            A.GESTURE_SWIPE_LEFT_AND_DOWN ->
                GestureOrientation.Physical.LEFT to GestureOrientation.Physical.DOWN
            A.GESTURE_SWIPE_LEFT_AND_RIGHT ->
                GestureOrientation.Physical.LEFT to GestureOrientation.Physical.RIGHT
            A.GESTURE_SWIPE_RIGHT_AND_UP ->
                GestureOrientation.Physical.RIGHT to GestureOrientation.Physical.UP
            A.GESTURE_SWIPE_RIGHT_AND_DOWN ->
                GestureOrientation.Physical.RIGHT to GestureOrientation.Physical.DOWN
            A.GESTURE_SWIPE_RIGHT_AND_LEFT ->
                GestureOrientation.Physical.RIGHT to GestureOrientation.Physical.LEFT
            else -> null
        }

    /** Alap kezelésben melyik fizikai irány jelenti ezt a logikai műveletet. */
    private fun normalPhysical(logical: GestureOrientation.Logical): GestureOrientation.Physical =
        when (logical) {
            GestureOrientation.Logical.PREVIOUS -> GestureOrientation.Physical.UP
            GestureOrientation.Logical.NEXT -> GestureOrientation.Physical.DOWN
            GestureOrientation.Logical.ENTER -> GestureOrientation.Physical.RIGHT
            GestureOrientation.Logical.BACK -> GestureOrientation.Physical.LEFT
        }

    private fun normalIdOf(logical: GestureOrientation.Logical): Int? =
        when (normalPhysical(logical)) {
            GestureOrientation.Physical.UP -> A.GESTURE_SWIPE_UP
            GestureOrientation.Physical.DOWN -> A.GESTURE_SWIPE_DOWN
            GestureOrientation.Physical.LEFT -> A.GESTURE_SWIPE_LEFT
            GestureOrientation.Physical.RIGHT -> A.GESTURE_SWIPE_RIGHT
        }

    private fun compose(
        first: GestureOrientation.Physical,
        second: GestureOrientation.Physical
    ): Int? = when (first) {
        GestureOrientation.Physical.UP -> when (second) {
            GestureOrientation.Physical.DOWN -> A.GESTURE_SWIPE_UP_AND_DOWN
            GestureOrientation.Physical.LEFT -> A.GESTURE_SWIPE_UP_AND_LEFT
            GestureOrientation.Physical.RIGHT -> A.GESTURE_SWIPE_UP_AND_RIGHT
            GestureOrientation.Physical.UP -> null
        }
        GestureOrientation.Physical.DOWN -> when (second) {
            GestureOrientation.Physical.UP -> A.GESTURE_SWIPE_DOWN_AND_UP
            GestureOrientation.Physical.LEFT -> A.GESTURE_SWIPE_DOWN_AND_LEFT
            GestureOrientation.Physical.RIGHT -> A.GESTURE_SWIPE_DOWN_AND_RIGHT
            GestureOrientation.Physical.DOWN -> null
        }
        GestureOrientation.Physical.LEFT -> when (second) {
            GestureOrientation.Physical.UP -> A.GESTURE_SWIPE_LEFT_AND_UP
            GestureOrientation.Physical.DOWN -> A.GESTURE_SWIPE_LEFT_AND_DOWN
            GestureOrientation.Physical.RIGHT -> A.GESTURE_SWIPE_LEFT_AND_RIGHT
            GestureOrientation.Physical.LEFT -> null
        }
        GestureOrientation.Physical.RIGHT -> when (second) {
            GestureOrientation.Physical.UP -> A.GESTURE_SWIPE_RIGHT_AND_UP
            GestureOrientation.Physical.DOWN -> A.GESTURE_SWIPE_RIGHT_AND_DOWN
            GestureOrientation.Physical.LEFT -> A.GESTURE_SWIPE_RIGHT_AND_LEFT
            GestureOrientation.Physical.RIGHT -> null
        }
    }
}
