package fr.ryfacto.mediabluetoothheadsetissue

enum class AudioMenuMediaSessionEvents {
    TRIGGER,
    INTERRUPTION_BEGAN,
    INTERRUPTION_ENDED_BUT_MUST_NOT_RESUME,
    INTERRUPTION_ENDED_AND_SHOULD_RESUME;
}
