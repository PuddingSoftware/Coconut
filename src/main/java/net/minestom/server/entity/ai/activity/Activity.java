package net.minestom.server.entity.ai.activity;

public enum Activity {

    // the core activity runs alongside the selected non-core activity rather than competing with it
    CORE,

    IDLE,

    FIGHT,

    PANIC,
}