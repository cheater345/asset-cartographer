package dev.nyx.cartographer.device;

// Read-only file metadata surface exposed by CartographerUserService,
// which runs as the shell user in a separate process (Shizuku user service).
// There is no write, delete, or exec method here — by construction.

interface IRemoteFs {

    // Reserved destroy method defined by the Shizuku server (16777114 in aidl).
    void destroy() = 16777114;

    // One escaped record per line: name|sizeBytes|modifiedAtMillis|d|f
    // Returns "__MISSING__" when the path is not a readable directory.
    String listDir(String path) = 1;

    // A single record line, or "__MISSING__" when the path does not exist.
    String statPath(String path) = 2;
}
