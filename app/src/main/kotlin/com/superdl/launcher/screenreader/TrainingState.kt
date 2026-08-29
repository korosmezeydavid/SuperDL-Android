package com.superdl.launcher.screenreader

/**
 * A GESZTUS-TANULÁS ÁLLAPOTA.
 *
 * A menüből indítjuk (a SuperDL-ből), de a gesztusokat a képernyőolvasó
 * szolgáltatás kapja meg — ezért kell egy közös, mindkettő által elérhető hely.
 */
object TrainingState {

    enum class Mode { OFF, FREE_PRACTICE, LESSONS, EXAM }

    @Volatile
    var mode: Mode = Mode.OFF
        private set

    /** Hányadik óránál / vizsgakérdésnél tartunk. */
    @Volatile
    var index: Int = 0

    /** Hány hibát vétett a vizsgán. */
    @Volatile
    var errors: Int = 0

    /** A vizsga kérdései, kevert sorrendben. */
    @Volatile
    var examOrder: List<ScreenReaderTraining.Lesson> = emptyList()

    /** Az összetett feladatok, a vizsga MÁSODIK szakaszában. */
    @Volatile
    var compositeOrder: List<ScreenReaderTraining.CompositeTask> = emptyList()

    /** Az összetett szakaszban járunk-e már. */
    @Volatile
    var inCompositePhase: Boolean = false

    /** Az aktuális összetett feladaton belül hányadik lépésnél tartunk. */
    @Volatile
    var compositeStep: Int = 0

    val isActive: Boolean get() = mode != Mode.OFF

    /**
     * Értesítés a mód változásáról.
     *
     * MIÉRT KELL: a tanulást a MENÜBŐL indítjuk, a mozdulatokat viszont a
     * képernyőolvasó szolgáltatás fogadja. Ha nem szólnánk neki azonnal, a
     * SuperDL saját gesztus-kezelése maradna érvényben, és a launcher menüje
     * reagálna a söprésekre a tanulás helyett.
     */
    @Volatile
    var onModeChanged: (() -> Unit)? = null

    fun startFreePractice() {
        mode = Mode.FREE_PRACTICE
        index = 0
        errors = 0
        onModeChanged?.invoke()
    }

    fun startLessons() {
        mode = Mode.LESSONS
        index = 0
        errors = 0
        onModeChanged?.invoke()
    }

    fun startExam() {
        mode = Mode.EXAM
        index = 0
        errors = 0
        inCompositePhase = false
        compositeStep = 0
        // Kevert sorrend, hogy ne lehessen bemagolni a sorrendet.
        examOrder = ScreenReaderTraining.LESSONS.shuffled()
        compositeOrder = ScreenReaderTraining.COMPOSITE_TASKS.shuffled()
        onModeChanged?.invoke()
    }

    fun stop() {
        mode = Mode.OFF
        index = 0
        errors = 0
        examOrder = emptyList()
        compositeOrder = emptyList()
        inCompositePhase = false
        compositeStep = 0
        onModeChanged?.invoke()
    }
}
