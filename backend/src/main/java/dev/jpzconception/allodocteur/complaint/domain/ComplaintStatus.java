package dev.jpzconception.allodocteur.complaint.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ComplaintStatus {
    OPEN, IN_REVIEW, RESOLVED, REJECTED;

    public static final Set<ComplaintStatus> CLOSED = EnumSet.of(RESOLVED, REJECTED);

    public boolean isClosed() {
        return CLOSED.contains(this);
    }
}
