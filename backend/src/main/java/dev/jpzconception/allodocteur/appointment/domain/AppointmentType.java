package dev.jpzconception.allodocteur.appointment.domain;

public enum AppointmentType {
    TELECONSULTATION_VIDEO, TELECONSULTATION_AUDIO, CHAT, HOME_VISIT;

    public boolean isRemote() {
        return this != HOME_VISIT;
    }

    /** Une session audio/vidéo est ouverte au démarrage ; le chat se passe uniquement dans la messagerie. */
    public boolean hasMediaSession() {
        return this == TELECONSULTATION_VIDEO || this == TELECONSULTATION_AUDIO;
    }
}
