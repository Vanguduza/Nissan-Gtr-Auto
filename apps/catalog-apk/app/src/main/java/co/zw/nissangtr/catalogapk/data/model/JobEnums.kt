package co.zw.nissangtr.catalogapk.data.model

enum class JobDesiredState {
    RUN,
    PAUSE,
}

enum class JobStatus {
    QUEUED,
    PROCESSING,
    PAUSED,
    COMPLETE,
    FAILED,
    CANCELLED,
}

enum class CloudflareMode {
    AUTO,
    ALWAYS,
    OFF,
}

enum class ProfileEngine {
    MEGAZIP,
    PARTSOUQ,
    CUSTOM,
}
