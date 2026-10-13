package dev.jpzconception.allodocteur.medicalrecord.domain;

public enum AccessAction {
    VIEW_RECORD(false), UPDATE_SUMMARY(true), VIEW_ENTRIES(false), CREATE_ENTRY(true), DELETE_ENTRY(true),
    VIEW_ACCESS_LOG(false), VIEW_PRESCRIPTIONS(false), VIEW_PRESCRIPTION(false), CREATE_PRESCRIPTION(true),
    CANCEL_PRESCRIPTION(true);

    private final boolean write;

    AccessAction(boolean write) {
        this.write = write;
    }

    public boolean isWrite() {
        return write;
    }
}
