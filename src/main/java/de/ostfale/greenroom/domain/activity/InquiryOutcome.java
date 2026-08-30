package de.ostfale.greenroom.domain.activity;

/** Shared by speaker and venue inquiries: they have the same outcomes. */
public enum InquiryOutcome {
    PENDING, ACCEPTED, DECLINED, NO_RESPONSE
}
