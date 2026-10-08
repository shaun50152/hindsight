package dev.hindsight.common.events;

/** Which policy version served a production decision (canary routing). */
public enum VersionRole {
    CONTROL,
    CANARY
}
